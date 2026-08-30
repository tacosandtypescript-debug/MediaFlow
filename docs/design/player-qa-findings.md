# Player QA findings (current audio player)

Read-only investigation of the **current** player stack. No UI restyle. Paths are repo-absolute under `/home/isaac/MediaFlow`.

The player does **not** read embedded tags or covers at playback time. Title/art/duration come from Space records, download rows, URI path segments, and libmpv clock — not ID3/`©nam`/`covr`. Seek-end wiring seeks to **0 ms**. Foreground service re-opens the same URI and can wipe title/artwork.

---

## 1. Artwork

### Does it load embedded album art from the file?

**No.** Nothing in the playback path extracts `covr` / APIC / MediaStore album art / mpv albumart bitmaps.

| Layer | What it actually does |
|---|---|
| `MediaArtwork` | Coil `SubcomposeAsyncImage` only if `isLoadableArtworkUrl` — HTTP(S) images, `content://` image URIs, or local **image** files. Audio/video extensions are rejected. |
| `PlayerViewModel.open` | `artworkUrl = preferredArtworkUrl(preservedArtwork ?: download?.thumbnailUri, space?.host?.avatarUrl)`. Never the media file. Never gallery. |
| `ThumbnailPersister` | Sidecar / remote JPEG-PNG-WebP into `filesDir/thumbs`. Not embedded in the container. |
| `MediaMetadata` / writers | Text tags only. No cover field, no `covr`, no APIC. |
| `MpvPlaybackEngine` | Sees albumart **video** tracks only to **ignore** them (`isAudioOnly = true`). Does not export a bitmap. |
| `MpvConfig` | `audio-display=no` — mpv will not render embedded cover as video. |

`isLoadableArtworkUrl` explicitly refuses media files (so Coil never even tries a file that happens to contain a cover):

```32:59:app/src/main/java/com/mediaflow/app/ui/common/media/MediaArtwork.kt
private val MEDIA_FILE_EXTENSIONS = setOf(
    "mp4", "m4a", "mp3", "webm", "mkv", "mov", "aac", "opus", "ogg", "wav", "m4v", "m3u8", "m3u",
)
/** True only for HTTP(S) images, content image URIs, or local image files. */
fun isLoadableArtworkUrl(url: String?): Boolean {
    // ...
    if (extension in MEDIA_FILE_EXTENSIONS) return false
```

`content://media/external/audio/media/20567` also fails the image checks (`/images` / `image` / image extension), so the MediaStore audio row is never used as art.

Player wiring:

```226:242:app/src/main/java/com/mediaflow/app/ui/player/PlayerViewModel.kt
            val preservedArtwork = activeState.artworkUrl.takeIf {
                activeState.mediaId == mediaUri || activeState.filePath == mediaUri
            }
            val download = withTimeoutOrNull(250) {
                downloadRepo.observeDownloads().first().firstOrNull { item ->
                    item.localUri == mediaUri || item.id == mediaUri || item.sourceUrl == mediaUri
                }
            }
            playerService.openMedia(
                // ...
                artworkUrl = preferredArtworkUrl(
                    preservedArtwork ?: download?.thumbnailUri,
                    space?.host?.avatarUrl,
                ),
```

`galleryRepository` is injected on the VM but **only used for delete** (`PlayerViewModel.kt:392–398`). Library overlay thumbs (`overlayThumbnails` in `LibraryViewModel.kt:204–227`) never reach the player unless the 250 ms download lookup hits.

Writers never persist a cover:

```11:17:data/src/main/kotlin/com/mediaflow/data/media/metadata/MediaMetadata.kt
data class MediaMetadata(
    val title: String? = null,
    val artist: String? = null,
    val albumArtist: String? = null,
    val album: String? = null,
    val description: String? = null,
    val date: String? = null,
)
```

```180:188:data/src/main/kotlin/com/mediaflow/data/media/metadata/Mp4MetadataEditor.kt
        metadata.title?.let { writeTextItem(ilstContent, TAG_TITLE, it) }
        metadata.artist?.let { writeTextItem(ilstContent, TAG_ARTIST, it) }
        // ... no TAG_COVR
```

