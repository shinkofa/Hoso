# StreamPack Audio Pipeline — Reference

> Source : StreamPack 3.1.2 (https://github.com/ThibaultBee/StreamPack tag `3.1.2`).
> Cloné dans `.research/streampack/` (gitignored), zone hors-tracking de Hoso.
> Rédigé le 2026-04-28 en préparation de la reprise du mixer audio mic+jeu.

---

## Pourquoi ce document existe

La tentative de mixer audio du 2026-04-28 a échoué après 6 cycles, en partie à cause d'**assumptions fausses sur la convention de l'état des `ByteBuffer`** dans le pipeline StreamPack. Ce document fixe les contrats observés dans la source — pas dans la doc, qui n'en parle pas — pour que la prochaine implémentation parte sur du solide.

---

## Vue d'ensemble du pipeline audio

```
[IAudioSourceInternal] ── pull ──► [RawFramePullPush] ── push ──► [AudioFrameProcessor] ──► [SingleStreamer] ──► [MediaCodecEncoder]
        │                                  │                              │                      │                       │
   getAudioFrame(factory)         loop.launch{                   processFrame(frame)     queueAudioFrame()      queueInputBuffer(
   fillAudioFrame(frame)            getFrame(factory)                                                              position, limit, ts)
                                    processFrame
                                    onFrame(processed) }
```

**Modes du port** (`AudioInput.kt:139`) :
- **PushAudioPort** (`PushConfig`) — boucle de pull dédiée sur `THREAD_NAME_AUDIO_PREPROCESSING`. Utilise `getAudioFrame(factory)`. C'est le mode utilisé par défaut dans `StreamerPipeline` (`AudioOutputMode.PUSH`).
- **CallbackAudioPort** (`CallbackConfig`) — appelée depuis le thread de l'encoder via `IAsyncByteBufferInput.OnFrameRequestedListener`. Utilise `fillAudioFrame(frame)` (le buffer est fourni par l'encoder, pas par la factory). Pas le chemin par défaut.

**Conséquence pour Hōsō** : on est dans le mode PUSH. L'implémentation de référence pour un mixer custom est **`getAudioFrame(frameFactory: IReadOnlyRawFrameFactory): RawFrame`**.

---

## Le contrat `IAudioSourceInternal`

Source : `core/elements/sources/audio/IAudioSource.kt`, `IAudioFrameSourceInternal.kt`.

```kotlin
interface IAudioSourceInternal : IAudioSource, IAudioFrameSourceInternal,
    SuspendStreamable, SuspendConfigurable<AudioSourceConfig>, Releasable {
    val isStreamingFlow: StateFlow<Boolean>

    interface Factory {
        suspend fun create(context: Context): IAudioSourceInternal
        fun isSourceEquals(source: IAudioSourceInternal?): Boolean
    }
}

interface IAudioFrameSourceInternal {
    fun fillAudioFrame(frame: RawFrame): RawFrame                       // mode CALLBACK
    fun getAudioFrame(frameFactory: IReadOnlyRawFrameFactory): RawFrame // mode PUSH
}
```

Méthodes `Streamable` / `Configurable` / `Releasable` à implémenter :
- `suspend fun configure(config: AudioSourceConfig)` — appelée AVANT `startStream`. Crée l'`AudioRecord`, vérifie `STATE_INITIALIZED`.
- `suspend fun startStream()` — `audioRecord.startRecording()` + `_isStreamingFlow.tryEmit(true)`.
- `suspend fun stopStream()` — `audioRecord.stop()` + `_isStreamingFlow.tryEmit(false)`.
- `fun release()` — `audioRecord.release()`. Idempotent attendu.

`isSourceEquals` permet à `AudioInput.setSource()` d'éviter de remplacer une source par une autre identique (ne pas retourner `true` à la légère pour un mixer paramétré).

---

## Le contrat `RawFrame` et `IRawFrameFactory`

