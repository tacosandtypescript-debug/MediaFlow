# MediaFlow

Android app to analyze, download, and play audio and video from public HTTPS links — YouTube, YouTube Music, TikTok, Instagram, Facebook, and X Spaces.

Kotlin + Jetpack Compose. Spanish UI. Visual identity: MediaFlow purple `#7C3AED` (not a clone of other music apps).

Package: `com.mediaflow.app` · Repo: [tacosandtypescript-debug/MediaFlow](https://github.com/tacosandtypescript-debug/MediaFlow) · branch `master` · version **1.2.5** (`versionCode` 4).

---

## Features

**Home**
- Paste a public HTTPS URL, choose Audio or Video, optional file name, then analyze and download.
- Recent downloads on the home grid.
- X Spaces: listen live when X publishes a stream; replay and **Descargar Space** when the Space has ended. Record while live is independent of pause. No invented metadata or DRM bypass.

**Library**
- All / video mosaic / audio list, sort, search, multi-select, favorites, local playlists.
- Play all, shuffle, drag-to-reorder audio queue.
- Artwork from the largest available thumbnail (`maxres` when YouTube publishes it).

**Player**
- Dedicated Now Playing (audio, video, X Space), miniplayer, queue, ±10 s, skip.
- Audio visualizers driven by player PCM (no microphone permission).
- System bars follow cover colors. Visualizer can be turned off.

**Downloads**
- Queue with progress notifications. HLS Spaces use concurrent fragment downloads.
- Files published under MediaStore (`Music/MediaFlow/`, `Movies/MediaFlow/`).

---

## Privacy and secrets

- No login, no session cookies, no Google/X/TikTok API keys in this repo.
- `local.properties`, keystores, `.apk`, Gradle caches, and git worktrees are gitignored.
- Guest tokens for public X endpoints are requested at runtime and not stored as developer secrets.

---

## Responsible use

You are responsible for the URLs you paste and for local copyright law. MediaFlow does not circumvent DRM and is not a store listing.

---

## Stack

| Layer | Tech |
|-------|------|
| UI | Kotlin, Jetpack Compose, Material 3 |
| Extract | [yt-dlp](https://github.com/yt-dlp/yt-dlp) via `yt-dlp-android` in `:data` |
| Playback | libmpv |
| Storage | MediaStore + local download history |
| Images | Coil |
| SDK | JDK 17 · minSdk 24 · targetSdk 37 |

Modules: `:app` (Compose UI) · `:domain` (use cases) · `:data` (yt-dlp, player, Spaces) · `:core:model`.

---

## Build

Needs **JDK 17** and an Android SDK.

```bash
./gradlew :app:assembleDebug
```

Outputs (ABI splits):

| File | Use |
|------|-----|
| `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk` | Most phones (including Samsung Galaxy A36) |
| `app/build/outputs/apk/debug/app-x86_64-debug.apk` | x86_64 emulator |
| `app/build/outputs/apk/debug/app-universal-debug.apk` | Any ABI, larger |

```bash
adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

```bash
./gradlew test
```

Prebuilt APKs are attached to [GitHub Releases](https://github.com/tacosandtypescript-debug/MediaFlow/releases) (debug-signed; enable “install unknown apps”).

---

## License

[MIT](LICENSE)