```54:59:data/src/main/kotlin/com/mediaflow/data/media/metadata/Id3MetadataEditor.kt
        metadata.title?.let { writeTextFrame(framesStream, "TIT2", it) }
        // ... TALB / TPE1 / TYER — no APIC
```

mpv treats embedded covers as non-video and disables audio display:

```27:27:data/src/main/kotlin/com/mediaflow/data/player/MpvConfig.kt
        mpv.setOptionString("audio-display", "no") // avoid blank video canvas for pure audio
```

```357:374:data/src/main/kotlin/com/mediaflow/data/player/MpvPlaybackEngine.kt
    private fun inspectTrackList(node: MPVNode) {
        // ...
                val isAlbumArt = albumartNode?.asBoolean() == true
                if (!isAlbumArt) {
                    foundVideo = true
                }
        _state.value = _state.value.copy(
            isAudioOnly = !foundVideo,
            isVideoAvailable = foundVideo,
        )
    }
```

`AudioPlayerView` then feeds Coil the (usually empty) URL:

```53:59:app/src/main/java/com/mediaflow/app/ui/player/components/AudioPlayerView.kt
        MediaArtwork(
            artworkUrl = preferredArtworkUrl(artworkUrl, space?.host?.avatarUrl),
            size = 220.dp,
            // ...
        )
```

### What happens with no cover?

`MediaArtwork` paints a gradient box + `Icons.Outlined.Audiotrack` (or `GraphicEq` for Space). No “unknown album”, no MediaStore `albumart` URI, no extracted frame.

```96:128:app/src/main/java/com/mediaflow/app/ui/common/media/MediaArtwork.kt
    val loadable = isLoadableArtworkUrl(artworkUrl)
    // ...
        if (loadable) {
            SubcomposeAsyncImage(
                // loading / error = same placeholder icon
            )
        } else {
            placeholder()
        }
```

**Also wiped after a successful lookup:** `MediaPlaybackService.start` re-calls `openMedia` **without** `artworkUrl` (defaults `null`). Last writer wins.

```247:256:app/src/main/java/com/mediaflow/app/ui/player/PlayerViewModel.kt
            MediaPlaybackService.start(
                context = app,
                mediaUri = mediaUri,
                title = space?.title ?: title,  // PlayerScreen always passes title=null
                isLive = effectiveLive,
```

```184:190:data/src/main/kotlin/com/mediaflow/data/player/background/MediaPlaybackService.kt
                        playerService.openMedia(
                            mediaId = mediaUri,
                            filePath = mediaUri,
                            title = title,
                            autoPlay = true,
                            isLive = isLive,
                        )
```

Notification art only loads **http** URLs (`PlaybackNotificationManager.kt:61–68`), never `file:///…/thumbs/….jpg`. `MediaSessionController.updateMetadata` is **never called** (dead API). Lockscreen/notification large icon is blank for local files.

MediaStore gallery rows set **no** `thumbnailUri` (`MediaStoreGalleryRepository.kt:138–155`).

---

## 2. Title

Construction in the UI combine:

```122:127:app/src/main/java/com/mediaflow/app/ui/player/PlayerViewModel.kt
        val uri = service.filePath.orEmpty()
        PlayerUiState(
            mediaUri = uri,
            title = space?.title ?: service.title ?: uri.substringAfterLast('/'),
```

`PlayerScreen` never passes a title into `open`:

```83:87:app/src/main/java/com/mediaflow/app/ui/player/PlayerScreen.kt
    LaunchedEffect(mediaUri) {
        if (mediaUri.isNotBlank()) {
            viewModel.open(mediaUri)
        }
    }
```

```118:118:app/src/main/java/com/mediaflow/app/ui/player/PlayerScreen.kt
    val displayTitle = uiState.title.ifBlank { mediaUri.substringAfterLast('/').ifBlank { "Media" } }
```

Nav is `player/{encodedUri}` only (`AppNavigation.kt:247–258`, `MediaFlowDestination.kt:47–53`). For `content://media/external/audio/media/20567`, `substringAfterLast('/')` **is the MediaStore `_ID`**.

`openMedia` uses the same fallback:

