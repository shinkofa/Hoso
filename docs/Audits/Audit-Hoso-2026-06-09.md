# Audit Hōsō — 2026-06-09

> Audit code réel brownfield, préalable à la refonte CDC + PET (`/concevoir`).
> 4 axes en parallèle (lecture seule) : Conformité Play Store & build, Qualité/maintenabilité, Tests, Sécurité.
> Source : repo `D:\30-Dev-Projects\Hoso`, commit de base de la session. Stack : Kotlin 2.2.21, AGP 8.13.1, Gradle 8.13, compileSdk 36, targetSdk 35, minSdk 29, fork StreamPack 3.1.2.

---

## 0. Bonnes nouvelles — 3 craintes de la veille défusées par le code réel

| Crainte (veille externe) | Réalité du code | Impact |
|---|---|---|
| `.so` natifs non alignés 16KB → release bloquée | **Zéro `.so` natif** dans le build release de l'app. komuxer 0.3.4 est pur-JVM/Kotlin. srtdroid (natif) n'est PAS inclus (testImplementation de streampack-core seulement). | Blocker 16KB **non applicable** aujourd'hui |
| Crash MediaProjection à la 2e session (token réutilisé) | **Conforme Android 14+** : consentement frais à chaque « Go Live », aucun caching/réutilisation de token. `StreamPermissionActivity` recréée à chaque session (`noHistory=true`). | Aucun risque |
| `debuggable=true` en release | **Faux positif** : valeur observée = variant debug. Source manifest propre, AGP injecte `false` en release. | Aucun risque |

---

## 1. Conformité Play Store & build

### Triage consolidé

| # | Item | Sévérité | Fichier:ligne | Action |
|---|------|----------|---------------|--------|
| 1 | OverlayService déclaré `foregroundServiceType="specialUse"` subtype « Stream overlay stop button » — justification invalide au review Google | **BLOCKER** | `AndroidManifest.xml:59-64` ; `OverlayService.kt:397-457` | Sortir OverlayService du FGS specialUse (bound service de ScreenRecordService, ou pas de FGS) |
| 2 | SigningConfig retombe sur la clé debug si `local.properties` absent → AAB debug-signé rejeté | HIGH | `app/build.gradle.kts:46-50` | CI injecte signing ou échoue le build si absent |
| 3 | `isShrinkResources` absent du buildType release | HIGH | `app/build.gradle.kts:40-52` | Ajouter `isShrinkResources = true` |
| 4 | Aucun pipeline CI → risque de soumettre APK au lieu d'AAB, ou AAB debug-signé | HIGH | (absent — pas de `.github/workflows/`) | Créer `release.yml` : `bundleRelease` + injection secrets |
| 5 | `usesCleartextTraffic="true"` global, pas de NetworkSecurityConfig | HIGH | `AndroidManifest.xml:21` | NSC whitelist domaines ingest RTMP uniquement |
| 6 | ChatBubbleService + StreamerBotService en `specialUse` — justification fragile au review | MED | `AndroidManifest.xml:66-84` | Préparer déclaration détaillée pour le formulaire de review |
| 7 | JVM target mismatch : app `17` vs streampack-fork `18` | MED | `app/build.gradle.kts:59` vs `android-library-convention.gradle.kts:21` | Aligner à `17` partout |
| 8 | `compileSdk = 36` / `targetSdk = 35` — delta d'un niveau | MED | `app/build.gradle.kts:16,24` | Bumper `targetSdk = 36` (obligatoire avant 2026-08-31 de toute façon) |
| 9 | ProGuard `keep io.github.thibaultbee.streampack.** { *; }` trop large | MED | `app/proguard-rules.pro:2` | Affiner vers `public *` |
| 10 | NDK version non déclarée | LOW | `gradle.properties` | Ajouter `android.ndkVersion` si un `.so` est jamais introduit |
| 11 | `versionCode=1` / `versionName=0.1.0` — process de versioning à formaliser | LOW | `app/build.gradle.kts:23-24` | Documenter incrément monotone |
| — | MediaProjection re-consentement par session | OK | `StreamPermissionActivity.kt:61-103` | Conforme — rien à faire |
| — | `android:debuggable` absent source / release = false | OK | `AndroidManifest.xml` | Rien à faire |
| — | Zéro `.so` natif dans le release | OK | vérifié extraction AAR komuxer | Alignement 16KB N/A |
| — | `isMinifyEnabled=true`, AGP 8.13.1 / Kotlin 2.2.21 / Gradle 8.13 | OK | — | Stack récent |

### MediaProjection — preuve de conformité

`StreamPermissionActivity.kt:48-103` : launcher recréé à chaque instanciation, nouvel intent `createScreenCaptureIntent` à chaque appel, token frais (`result.resultCode` + `result.data`) passé au bind. `ScreenRecordService.isRunning` (mis false en `onDestroy`) garde contre tout `startService` sans token → évite `SecurityException` Android 14+.

