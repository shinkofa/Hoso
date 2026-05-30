# Hoso — Roadmap Features

> Source of truth for the streaming app feature plan. Validated by Jay
> on 2026-05-29 after the StreamPack Twitch audio fix landed.
> Order is dependency-driven, not chronological.

## Status legend

- ⬜ Not started
- 🟡 In progress
- ✅ Done
- ⏸️ Deferred (waiting on something)
- ❌ Dropped

## Groupe 1 — Fondations (faible risque, débloque le reste)

| # | Feature | Notes techniques | Effort | Statut |
|---|---------|------------------|--------|--------|
| G1.1 | Auto-save preferences étendu | Étend `StreamConfig` (déjà existant) pour stocker tous les nouveaux réglages des features suivantes (présets destination, alpha overlay, comportements mute, etc.). | Petit | ✅ `58dc549` |
| G1.2 | Multi-destination (présets) | Préset par destination : Twitch / YouTube Live / Kick / custom RTMP. Stockage clé / URL / résolution / bitrate par préset. UI : spinner ou liste de profils. Repose sur G1.1. | Moyen | ✅ `951aced` |

## Groupe 2 — UX overlay (polish, faible risque)

| # | Feature | Notes techniques | Effort | Statut |
|---|---------|------------------|--------|--------|
| G2.1 | Overlay transparence auto | Timer 5-10 s sans touch → anim alpha vers ~30 %. Touch / drag → restore 100 %. Côté `OverlayService` uniquement. | Petit | ✅ `915e94e` (7 s, 30 %) |

## Groupe 3 — Contrôles live (UX streamer immédiat)

| # | Feature | Notes techniques | Effort | Statut |
|---|---------|------------------|--------|--------|
| G3.1 | Mute mic | `IAudioInput.isMuted` via `IWithAudioSource` (StreamPack SDK natif). Bouton overlay sky blue / danger red. | Petit-Moyen | ✅ a903479 |
| G3.2 | Pause stream | Masque opaque plein écran (TYPE_APPLICATION_OVERLAY MATCH_PARENT) capté par MediaProjection + force-mute. State machine sauvegarde mute pré-masque. Z-order via remove+re-add des contrôles. | Moyen | ✅ 61ad178 |
| G3.3 | Privacy mode | Réutilise la machinerie G3.2 avec `MASK_PRIVACY` + label dédié. 1-tap sur bouton privacy = mute + masque opaque. | Moyen | ✅ 09bbb08 |

## Groupe 4 — Robustesse réseau (stream fiable mobile)