Sources : `core/elements/data/Frame.kt`, `core/elements/utils/pool/{IFrameFactory,RawFrameFactory,ByteBufferPool}.kt`.

```kotlin
data class RawFrame(
    val rawBuffer: ByteBuffer,
    var timestampInUs: Long,
    val onClosed: (RawFrame) -> Unit = {}
) : Closeable {
    override fun close() { onClosed(this) }
}

interface IReadOnlyRawFrameFactory {
    fun create(bufferSize: Int, timestampInUs: Long): RawFrame
}

interface IRawFrameFactory : IReadOnlyRawFrameFactory {
    fun clear()
    fun close()
}
```

**Cycle de vie d'un frame** :
1. La factory `create(bufferSize, ts)` retourne un `RawFrame` dont le `rawBuffer` est emprunté au `ByteBufferPool`.
2. Le pool retourne un buffer dans cet état exact :
   - `buffer.clear().limit(capacity)` → **position=0, limit=capacity, mark=undefined**.
   - Capacity ≥ bufferSize demandé (le pool peut servir plus grand).
3. Quand le frame est `close()`, le `onClosed` callback rend le buffer au pool.
4. Le pool est `clear()` à `stopStream`, `close()` à `release` (par `RawFramePullPush`).

**Ownership des frames** :
- Une source qui *crée* un frame via `factory.create()` (ou via un autre frame qui en sort) en devient propriétaire jusqu'à `close()`.
- Le frame retourné par `getAudioFrame` est consommé downstream (encoder via `queueAudioFrame` → `frame.close()` après usage).
- **Si une source intermédiaire (mixer) pull plusieurs frames pour produire UN seul frame, elle DOIT `close()` les frames intermédiaires elle-même** avant de retourner le frame final.

**Pool partagé pour le push path** : `RawFramePullPush` détient une `RawFrameFactory(isDirect=true)` unique. C'est cette factory qui est passée en paramètre à chaque appel de `getAudioFrame`. Donc tous les frames créés (input + output du mixer) viennent du même pool.

---

## ⚠️ La convention `ByteBuffer` (CRITIQUE — c'est ce qui a tué le cycle 6)

**Règle observée dans la source** : un `RawFrame.rawBuffer` est TOUJOURS en état "**raw layout**" : `position = 0`, `limit = capacity`. La taille des données utiles est **implicite et égale à `capacity`** ; on ne la sépare pas via `limit`.

**JAMAIS de `flip()`** dans le pipeline audio StreamPack. Aucune occurrence dans le code source (vérifié par grep).

### Pourquoi ça marche pour `AudioRecordSource`

`AudioRecordSource.fillAudioFrame` (`core/elements/sources/audio/audiorecord/AudioRecordSource.kt:160`) :

```kotlin
override fun fillAudioFrame(frame: RawFrame): RawFrame {
    val buffer = frame.rawBuffer
    val length = audioRecord.read(buffer, buffer.remaining())  // pas de flip
    if (length > 0) {
        frame.timestampInUs = getTimestampInUs(audioRecord)
        return frame
    }
    ...
}
```

**Détail critique** sur `AudioRecord.read(ByteBuffer, int)` (cf javadoc Android) :

> *"Note that the value returned by `Buffer.position()` on this buffer is unchanged after a call to this method."* (pour les buffers DIRECT — qui sont ceux que StreamPack utilise via `RawFrameFactory(isDirect=true)`).

Donc après `read` : **position=0, limit=capacity, et les données sont écrites dans `[0..length-1]`**. Si `length == capacity` (cas typique en blocking read), tout le buffer est rempli ; le contrat tient. Si `length < capacity`, **les `capacity-length` octets de queue restent indéterminés** mais le pipeline traite quand même `capacity` octets. C'est un point d'inconfort connu mais accepté.

### Comment l'encoder lit le buffer

`MediaCodecEncoder.SyncByteBufferInput.queueInputFrameSync` (`MediaCodecEncoder.kt:513`) :

