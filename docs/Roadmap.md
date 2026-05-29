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
| G3.1 | Mute mic | Couper `AudioRecord` côté StreamPack sans stopper le stream. Vérifier API SDK (`microphoneSource.mute()` ou injecter silence dans la source). Bouton dédié overlay. | Petit-Moyen | ⬜ |
| G3.2 | Pause stream | StreamPack 3.1.2 ne supporte pas de pause native ; Twitch non plus. Stratégie : freeze l'écran sur dernière frame (Surface noir / image) + mute mic. Vraie "pause UX" sans déco RTMP. | Moyen | ⬜ |
| G3.3 | Privacy mode | Combinaison 1-tap : mute mic + masque opaque Surface au-dessus du screen capture. Override visuel total mais le stream continue. | Moyen | ⬜ |

## Groupe 4 — Robustesse réseau (stream fiable mobile)

| # | Feature | Notes techniques | Effort | Statut |
|---|---------|------------------|--------|--------|
| G4.1 | Auto-reconnect RTMP | Hook sur `endpoint` failure → backoff exponentiel (1 s → 2 s → 5 s → 10 s) → retry. Préserver le notification FGS pendant la reconnexion. Toast / log overlay sur reconnexions. | Moyen | ⬜ |

## Groupe 5 — Instrumentation (HUD)

| # | Feature | Notes techniques | Effort | Statut |
|---|---------|------------------|--------|--------|
| G5.1 | HUD overlay live stats | Bitrate live, fps, RTT (si exposé), frame drops, durée stream, état audio (mute on / off). Récupérer via API StreamPack (`streamer.encoder.*`) ou hook FLV. À auditer ce que le SDK expose précisément. | Moyen-Gros | ⬜ |

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

1. **G1.1 + G1.2 + G2.1** — fondation + quick win UX (livrable cohérent, zéro risque sur le pipeline streaming)
2. **G3.1 + G3.2 + G3.3** — contrôles essentiels live
3. **G4.1** — fiabilité mobile
4. **G5.1** — instrumentation streamer
5. **G6.1** — Streamer.bot bridge
6. **G7.1** — mix audio (session dédiée)
7. **G6.2** — chat overlay (session dédiée)

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
