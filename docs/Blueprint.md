# Blueprint — Hoso (放送)

**Version** : 0.1.0
**Date** : 2026-04-19
**Statut** : MVP en cours

---

## POUR QUOI

Jay a besoin de streamer ses jeux mobiles (Dofus Touch, MOBA) depuis son telephone vers Twitch. Les 3 apps consumer testees (Twitch Mobile, Streamlabs Mobile, Prism Live Studio) plantent toutes a cause de la gestion foreground service / overlay sur Android.

**Alignement L3** : outil invisible qui sert Jay directement (D12 — Build For Me First).
**Alignement L2** : le streaming = visibilite magnetique naturelle pour un Projecteur.
**Alignement L1** : app perso, scope reduit, weekend project.

## Architecture

App Android native Kotlin, single-activity. Pas de backend, pas de serveur.

```
MainActivity (UI)
    └── ScreenRecordService (foreground service, persistant)
            └── StreamPack SDK (MediaProjection + RTMP)
                    └── Twitch ingest (RTMP)
```

### Stack

| Couche | Choix |
|--------|-------|
| Langage | Kotlin |
| SDK | StreamPack 3.1.2 (Apache 2.0) |
| Screen capture | Android MediaProjection API |
| Audio | MicrophoneSource (micro) |
| Encoding | MediaCodec H264 hardware |
| Protocole | RTMP vers Twitch |
| Persistence | SharedPreferences (config stream) |
| UI | XML layouts + ViewBinding |

### Decisions

| Decision | Raison |
|----------|--------|
| StreamPack SDK (pas de fork) | Dependency Gradle, pas besoin de modifier le SDK |
| Foreground service persistant | LE differenciateur — c'est ce qui casse chez les concurrents |
| Micro seulement (pas audio systeme) | AudioPlaybackCapture est bloque par certains jeux. Micro = fiable partout. |
| Pas de YouTube | Twitch prioritaire, YouTube = bonus futur |
| SharedPreferences (pas Room/DB) | 5 settings max, pas besoin d'ORM |
| Dark theme only | App perso gaming, pas besoin de light mode |
| Pas de i18n | App perso, FR only |

## Devices cibles

| Device | OS | Risque |
|--------|-----|--------|
| Oppo CPH2173 | Android 14 (ColorOS) | Battery optimization tue les foreground services |
| Xiaomi Redmi Note 14 5G | Android 15 (HyperOS) | Pire score background killing |

**Mitigation** : guide post-install pour whitelist battery optimization par constructeur.

## Hors scope

- Publication Play Store
- Multi-plateforme
- Chat overlay
- Scenes / transitions
- Recording local
- i18n / multi-langue