| # | Feature | Notes techniques | Effort | Statut |
|---|---------|------------------|--------|--------|
| G4.1 | Auto-reconnect RTMP | Watcher sur `streamer.isOpenFlow` (drop(1), distinctUntilChanged) détecte les déco non voulues. Backoff 1 → 2 → 5 → 10 → 15 → 30 s puis cap 30 s, 20 tentatives ≈ 8 min. Service rappelle `startStream(descriptor)` lui-même via URL capturée par `ACTION_REMEMBER_URL`. Notification FGS live ("Reconnexion n/20") + Toast overlay perte/reprise/abandon. | Moyen | ✅ bea7df2 |
| G4.5 | Overlay collapse/expand | Rangée de boutons permanente non-ergonomique en stream. Bascule vers état COLLAPSED (un seul trigger 48dp draggable) ↔ EXPANDED (rangée complète centrée à l'écran). Auto-collapse 5 s d'inactivité, auto-expand forcé sur activation masque (mute/privacy/pause) pour garder Resume à un tap. Zone HUD réservée (`visibility=gone`) pour câblage G5.1. Design icônes/palette différé jusqu'à intégration complète des features. | Moyen | ✅ 9832348 |

## Groupe 5 — Instrumentation (HUD)

| # | Feature | Notes techniques | Effort | Statut |
|---|---------|------------------|--------|--------|
| G5.1 | HUD overlay live stats | Audit SDK livré : RTMP n'expose pas FPS / RTT / frame-drops (SRT-only). Scope honnête retenu : durée HH:MM:SS depuis premier startStream (survit aux reconnexions), bitrate sortant via `TrafficStats.getUidTxBytes` (delta 1 s — proxy du RTMP qui domine le trafic UID), état connexion (LIVE / RECONNEXION n/20 / PERDU) dérivé du watcher G4.1, état mic. Ticker 1 Hz actif uniquement en EXPANDED (zéro coût en COLLAPSED). | Moyen | ✅ 1e16ad7 |

## Groupe 6 — Intégrations externes (réseau + UI complexes)

| # | Feature | Notes techniques | Effort | Statut |
|---|---------|------------------|--------|--------|
| G6.1 | Streamer.bot bridge | WebSocket client vers Streamer.bot (port local PC ou exposé). Permet alerts / commandes depuis mobile. Auth + reconnexion. | Moyen | ⬜ |
| G6.2 | Chat overlay (Twitch IRC) | Twitch IRC client (lib `tmi.kt` ou socket maison) + permission overlay + UI scroll. Multi-destination potentielle si YouTube / Kick supportés plus tard. | Gros | ⬜ |

## Groupe 7 — Audio avancé (R&D)

| # | Feature | Notes techniques | Effort | Statut |
|---|---------|------------------|--------|--------|
| G7.1 | Mix audio mic + game audio | Android 10+ `AudioPlaybackCapture` API (game audio via MediaProjection) + mix avec `AudioRecord` MIC. Complexité OEM (ColorOS peut filtrer). Sujet déjà exploré et archivé dans `.research/audio-backup-revert-archive/MixedAudioSource.kt`. Session dédiée. | Gros | ⬜ |

## Ordre d'exécution validé (2026-05-29)

1. **G1.1 + G1.2 + G2.1** — fondation + quick win UX (livrable cohérent, zéro risque sur le pipeline streaming) — ✅ livré 2026-05-30
2. **G3.1 + G3.2 + G3.3** — contrôles essentiels live — ✅ livré 2026-05-30 (a903479 + 61ad178 + 09bbb08)
3. **G4.1** — fiabilité mobile — ✅ livré 2026-05-30 (bea7df2)
4. **G4.5** — ergonomie overlay (collapse/expand) — ✅ livré 2026-05-30 (9832348)
5. **G5.1** — instrumentation streamer (HUD honnête) — ✅ livré 2026-05-30 (1e16ad7)
6. **G6.1** — Streamer.bot bridge
7. **G7.1** — mix audio (session dédiée)
8. **G6.2** — chat overlay (session dédiée)

## Méthodologie pour chaque feature

Per `rules/Quality.md` + `rules/Workflows.md` :

- Reformulation avant code (>1 fichier ou comportement visible)
- TDG : tests d'abord (où c'est testable côté JVM — `StreamConfig`, parsers, état overlay)
- Atomic commits — un commit par feature minimum, par sous-étape si feature grosse
- Backup tag tous les 3-4 commits
- Veille proactive si bloqué > 1-2 cycles (memory rule `feedback_proactive_veille.md`)
- Session report par jour de travail dans `docs/Sessions/`
- Mise à jour de ce fichier (statut) à la fin de chaque feature

## Hors-roadmap (déjà fait — 2026-05-29)

- ✅ Fix audio FGS bitmask + MIC source + ILogger verbose (commit `ad8e7a0`)
- ✅ Gradle 8.13 + AGP 8.13.1 (commit `32c15ca`)
- ✅ Fork StreamPack `fix/twitch-audio-race-hoso` + composite build (commit `fe9bb70` + fork `1141dd7`)
- ✅ Fix `unbindService` manquant dans `stoppedReceiver` (commit `09d6367`)
- ✅ Push fork sur `github.com/theermite/StreamPack`
- ✅ PR upstream `ThibaultBee/StreamPack#294`
