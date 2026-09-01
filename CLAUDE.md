# CLAUDE.md — Hoso (放送)

> App Android perso de streaming mobile : capture écran + son du jeu + micro,
> diffusion RTMP vers Twitch/YouTube/Kick. Statut réel (Shinzo, 2026-07-03) :
> v1.1.0 (vc12) en test fermé Alpha sur Google Play, pas encore en production
> publique. Bug ouvert non résolu : l'app ne démarre pas sur Samsung S25 Ultra.

## MANDATORY FIRST READ (BLOCKING — load order critical)

1. `.claude/rules/Interpretation-Protocol.md` — how to read every other rule
2. `.claude/rules/Confidentiality.md` — absolute blocking rule on user personal data
3. `.claude/rules/Monozukuri.md` — philosophie chapeau

No exception. No shortcut.

## Identity

| Champ | Valeur |
|---|---|
| Nom | Hoso (放送 — « diffusion »), anciennement Hōsō |
| Type | App Android native (Kotlin), package `com.theermite.hoso` |
| Statut réel | Fonctionnelle, en test fermé Play Store Alpha (vc12/1.1.0, déployé 2026-07-03). Pas sur le Play Store public. 1 bug ouvert (crash lancement Samsung S25 Ultra). |
| Repo | Local `D:\30-Dev-Projects\Hoso`. Distant déclaré dans les docs : `github.com/shinkofa/Hoso` (public, Apache 2.0). |
| Fork associé | `streampack-fork/` — fork local de StreamPack 3.1.2 (gitignored, dépôt Git séparé), branche `fix/twitch-audio-race-hoso`, PR upstream `ThibaultBee/StreamPack#294` |

## Ce que fait Hoso

Flux principal observé dans le code (`app/src/main/java/com/theermite/hoso/`) :

1. **MainActivity** — écran de configuration : destinations (presets Twitch/YouTube/Kick/RTMP custom via `config/DestinationPreset.kt`), réglages stream (`config/StreamConfig.kt`), choix source audio (`config/AudioSource.kt` : Micro seul ou Mix micro+jeu).
2. **OnboardingActivity** — guide 4 étapes au premier lancement (`onboarding/OnboardingStep.kt` : WELCOME → PERMISSIONS → CONNECT_CHANNEL → GO_LIVE), rejouable.
3. **StreamPermissionActivity** — demande la permission MediaProjection (transparent), puis lance :
   - **ScreenRecordService** — foreground service qui capture l'écran et pousse le flux RTMP via StreamPack (fork local).
   - **OverlayService** — foreground service qui affiche la bulle de contrôle flottante draggable (start/stop, mute, pause, mode privacy, HUD débit/durée/état).
   - **ChatBubbleService** — foreground service IRC Twitch maison (`chat/TwitchIrcClient.kt`, connexion anonyme `justinfan`), bulle de chat flottante redimensionnable.
4. **Mixage audio** — `audio/MixedAudioSource.kt` + `AudioGains.kt` : mixeur PCM logiciel micro + son du jeu (`AudioPlaybackCapture`), gains réglables en live.
5. **Monitoring** — GlitchTip (self-hosted, via Sentry Android SDK) branché en release depuis vc12 (2026-07-03), DSN injecté par `local.properties`, désactivé en debug.

Pas de compte utilisateur, pas d'analytics, pas de collecte de données déclarée (voir `PRIVACY.md` — **note** : ce fichier date du 2026-06-14 et ne mentionne pas encore le monitoring GlitchTip ajouté ensuite, à vérifier/mettre à jour séparément).

## Stack

| Dépendance (trouvée dans `app/build.gradle.kts` / `gradle/libs.versions.toml`) | Rôle |
|---|---|
| Kotlin + AGP (Android Gradle Plugin) | Langage et build Android |
| `androidx.core`, `appcompat`, `material`, `constraintlayout`, `lifecycle-runtime`, `recyclerview`, `viewpager2` | UI standard Android (viewpager2 sert à l'onboarding) |
| `io.github.thibaultbee.streampack:streampack-core/services/rtmp` | Capture écran + encodage + diffusion RTMP — substitué par le composite build local `streampack-fork/` (patch race condition audio Twitch) |
| `sentry.android` (Sentry Android SDK) | Crash reporting vers GlitchTip self-hosted |
| `junit`, `org.json` (test) | Tests unitaires JVM |

Build : `compileSdk 36`, `minSdk 29`, `targetSdk 35`, `versionCode 12`, `versionName 1.1.0`, Java 17, ViewBinding activé, lint release avec `NewApi` en fatal (empêche un appel API au-dessus de `minSdk` de shipper un crash — c'est exactement ce qui a cassé Android 10 avant correctif).

## Documents

Existants dans `docs/` (contrairement à beaucoup de projets, ce projet a déjà son CDC + PET) :

- `docs/CDC-Hoso.md` — Cahier des Charges, v1.1.0, validé 2026-06-11.
- `docs/PET-Hoso.md` — Plan d'Exécution Technique, v0.1.1, 2026-06-11.
- `docs/Roadmap.md` — plan de features par groupes (G1 à G7), la plupart livrés.
- `docs/Audits/Audit-Hoso-2026-06-09.md` — audit de cohérence.
- `docs/Battery-Optimization-Guide.md`, `docs/StreamPack-Audio-Pipeline.md` — docs techniques dédiées.
- `docs/store/` — assets et docs de soumission Play Store (Phase C, Phase D, plan de publication).
- `docs/Sessions/` — 20 rapports de session (2026-04-20 → 2026-07-03).

## Commands

Trouvées dans `README.md` / `scripts/` (build non exécuté à la vérification, environnement Gradle daemon indisponible en session — commandes rapportées telles quelles, à confirmer avant usage) :

```bash
# Build debug (nécessite JDK 17+, cloner streampack-fork à côté du repo — voir settings.gradle.kts)
./gradlew assembleDebug

# Tests unitaires JVM (5 fichiers de test existants : IrcMessageParseTest, LegalLinksTest,
# StreamStartErrorTest, OverlayServiceManifestTest, OnboardingStepTest — 38 tests verts au 2026-07-03)
./gradlew testDebugUnitTest

# Installer sur device USB
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Build release signé (mainteneur uniquement, clé dans Shinkofa-Vault)
./scripts/fetch-signing.sh
./gradlew assembleRelease
```

## Méthodologie

Hérite de la méthodologie MNK-GoRin / Kata. Synchronisée via `/sync-repo`.

## Note confidentialité

Le point sensible du projet est la **clé de stream** (RTMP stream key) : `PRIVACY.md` déclare qu'elle est stockée uniquement en local (stockage privé de l'app) et envoyée uniquement au serveur RTMP choisi par l'utilisateur — jamais au développeur ni à un tiers. Aucune autre donnée personnelle identifiée dans le code (pas de compte, pas de nom, pas d'email collecté). Le monitoring GlitchTip envoie des rapports de crash — `PII off` déclaré dans les notes Shinzo (2026-07-03), à vérifier dans le code d'init Sentry avant toute évolution touchant ce module.