```256:261:domain/src/main/kotlin/com/mediaflow/domain/player/PlayerService.kt
                _uiState.value = _uiState.value.copy(
                    mediaId = mediaId,
                    filePath = filePath,
                    title = title ?: filePath.substringAfterLast('/'),
```

### When the player shows `20567` / MediaStore ids / file stems

1. **URI last segment** — `content://…/audio/media/20567` → `"20567"`. `file:///…/foo.m4a` → `"foo.m4a"`.
2. **Service second `openMedia`** — `MediaPlaybackService` passes `title = space?.title ?: title` from `PlayerScreen`, i.e. **null**, overwriting a download title the VM just set.
3. **Download lookup miss / 250 ms timeout** (`PlayerViewModel.kt:229–233`) — MediaStore `id` is the content URI; download `id` is a hash. Match only if `localUri` equals the player URI **and** `observeDownloads().first()` emits in time.
4. **Gallery DISPLAY_NAME, not TITLE** — projection is `_ID, DISPLAY_NAME, MIME_TYPE, SIZE, DATE_ADDED` (`MediaStoreGalleryRepository.kt:85–91`). No `MediaStore.Audio.Media.TITLE`, no album, no artist. `DownloadItem.title = name` (filename).
5. **Writers stamp the file stem, not a real track title:**

```235:240:data/src/main/kotlin/com/mediaflow/data/repository/Media3DownloadRepository.kt
                val mediaMetadata = MediaMetadata(
                    title = com.mediaflow.data.ytdlp.YtDlpRuntime.fileStem(safeName),
                )
```

```227:241:data/src/main/kotlin/com/mediaflow/data/download/YtDlpPlatformDownloader.kt
            val metadata = space?.let { s -> MediaMetadata.fromXSpace(s) }
                ?: MediaMetadata(
                    title = YtDlpRuntime.fileStem(request.fileName) ?: current.title?.ifBlank { null },
                )
            mediaMetadataWriter.writeMetadata(audioReady, metadata)
        // ...
            title = audioReady.nameWithoutExtension,
```

6. **Space title always wins** (`space?.title ?: …`) even if the file has a better ID3 `TIT2` / `©nam`.

Library rows can look correct (`space?.title ?: item.title ?: item.fileName`, `AudioLibraryView.kt:61`) while the now-playing screen shows `20567` because the player never gets that string over nav.

### When a real ID3 title is ignored

Always, at playback:

- No `MediaMetadataRetriever`.
- No mpv `media-title` / `metadata` observe (`MpvPlaybackEngine` only observes `time-pos`, `duration`, `pause`, `eof-reached`, `speed`, `volume`, `mute`, `vid`, `track-list`).
- Combine title never reads file tags.
- Even when tags **were** written at download, the player prefers Space title → `PlayerServiceState.title` → URI stem.

So a tagged `TIT2="Real Song"` is ignored whenever:

- `currentSpace.title` is set,
- `service.title` is a filename / id from `openMedia`,
- or the URI last segment is used.

`PlayerScreenTest.showsPlayerSurfaceAndMediaName` **asserts** `sample.mp4` (`app/src/test/java/com/mediaflow/app/ui/PlayerScreenTest.kt:45–51`), locking the stem fallback.

---

## 3. Metadata sources (title, artist, album, artwork, duration)

| Field | DownloadItem | MediaStore gallery | Writers (embed) | Playback (player) |
|---|---|---|---|---|
| **title** | `fileName` / `nameWithoutExtension` / Media3 `metadata.fileName` | `DISPLAY_NAME` only | `©nam` / TIT2 from stem or Space title | Space → `service.title` → URI stem. **Never read back.** |
| **artist** | not stored | not queried | Space host / omitted for non-Space | `space.host` or `service.artistOrHost` (only if VM passed it). Local files: `R.string.player_media_audio`. |
| **album** | not stored | not queried | `"X Spaces"` for Spaces; else omitted | not shown |
| **artwork** | `ThumbnailPersister` sidecar or remote URL | always `null` | **not written** | Coil on sidecar/avatar/HTTP; else icon |
| **duration** | `durationSeconds` from validation / request | **not queried** (`DURATION` missing from projection) | n/a | mpv `duration` after `FILE_LOADED`; until then `saved?.totalDurationMs ?: 0L` |