```kotlin
val size = min(frame.rawBuffer.remaining(), inputBuffer.remaining())
inputBuffer.put(frame.rawBuffer, frame.rawBuffer.position(), size)
mediaCodec.queueInputBuffer(inputBufferId, 0, size, frame.timestampInUs, 0)
```

Avec `position=0, limit=capacity` → `remaining = capacity`, `position()=0`, donc `put` copie `[0..capacity]` du frame. ✓

L'autre chemin async (`MediaCodecEncoder.kt:447`) :

```kotlin
mediaCodec.queueInputBuffer(index, frame.rawBuffer.position(), frame.rawBuffer.limit(), ...)
// = queueInputBuffer(index, 0, capacity, ...)
```

Idem. Les deux confirment la convention "raw layout".

### Comment `MuteEffect` traite le buffer

`AudioEffects.kt:30` :

```kotlin
override fun processFrame(frame: RawFrame): RawFrame {
    val remaining = frame.rawBuffer.remaining()    // = capacity (position=0, limit=capacity)
    val position = frame.rawBuffer.position()      // = 0
    if (remaining != mutedByteArray?.size) {
        mutedByteArray = ByteArray(remaining)
    }
    frame.rawBuffer.put(mutedByteArray!!)           // écrit `remaining` zéros, position devient capacity
    frame.rawBuffer.position(position)              // RESET position à 0 → revient en raw layout
    return frame
}
```

**Confirmation explicite** : MuteEffect remet position à 0 après écriture pour préserver la convention. C'est le pattern de référence pour toute manipulation in-place d'un `RawFrame.rawBuffer`.

### ❌ Le bug du cycle 6 (post-mortem)

Symptôme : "Mix produisant du silence (size=0)".

Cause probable :
- Sur des sub-frames pullés depuis `MicrophoneSource.getAudioFrame()` et `MediaProjectionAudioSource.getAudioFrame()`, les buffers sont en raw layout (position=0, limit=capacity).
- Le code cycle 6 a fait `bufA.flip()` (et/ou similaire) en croyant remettre les buffers en read state.
- `flip()` quand position=0 → met `limit=position=0` → `remaining()=0` → la boucle de mix lit 0 short → **écrit 0 short dans output** → output reste vide → silence.
- Le `outBuf.flip()` final, en supposant un état write→read post-mix, complétait le désastre.

**Règle pour la prochaine tentative** : ne JAMAIS appeler `flip()`. Utiliser `duplicate()` si on a besoin d'un curseur indépendant pour parcourir les samples sans modifier l'état du buffer original.

---

## Squelette correct d'un mixer custom