---

## 2. Qualité & maintenabilité

### Cartographie (20 fichiers Kotlin, 5583 lignes app + 543 tests)

| Composant | Rôle | Lignes | Alerte |
|---|---|---|---|
| `OverlayService` | Overlay flottant, HUD, masque, panel actions, gains | 1402 | 🔴 BLOCKING taille (>500) — God Class (8 responsabilités) |
| `MainActivity` | Settings + coordination services | 690 | 🔴 BLOCKING taille ; `showPresetDialog` 97L (CC ~12) |
| `ChatBubbleService` | Chat overlay 3 fenêtres, IRC wiring, fade/drag | 587 | 🟠 WARNING taille (>300) |
| `ScreenRecordService` | FGS stream, mute/mask, reconnect loop | 453 | `runReconnectLoop` 36L ; catch silencieux L111/L297 |
| `StreamConfig` | Persistance SharedPreferences | 389 | 🟠 WARNING taille |
| `StreamerBotClient` | WebSocket, auth HMAC-SHA256, actions | 368 | Sensitive |
| `StreamerBotService` | FGS bridge | 258 | `onDestroy` écrit `enabled=false` (fragile si kill OEM, L87) |
| `TwitchIrcClient` | Socket TLS IRC, reconnect, heartbeat | 245 | Sensitive |
| `IrcMessage` | Parser tags IRCv3 + PRIVMSG | 180 | `parse` CC ~11-12 |
| `MixedAudioSource` | Mixeur PCM mic+jeu, gains live | 150 | Sensitive — **0 test** |
| `StreamLauncher` | Config codec + startStream | 99 | `configureAndStart` 57L |
| `DestinationPreset` | Data class + JSON serde | 96 | data class tout `var`, streamKey non validé |
| `AudioGains` | Singleton AtomicInteger UI↔pipeline | 39 | Sensitive — **0 test** |

### Métriques clés

| Métrique | Valeur | Seuil | Statut |
|---|---|---|---|
| Fichiers > 500 lignes (BLOCKING) | 2 (OverlayService 1402, MainActivity 690) | 0 | 🔴 FAIL |
| Fichiers > 300 lignes (WARNING) | 4 | 0 | 🟠 WARN |
| Fonctions > 30 lignes | 7 | 0 | 🟠 FAIL |
| CC max | ~14 (`attachExpandedTouch`) | ≤10 | 🔴 FAIL |
| TODO/FIXME | 0 | 0 | ✅ |
| Logs debug / code commenté oubliés | 0 | 0 | ✅ |
| Secrets hardcodés | 0 | 0 | ✅ |
| Magic numbers (couleurs hex) | 3 (`OverlayService.kt:1234-1236`) | 0 | 🟠 WARN |
| Dead code | 2 fonctions (`OverlayService.kt:1286,1293`) | 0 | 🟠 WARN |
| Dépendances circulaires | 0 | 0 | ✅ |

### Fork StreamPack — bien maîtrisé

1 seul commit propriétaire (`1141dd7`) au-dessus de 3.1.2, sur 1 fichier (`EncodingPipelineOutput.kt`, +15/-2). Fix race RTMP audio csd-0, documenté (commit + commentaire inline). Risque de divergence **bas** tant que le delta reste minimal. Pas de ticket upstream identifié → à surveiller.

---

## 3. Tests

### Inventaire (39 tests, 3 fichiers, 0 androidTest)

| Fichier | Cible | Tests | Qualité |
|---|---|---|---|
| `IrcMessageParseTest` | `IrcMessage.parse` (15) + `backoffDelay` (3) | 18 | Dense, bons vecteurs |
| `StreamerBotBackoffTest` | `backoffDelay` | 3 | OK |
| `StreamerBotProtocolTest` | auth hash + builders + event/action parse | 18 | Excellent (vecteur Python indép.) |

### 5 métriques de fiabilité

| Métrique | Valeur | Statut |
|---|---|---|
| Tests vides | 0/39 | ✅ |
| Tests triviaux | 0/39 | ✅ |
| Ratio mock:assert | 0:123 (zéro mock) | ✅ |
| Couverture de ligne | **non mesurée — ni JaCoCo ni Kover configuré** | 🔴 BLOCKING outillage |
| Analyse statique | detekt/ktlint absents (compilateur Kotlin seul) | 🟠 Partiel |

### Gaps critiques (modules Sensitive non testés)

