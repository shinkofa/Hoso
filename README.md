# Hoso (放送)

> Personal mobile streaming app — Android screen + mic to RTMP (Twitch, YouTube Live, Kick, custom).

## What it does

Hoso captures your Android screen and microphone, encodes to H.264/AAC, and streams via RTMP to any destination. Built for personal IRL/mobile streaming with minimal UI and maximum reliability.

## Features

- **Multi-destination presets** — Twitch / YouTube Live / Kick / custom RTMP. Switch in one tap.
- **Resolution presets** — Native-fit 1080p/720p, crop modes, 480p fallback.
- **Configurable bitrate** — 1000-8000 kbps with CBR encoding.
- **Overlay stop button** — Draggable overlay with auto-fade (30% alpha after 7s idle, instant wake on touch).
- **Foreground service** — Stable capture via MediaProjection + notification controls.

## Stack

| Component | Technology |
|-----------|-----------|
| Language | Kotlin |
| Min SDK | 29 (Android 10) |
| Target SDK | 35 |
| Streaming | [StreamPack 3.1.2](https://github.com/ThibaultBee/StreamPack) (local fork with Twitch audio race fix) |
| Build | Gradle 8.13, AGP 8.13.1 |
| UI | Material Components + ViewBinding |

## StreamPack fork

Hoso uses a local composite build of StreamPack with a fix for a race condition where the AAC sequence header (csd-0) was emitted before the RTMP publish handshake completed, causing Twitch to never receive audio codec info. The patch moves `endpointInternal.startStream()` before encoder coroutine launch.

- Fork: `streampack-fork/` (gitignored, lives in its own repo)
- Upstream PR: [ThibaultBee/StreamPack#294](https://github.com/ThibaultBee/StreamPack/pull/294)

## Build

```bash
# Requires Android Studio JBR or JDK 17+
export JAVA_HOME="/path/to/android-studio/jbr"

# The StreamPack fork must be cloned alongside:
# git clone https://github.com/theermite/StreamPack streampack-fork
# cd streampack-fork && git checkout fix/twitch-audio-race-hoso

./gradlew assembleDebug
```

## Install

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Roadmap

See [docs/Roadmap.md](docs/Roadmap.md) for the full feature plan (mute, pause, privacy mode, auto-reconnect, HUD, chat overlay, Streamer.bot bridge, audio mix).

## Author

[The Ermite](https://solo.to/theermite)