```kotlin
package com.theermite.hoso.audio

import android.content.Context
import android.media.projection.MediaProjection
import io.github.thibaultbee.streampack.core.elements.data.RawFrame
import io.github.thibaultbee.streampack.core.elements.sources.audio.AudioSourceConfig
import io.github.thibaultbee.streampack.core.elements.sources.audio.IAudioSourceInternal
import io.github.thibaultbee.streampack.core.elements.sources.audio.audiorecord.MediaProjectionAudioSourceFactory
import io.github.thibaultbee.streampack.core.elements.sources.audio.audiorecord.MicrophoneSourceFactory
import io.github.thibaultbee.streampack.core.elements.utils.pool.IReadOnlyRawFrameFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteOrder

internal class MixedAudioSource(
    private val mic: IAudioSourceInternal,
    private val media: IAudioSourceInternal,
) : IAudioSourceInternal {

    private val _isStreamingFlow = MutableStateFlow(false)
    override val isStreamingFlow = _isStreamingFlow.asStateFlow()

    override suspend fun configure(config: AudioSourceConfig) {
        mic.configure(config)
        media.configure(config) // mêmes sampleRate/channelConfig/byteFormat — sinon resampler en amont
    }

    override suspend fun startStream() {
        mic.startStream()
        media.startStream()
        _isStreamingFlow.tryEmit(true)
    }

    override suspend fun stopStream() {
        try { mic.stopStream() } finally { media.stopStream() }
        _isStreamingFlow.tryEmit(false)
    }

    override fun release() {
        try { mic.release() } finally { media.release() }
        _isStreamingFlow.tryEmit(false)
    }

    override fun fillAudioFrame(frame: RawFrame): RawFrame =
        throw UnsupportedOperationException("MixedAudioSource only supports PUSH path")

    override fun getAudioFrame(frameFactory: IReadOnlyRawFrameFactory): RawFrame {
        val micFrame = mic.getAudioFrame(frameFactory)
        try {
            val mediaFrame = media.getAudioFrame(frameFactory)
            try {
                val capacity = micFrame.rawBuffer.capacity().coerceAtMost(mediaFrame.rawBuffer.capacity())
                val out = frameFactory.create(capacity, micFrame.timestampInUs)

                // Curseurs indépendants — ne touchent pas position/limit des originaux
                val a = micFrame.rawBuffer.duplicate().order(ByteOrder.nativeOrder())
                val b = mediaFrame.rawBuffer.duplicate().order(ByteOrder.nativeOrder())
                val o = out.rawBuffer.duplicate().order(ByteOrder.nativeOrder())

                val sampleCount = capacity / 2 // PCM 16-bit signed
                for (i in 0 until sampleCount) {
                    val s = a.short.toInt() + b.short.toInt()
                    val clipped = s.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    o.putShort(clipped.toShort())
                }
                // out.rawBuffer.position est resté à 0 (on a écrit via duplicate). ✓ raw layout préservé.

                return out
            } finally { mediaFrame.close() }
        } finally { micFrame.close() }
    }

    class Factory(
        private val mediaProjection: MediaProjection,
    ) : IAudioSourceInternal.Factory {
        override suspend fun create(context: Context): IAudioSourceInternal {
            val mic = MicrophoneSourceFactory().create(context)
            val media = MediaProjectionAudioSourceFactory(mediaProjection).create(context)
            return MixedAudioSource(mic, media)
        }
        override fun isSourceEquals(source: IAudioSourceInternal?): Boolean =
            source is MixedAudioSource // pas plus fin sans param identifiant
    }
}
```

**Ce skeleton est de référence, pas du code livrable.** À adapter :
- Gain mic et gain jeu (ducking) : multiplier par un facteur avant somme.
- Gérer le cas `capacity` différent entre mic et media (resampling ou padding zéro — à valider device).
- Effets `defaultAudioEffects` (AEC + NS) : sur `MicrophoneSourceFactory`, pas sur `MediaProjectionAudioSourceFactory` (jeu pas besoin).
- Threading : `getAudioFrame` est appelée depuis `THREAD_NAME_AUDIO_PREPROCESSING`. `audioRecord.read` y bloque jusqu'au remplissage du buffer. Deux `read` sériels (mic puis media) doublent la latence — acceptable au début, à mesurer.

---

## Pièges et points d'attention

1. **Drift A/V long stream** : deux `AudioRecord` distincts, deux clocks `getTimestampInUs`. Sur >1h on peut diverger. À monitorer.
2. **`MediaProjectionAudioSource` requires API ≥ Q (29)** — Hoso minSdk 29, OK.
3. **Permissions** : `RECORD_AUDIO` requise pour les deux. La media projection nécessite en plus le token déjà géré par le foreground service.
4. **Effets audio (AEC/NS)** : par défaut `AudioRecordSourceFactory` ajoute AEC et NS. L'AEC sur la source mic peut entrer en interaction étrange avec un mix qui contient déjà du jeu. À tester avec et sans.
5. **Buffer size mismatch** : `getMinBufferSize` peut différer entre mic et projection (canaux différents possibles). Si `AudioConfig` les force identiques, c'est OK. Sinon resample ou rejeter.
6. **Lifecycle stop ordering** : si `mic.stopStream` throw, `media.stopStream` doit quand même être appelé (try/finally).
7. **`isSourceEquals`** : retourner `true` à la légère ferait que `AudioInput.setSource` court-circuite la nouvelle source. Pour un mixer paramétré, comparer les paramètres significatifs.