| Priorité | Brick test | Cible | Pourquoi |
|---|---|---|---|
| **P1** | `MixedAudioSourceTest` | `mixInPlace()` | **Bug racine prod déjà rencontré** (capacity vs position). 0 test. Pure JVM, testable direct. |
| P2 | `AudioGainsTest` | clamping + conversion permil→float | Lu ~50×/s ; valeur hors borne casse le mix sans crash |
| P3 | `DestinationPresetSerializationTest` | round-trip JSON + corruption | Corruption silencieuse = perte config stream utilisateur |
| P4 | `AudioSourceMigrationTest` | `fromStorageKey` (`game`→MIX) | Migration legacy ; régression silencieuse possible |
| P5 | `TwitchIrcClientHandleLineTest` | dispatch PING/PONG, state machine | Bug subtil → reconnect en boucle (MockK requis) |
| P6 | `StreamerBotClientRouteFrameTest` | auth flow, corrélation id | État mutable peut fuir entre reconnects (MockK) |
| P7 | Config JaCoCo ou Kover | build.gradle.kts | Sans outil, couverture = postulat, CI non gateable |

---

## 4. Sécurité (ASVS L2)

### Verdict : aucun BLOCKER. 3 MEDIUM défensifs.

| # | Finding | Sévérité | Fichier:ligne | Remédiation |
|---|---|---|---|---|
| 1 | Clé de stream + password Streamer.bot en SharedPreferences non chiffré | MED | `StreamConfig.kt:11` | `EncryptedSharedPreferences` (Jetpack Security Crypto) |
| 2 | `usesCleartextTraffic="true"` global, pas de scoping | MED | `AndroidManifest.xml:21` | `network_security_config.xml` scopé ingest RTMP |
| 3 | Défaut RTMP Twitch en clair (`rtmp://`) alors que RTMPS dispo | MED | `StreamConfig.kt:348`, `DestinationPreset.kt:33` | Défaut → `rtmps://live.twitch.tv/443/app/` |
| 4 | WebSocket Streamer.bot en `ws://` sans fallback `wss://` | LOW | `StreamerBotClient.kt:167` | Option `wss://` ou warning si host non-Tailscale |
| 5 | `sendRaw` loggue la commande complète (inclut `PASS`) | LOW | `TwitchIrcClient.kt:216` | Masquer contenu commandes sensibles |
| 6 | Logs `Log.i/d/v` non supprimés en release | LOW | `proguard-rules.pro` | `assumenosideeffects` sur `Log.v/d/i` |

### Points forts à conserver

- `allowBackup="false"` → vecteur backup ADB éliminé.
- Tous services `exported=false`, receivers `RECEIVER_NOT_EXPORTED`.
- IRC Twitch sur TLS 6697. Kick déjà en `rtmps://`.
- Pipeline signing vault-backed propre (aucun secret en repo ni en historique git).
- Broadcasts internes scopés `setPackage(packageName)` → clé de stream non exposable.
- Auth Streamer.bot via SHA256 salé+challengé (jamais de password sur le wire).

---

## 5. Classification risque consolidée (proposée — à valider par Jay au §7 du CDC)

| Module | Niveau | Couverture cible |
|---|---|---|
| `ScreenRecordService`, `MixedAudioSource`+`AudioGains`, `StreamLauncher`, `StreamerBotClient`, `TwitchIrcClient`, `streampack-fork` | **Sensitive** | 90% |
| `OverlayService`, `ChatBubbleService`, `MainActivity`, `StreamConfig`, `DestinationPreset`, `StreamerBotService`, `StreamerBotProtocol`, adapters | **Standard** | 80% |
| `scripts/` (signing, icônes) | **Tooling** | 60% |

Aucun module **Critical** (pas d'auth compte, pas de paiement, pas de crypto de sécurité). Note : si IAP/Play Billing est ajouté un jour, ce module sera Critical (95%).

---

## 6. Implications pour CDC + PET

- **CDC §4 Architecture** : refonte OverlayService (sortie FGS specialUse) + découpage des 2 God Classes.
- **CDC §7 Risque** : tableau §5 ci-dessus.
- **CDC §8 FMEA** : sur modules Sensitive (capture/stream, mixeur audio).
- **CDC §9 Human Quality Gates** : ergonomie non-invasive (déjà forte), à formaliser.
- **PET — bricks prioritaires** :
  1. 🔴 Sortir OverlayService du FGS specialUse (blocker review)
  2. Hygiène release : `isShrinkResources`, signing CI-safe, NetworkSecurityConfig, RTMPS défaut
  3. Pipeline CI `release.yml` (bundleRelease + secrets)
  4. Outillage couverture (JaCoCo/Kover) + tests P1-P4 (mixeur audio en tête)
  5. GlitchTip opt-in + privacy policy + Data Safety
  6. targetSdk 36, JVM target aligné, ProGuard affiné
  7. Découpage OverlayService + MainActivity (dette maintenabilité)
  8. EncryptedSharedPreferences (sécurité défensive)

---

*Audit généré par 4 agents Takumi (lecture seule). Aucune modification de code. Référencé par CDC-Hoso + PET-Hoso.*
