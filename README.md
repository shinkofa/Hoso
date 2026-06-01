# Hoso (放送)

> Personal mobile streaming app — Android screen + mic to RTMP (Twitch, YouTube Live, Kick, custom).

## What it does

Hoso captures your Android screen and microphone, encodes to H.264/AAC, and streams via RTMP to any destination. Built for personal IRL/mobile streaming with minimal UI and maximum reliability.

## Features

- **Multi-destination presets** — Twitch / YouTube Live / Kick / custom RTMP. Switch in one tap.
- **Resolution presets** — Native-fit 1080p/720p, crop modes, 480p fallback.
- **Configurable bitrate** — 1000-8000 kbps with CBR encoding.
- **Audio source selection** — Mic-only or Mix mode (mic + game audio with independent gain sliders).
- **Floating overlay** — Draggable start/stop toggle with auto-fade. Stream control stays on screen while gaming.
- **Twitch chat overlay** — Live IRC chat in a floating bubble with 3 size presets (S/M/L), adjustable opacity, free drag positioning, and status indicators (LIVE / OFF / connecting). Uses a 3-window click-through architecture so the game underneath receives all touches in the chat list region.
- **Streamer.bot bridge** — WebSocket client connecting to Streamer.bot on your PC (LAN / Tailscale). Browse and trigger published actions from the overlay. Auto-reconnect with exponential backoff.
- **Collapsible settings cards** — Each configuration section (Destination, Stream, Audio, Streamer.bot) folds/unfolds with a single tap. State persisted between sessions.
- **Unified Start/Stop CTA** — Single action button that toggles between "PASSER EN DIRECT" and "ARRETER LE STREAM" with color feedback.
- **Auto-reconnect** — Automatic stream reconnection with configurable retry count.
- **HUD live stats** — Duration, bitrate, connection state, mic status in the expanded overlay.
- **Privacy mode** — One-tap screen mask for sensitive content during stream.
- **Foreground service** — Stable capture via MediaProjection + notification controls.

## Stack

| Component | Technology |
|-----------|-----------|
| Language | Kotlin |
| Min SDK | 29 (Android 10) |
| Target SDK | 35 |
| Streaming | [StreamPack 3.1.2](https://github.com/ThibaultBee/StreamPack) (local fork with Twitch audio race fix) |
| Build | Gradle 8.13, AGP 8.13.1 |
| UI | Material Components 2 + ViewBinding |
| Chat | Custom Twitch IRC client (no external deps) |
| Bot bridge | OkHttp WebSocket |

## Architecture

```
MainActivity (settings screen)
  -> OverlayService (foreground, floating controls)
     -> StreamPermissionActivity (MediaProjection grant)
        -> ScreenRecordService (foreground, capture + RTMP)
     -> ChatBubbleService (foreground, 3-window overlay)
        - Body window (FLAG_NOT_TOUCHABLE — visual only)
        - Header overlay (drag, close, opacity controls)
        - Dot overlay (size cycle S/M/L)
     -> StreamerBotService (foreground, WebSocket bridge)
```

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

See [docs/Roadmap.md](docs/Roadmap.md) for the full feature plan.

## Contributing

Issues and pull requests are welcome. Hoso is built around a small, opinionated stack — please open an issue to discuss substantial changes before sending a PR. Atomic commits, conventional commit messages, and tests on critical paths (auth, streaming pipeline, IRC client).

## License

Hoso is released under the [Apache License 2.0](LICENSE). Third-party attributions are listed in [NOTICE](NOTICE).

## Author

[The Ermite](https://solo.to/theermite)