`PlayerUiState.subtitle` is **never assigned** in the combine (`PlayerViewModel.kt:125–139`); screen rebuilds subtitle from space / `artistOrHost` / generic “AUDIO”.

`PlayerMetadataSection` duplicates `AudioPlayerView` title (`PlayerScreen.kt:197–230`).

Duration until engine load:

```262:264:domain/src/main/kotlin/com/mediaflow/domain/player/PlayerService.kt
                    playbackState = EnginePlaybackState.PREPARING,
                    currentPositionMs = startPos,
                    durationMs = saved?.totalDurationMs ?: 0L,
```

`PlayerTimeline` **ignores all tap/drag** while `durationMs == 0` (`PlayerTimeline.kt:54–57`, `118`, `138`, `146`).

There is no `MediaExtractor`/`MediaMetadataRetriever` metadata **read** path. `MediaExtractor` is mux/validate only (`MediaFileValidator`, `MediaTrackMuxer`).

---

## 4. Seek / scrub

### Can engine ticks overwrite the slider while dragging?

**Partially isolated locally, broken in the VM/service contract.**

`PlayerTimeline` keeps `isDragging` + `dragProgressFraction` and uses those while dragging (`PlayerTimeline.kt:51–64`). Engine `time-pos` will **not** move the canvas **until** `isDragging` flips false.

`PlayerUiState` was meant to freeze position:

```61:62:app/src/main/java/com/mediaflow/app/ui/player/PlayerUiState.kt
    val currentPositionMs: Long
        get() = if (isScrubbing) scrubPositionMs else serviceState.currentPositionMs
```

But:

1. `PlayerTimeline.onScrubbingChanged` is `(Boolean) -> Unit` — **never sends the scrub position**.
2. `PlayerScreen` calls `viewModel.setScrubbing(scrubbing)` with default `positionMs = 0L`.
3. `scrubPositionMs` is **not** in the `combine` inputs; only `.value` is snapshotted (`PlayerViewModel.kt:97–134`). Drag updates would not recompose even if position were set.
4. `PlayerService` copies **every** engine tick into UI state with no scrub lock:

```99:104:domain/src/main/kotlin/com/mediaflow/domain/player/PlayerService.kt
            engine.state.collect { engineState ->
                _uiState.value = _uiState.value.copy(
                    playbackState = engineState.playbackState,
                    currentPositionMs = engineState.currentPositionMs,
                    durationMs = engineState.durationMs,
```

On finger-up, local `isDragging` becomes false **immediately**, so the slider jumps to `serviceState.currentPositionMs` (often stale or 0) before mpv seeks.

### Does tap/drag seek to `fraction * duration`?

Yes, locally:

```126:129:app/src/main/java/com/mediaflow/app/ui/player/components/PlayerTimeline.kt
                                    val targetMs = (dragProgressFraction * effectiveDuration).toLong()
                                    onSeekTo(targetMs)
                                    isDragging = false
                                    onScrubbingChanged(false)
```

(same formula on drag end, lines 147–151).

Then the VM **seeks again to 0**:

```311:318:app/src/main/java/com/mediaflow/app/ui/player/PlayerViewModel.kt
    fun setScrubbing(scrubbing: Boolean, positionMs: Long = 0L) {
        isScrubbing.value = scrubbing
        if (scrubbing) {
            scrubPositionMs.value = positionMs
            cancelHideControls()
        } else {
            seekTo(positionMs)
```

```232:239:app/src/main/java/com/mediaflow/app/ui/player/PlayerScreen.kt
                    PlayerTimeline(
                        currentPositionMs = uiState.currentPositionMs,
                        durationMs = uiState.durationMs,
                        onSeekTo = viewModel::seekTo,
                        onScrubbingChanged = { scrubbing ->
                            viewModel.setScrubbing(scrubbing)
                        },
```

Order on tap/drag end: `seekTo(targetMs)` then `setScrubbing(false)` → `seekTo(0)`. `PlayerService.seekTo` updates engine **and** UI, last write wins:

```400:406:domain/src/main/kotlin/com/mediaflow/domain/player/PlayerService.kt
    fun seekTo(positionMs: Long) {
        if (isReleased) return
        engine.seekTo(positionMs)
        _uiState.value = _uiState.value.copy(currentPositionMs = positionMs)
```

`onDragCancel` seeks to 0 **without** sending the drag position (`PlayerTimeline.kt:153–156`).

Two `pointerInput`s (press + drag) on the same box (`PlayerTimeline.kt:115–165`) can both fire; parent `playerGestures` (`PlayerScreen.kt:129–137`) also owns taps on the same screen.

`bufferedMs` is never passed from `PlayerScreen` (always 0). `PlayerControls` (video overlay) duplicates another `PlayerTimeline` (`PlayerControls.kt:261–267`) with the same `(Boolean)` scrub API.

---

## 5. Play / Pause vs `EnginePlaybackState`

```7:14:domain/src/main/kotlin/com/mediaflow/domain/player/PlaybackEngine.kt
enum class EnginePlaybackState {
    IDLE, PREPARING, PLAYING, PAUSED, ENDED, ERROR,
}
```

There is **no BUFFERING**. UI maps buffering to PREPARING only:

```37:50:app/src/main/java/com/mediaflow/app/ui/player/PlayerUiState.kt
    val isPlaying: Boolean
        get() = serviceState.isPlaying   // playbackState == PLAYING
    // ...
    val isPreparing: Boolean
        get() = serviceState.playbackState == EnginePlaybackState.PREPARING
    val isBuffering: Boolean
        get() = serviceState.playbackState == EnginePlaybackState.PREPARING
```

`togglePlayPause` is a boolean on `isPlaying`, not a state machine:

```260:267:app/src/main/java/com/mediaflow/app/ui/player/PlayerViewModel.kt
    fun togglePlayPause() {
        showControlsTemporarily()
        if (uiState.value.serviceState.isPlaying) {
            playerService.pause()
        } else {
            playerService.play()
        }
    }
```

| Engine state | Audio `PlaybackControls` | Overlay `PlayPauseButton` | `togglePlayPause` |
|---|---|---|---|
| PLAYING | Pause | Pause | `pause()` |
| PAUSED / PREPARING / IDLE / ERROR | Play | Play | `play()` |
| ENDED | **Play** (not Replay) | Replay (`PlayPauseButton.kt:77–85`) | `play()` — **not** `restartFromBeginning()` |

Audio screen uses `PlaybackControls` (`PlayerScreen.kt:244–254`), which only checks `playbackState == PLAYING` (`PlaybackControls.kt:82–104`). ENDED looks like paused.

mpv `keep-open=yes` (`MpvConfig.kt:23`). After EOF, `play()` only `pause=false` (`MpvPlaybackEngine.kt:235–239`). Unpausing at EOF typically **does not restart**. Audio has no Restart control (`PlayerControls` restart is unused on this layout). User must scrub (which currently seeks to 0 — accidentally “works” as restart).

`FILE_LOADED` forces PLAYING unless current state is already PAUSED — but load() sets PREPARING, so `isPaused` is false even when `autoPlay=false`:

```117:122:data/src/main/kotlin/com/mediaflow/data/player/MpvPlaybackEngine.kt
                    _state.value = _state.value.copy(
                        durationMs = durMs,
                        playbackState = if (_state.value.isPaused) EnginePlaybackState.PAUSED else EnginePlaybackState.PLAYING,
```

Mini-player and notification also treat non-PLAYING as Play (`MiniPlayer.kt:133–134`). Notification ACTION_PLAY without extras calls `playerService.play()` (`MediaPlaybackService.kt:192–195`) — same ENDED hole.

Rapid play/pause: each tap reads `uiState.value` (combine snapshot) and hits mpv `pause` flag. `pause` property observer emits start/pause events (`MpvPlaybackEngine.kt:48–57`) independently of `FILE_LOADED`. No debounce; PREPARING + play() is easy to desync.

---

## 6. Recomposition, long titles, background/resume, consecutive seeks, rapid play/pause