---

## Régressions observées en fin de session 2026-04-28 (à isoler)

Trois symptômes remontés en parallèle de l'échec mixer, cause racine non identifiée :

| Symptôme | Hypothèse à tester |
|---|---|
| Cadrage portrait au lieu de landscape sur Twitch | Probable régression côté `VideoConfig` ou orientation MediaProjection. Tester sur main actuel (post-revert) avec le code mixer absent. |
| Mode mic seul silencieux après le build mixer | Build mixer = recréation `MicrophoneSource` ? Vérifier que post-revert ce mode fonctionne. |
| Stream qui ne démarre pas en mode mixer | Probable : `setSource` échoue silencieusement ou `configure` crash sans logs. Ajouter `Logger.e` systématique en début de prochaine implem. |

**Tests post-revert AVANT toute reprise** : sur main actuel (`16bc88f`), confirmer que mode mic seul + cadrage landscape fonctionnent comme avant le 2026-04-28. Si oui, les régressions étaient liées au build mixer revert. Si non, investiguer en priorité.

---

## Liens source utiles

| Composant | Chemin |
|---|---|
| `IAudioSourceInternal` | `core/elements/sources/audio/IAudioSource.kt` |
| `IAudioFrameSourceInternal` | `core/elements/sources/audio/IAudioFrameSourceInternal.kt` |
| `AudioRecordSource` (sealed) | `core/elements/sources/audio/audiorecord/AudioRecordSource.kt` |
| `MicrophoneSource` (internal) + Factory | `core/elements/sources/audio/audiorecord/MicrophoneSource.kt` |
| `MediaProjectionAudioSource` (internal) + Factory | `core/elements/sources/audio/audiorecord/MediaProjectionAudioSource.kt` |
| `RawFrame`, `Frame` | `core/elements/data/Frame.kt` |
| `IRawFrameFactory`, `RawFrameFactory`, `ByteBufferPool` | `core/elements/utils/pool/` |
| `RawFramePullPush` (boucle pull) | `core/elements/processing/RawFramePullPush.kt` |
| `AudioInput`, `PushAudioPort`, `CallbackAudioPort` | `core/pipelines/inputs/AudioInput.kt` |
| `AudioFrameProcessor`, `MuteEffect` | `core/elements/processing/audio/{AudioFrameProcessor,AudioEffects}.kt` |
| `MediaCodecEncoder` (consumer côté queueInputFrame) | `core/elements/encoders/mediacodec/MediaCodecEncoder.kt:447,513` |
| `StubAudioSource` (exemple minimal) | `core/src/androidTest/.../sources/StubAudioSource.kt` |

---

## Synthèse — règles à imprimer avant de toucher au code

1. `RawFrame.rawBuffer` est en **raw layout** : `position=0`, `limit=capacity`. La taille utile = `capacity`.
2. **Ne jamais appeler `flip()`** sur un buffer du pipeline audio.
3. Pour parcourir un buffer sans le mutiler : `duplicate().order(ByteOrder.nativeOrder())`.
4. PCM 16-bit signé little-endian (cf `AudioConfig` byteFormat = `ENCODING_PCM_16BIT`).
5. Une source qui pull N frames intermédiaires DOIT `close()` chacun avant de retourner.
6. La `IReadOnlyRawFrameFactory` partage le pool de `RawFramePullPush` ; tous les `create()` taperont dans le même pool.
7. `AudioRecord.read(direct ByteBuffer, ...)` ne bouge PAS la position. C'est sur ça que repose toute la convention.
8. `release()` / `stopStream()` doivent être idempotents et propager aux sous-sources via `try/finally`.