**Recomposition.** `combine` includes `playerService.uiState`, which updates on every `time-pos`. The full `PlayerScreen` column (artwork Coil request, duplicated titles, timeline, Crossfade play button) recomposes at engine tick rate. `MediaArtwork` builds a new `ImageRequest` every time (`MediaArtwork.kt:116–119`). `AudioPlayerView.isPlaying` is `@Suppress("UNUSED_PARAMETER")` — no playing visual, still recomposed.

**Long titles.** `AudioPlayerView` title `maxLines = 2` (`AudioPlayerView.kt:109–117`) **and** `PlayerMetadataSection` `maxLines = 2` (`PlayerMetadataSection.kt:44–51`). Same string twice. No marquee. Header context is a separate third line (`playbackContext ?: "Reproduciendo"`).

**Background / resume.** `PlayerSessionHolder` + early-return if same `mediaId` and not IDLE (`PlayerViewModel.kt:217–220`) avoids reload **inside the VM**. Then `MediaPlaybackService.start` is skipped too (return before start). Re-entering the player for a **new** session still double-`openMedia`s (VM + service). Service `openMedia` does not no-op on the same URI (`PlayerService.kt:196–270`) — it reloads and recomputes resume position.

Progress resume uses `mediaId == mediaUri` (`PlayerService.kt:231–239`). If library plays `content://…/20567` and downloads used a different id, resume keys diverge.

**Consecutive seeks.** Timeline end = seek(target) + seek(0). `seekTo` also `saveCurrentProgressNow` on every call (`PlayerService.kt:404–406`). mpv seek is `absolute` seconds (`MpvPlaybackEngine.kt:257–264`). Next `time-pos` overwrites UI; with isScrubbing already false, the thumb jumps.

**Rapid play/pause.** See §5. Mini-player on the tab bar calls `playerService.play/pause` directly (`AppNavigation.kt:123–128`), bypassing the VM, while the full player uses the VM — two controllers on the same engine.

**Audio vs surface flash.** `isAudioOnly` defaults **false** (`PlayerServiceState` / `EngineState`). `load()` does not reset it (`MpvPlaybackEngine.kt:187–192`). Until `track-list` / `FILE_LOADED`, `PlayerScreen` may compose `PlayerSurface` instead of `AudioPlayerView` (`PlayerScreen.kt:193–211`).

---

## 7. Existing tests — what they miss

### `PlayerScreenTest` (`app/src/test/java/com/mediaflow/app/ui/PlayerScreenTest.kt`)

Covers: URI stem title `sample.mp4`; back; isolated share; isolated `PlayPauseButton` click; timeline **labels** 01:05 / 05:00; Space copy in `AudioPlayerView`; seek-feedback pill; live overlays.

Misses:

- Seek gesture / `fraction * duration` / **seek-to-0 on scrub end**
- Artwork: embedded, sidecar, placeholder, `preferredArtworkUrl` in player
- Title: MediaStore id, ID3 vs stem, service overwrite
- Play/pause vs PREPARING / ENDED (audio uses `PlaybackControls`, not `PlayPauseButton`)
- `PlayerScreen` + real `PlayerViewModel` open path
- Duplicate title (`AudioPlayerView` + `PlayerMetadataSection`)
- Duration 0 disables scrub
- Recomposition / Coil request churn

### `PlayerViewModelBackgroundTest` (`app/src/test/java/com/mediaflow/app/ui/player/PlayerViewModelBackgroundTest.kt`)

Covers: live session reuse (no second `load`); auto-download toggle; live `PlaybackFinished` overlay; replay download dedup; glitch stays `ActiveLive`.

Misses:

- Local audio `open(contentUri)` title/artwork
- `setScrubbing(false)` → `seekTo(0)`
- `togglePlayPause` on ENDED
- Double `openMedia` from `MediaPlaybackService.start`
- Download timeout 250 ms
- Gallery vs download metadata
- Engine tick vs `isScrubbing`
- Rapid play/pause / consecutive seeks

`ArtworkUriTest` only unit-tests URL gating; it does **not** mount `AudioPlayerView` / Coil / embedded covers.

No test observes mpv `media-title` or `MediaMetadataRetriever`.

---

## Must fix (Core)

1. **Stop seeking to 0 on scrub end.** `PlayerScreen` must pass the release position into `setScrubbing(false, targetMs)`; `PlayerTimeline` must report that position; do not call both `onSeekTo(target)` and `setScrubbing(false)` → `seekTo(0)`. `onDragCancel` must restore, not zero. (`PlayerScreen.kt:232–239`, `PlayerViewModel.kt:311–318`, `PlayerTimeline.kt:126–156`)
2. **Hold UI position while scrubbing.** Put `scrubPositionMs` in the `combine`; keep `isScrubbing` true until the engine reports the target (or a short settle). Stop `PlayerService` engine ticks from overwriting the thumb during drag. (`PlayerViewModel.kt:97–134`, `PlayerUiState.kt:61–62`, `PlayerService.kt:99–104`)
3. **Do not double-`openMedia`.** `MediaPlaybackService.start` must not reload the URI the VM just opened, and must not pass `title=null` / `artworkUrl=null` over good metadata. (`PlayerViewModel.kt:234–256`, `MediaPlaybackService.kt:163–190`, `PlayerService.kt:196–269`)
4. **Resolve title from real metadata, never MediaStore `_ID` / URI stem as the primary label.** Read ID3/`©nam` (or mpv `media-title`) and MediaStore `TITLE`/`ARTIST`/`ALBUM`/`DURATION`/`ALBUM_ID`. Pass title through nav or gallery lookup on `open`. Stop preferring file stem over tags. (`PlayerViewModel.kt:122–127, 205–237`, `PlayerService.kt:259`, `MediaStoreGalleryRepository.kt:85–155`)
5. **Load artwork: sidecar thumb → embedded cover (`covr`/APIC/mpv albumart/MediaStore albumart) → placeholder.** Write covers at download time (`MediaMetadata` has no art field; writers skip `covr`/APIC). Player must not reject the only available art. (`MediaArtwork.kt:32–71`, `ThumbnailPersister.kt`, `Mp4MetadataEditor.kt:180–188`, `Id3MetadataEditor.kt:54–59`, `PlayerViewModel.kt:239–242`)
6. **Wire play/pause to the engine state machine.** `ENDED` → `restartFromBeginning()` (or seek 0 + play). `PREPARING` should not look like paused-ready. Audio `PlaybackControls` must match overlay Replay. (`PlayerViewModel.kt:260–267`, `PlaybackControls.kt:82–104`, `MpvPlaybackEngine.kt:235–239, 117–122`)
7. **Duration before first `FILE_LOADED`.** Query MediaStore `DURATION` / download `durationSeconds` / retriever so the slider is enabled and `fraction * duration` is defined. (`PlayerService.kt:262–264`, `MediaStoreGalleryRepository.kt:85–91`, `PlayerTimeline.kt:54–57, 118`)
8. **Artist/album for local files.** Stop substituting the generic “AUDIO” string when tags or MediaStore artist exist. (`PlayerScreen.kt:216–226`, `PlayerUiState.subtitle` never set)

## Must show (UIUX)

1. **Cover art** on `AudioPlayerView` (220 dp) and mini-player: real image when the file or sidecar has one; the existing Audiotrack/`GraphicEq` placeholder only when there is **no** art — never a blank/failed Coil box.
2. **Human title** (ID3 / Space / filename **without** MediaStore numeric id). One title, not the same string in `AudioPlayerView` and `PlayerMetadataSection`.
3. **Artist (and album when known)** under the title; Space host stays host — not `"AUDIO"` / filename as artist.
4. **Duration and position** as soon as the file is open (`mm:ss` / `hh:mm:ss`); slider must move to the tapped fraction and **stay** there while dragging (tooltip already exists in `PlayerTimeline`).
5. **Play vs Pause vs Ended:** Pause only while `PLAYING`; Replay/restart after `ENDED`; buffering indicator for real cache waits, not only `PREPARING`.
6. **Long titles:** single hierarchy, ellipsis or marquee, no duplicated two-line blocks eating the art.
7. **Notification / lockscreen** title + large icon from the same artwork/title as the player (call `updateMetadata`; load `file://` thumbs, not HTTP-only).
