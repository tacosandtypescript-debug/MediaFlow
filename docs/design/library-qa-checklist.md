# Library QA checklist — Video grid, sort, multi-select, thumbnails

Read-only snapshot of **current** Library video UX. No features implemented here. Paths are repo-absolute under `/home/isaac/MediaFlow`.

Parent will resume Library-QA to add tests against these gaps. Do not treat this file as a spec to implement until that pass.

---

## Surface under test

| Piece | Path | Current behavior |
|---|---|---|
| Screen | `app/src/main/java/com/mediaflow/app/ui/library/LibraryScreen.kt` | Chips + recents bar + filter bodies. Search is title/`fileName` only. |
| Video body | `app/src/main/java/com/mediaflow/app/ui/library/components/VideoLibraryView.kt` | Always a **2-column** `LazyVerticalGrid`. Tile art is **1:1**. |
| Recents chrome | `.../library/components/LibraryRecentsBar.kt` | Label “Recién añadido” + unused `SwapVert`. Grid toggle **only** on filter ALL. |
| State | `LibraryUiState.kt` / `LibraryViewModel.kt` | Filter, lists, favorites, playlists, progress, player id. **No sort mode. No selection set.** |
| Art | `app/src/main/java/com/mediaflow/app/ui/common/media/MediaArtwork.kt` | Coil only if `isLoadableArtworkUrl`. Crop fill. Placeholder `Videocam` for video. |
| Overlay | `LibraryViewModel.overlayThumbnails` | Copies loadable download `thumbnailUri` onto gallery rows by id / `localUri` / `sourceUrl`. |
| Persist | `data/src/main/kotlin/com/mediaflow/data/download/ThumbnailPersister.kt` | Sidecar/HTTP → `filesDir/thumbs/{id}.jpg|png|webp`. Emits `Uri.fromFile` (`file:///`). |
| Gallery rows | `data/.../MediaStoreGalleryRepository.kt` | MediaStore audio/video owned by MediaFlow. **`thumbnailUri` never set.** |
| Existing tests | `app/src/test/java/com/mediaflow/app/ui/LibraryScreenTest.kt` | Header + chips + playlists tab. **No `VideoLibraryView`.** `ArtworkUriTest` covers URI gates + overlay. |

Video tab wiring:

```196:203:app/src/main/java/com/mediaflow/app/ui/library/LibraryScreen.kt
            LibraryFilter.VIDEO -> VideoLibraryView(
                items = visibleVideo,
                playingMediaId = uiState.playingMediaId,
                favoriteUris = uiState.favoriteUris,
                onPlayItem = { item -> onOpenItem(item) },
                onToggleFavorite = { uri -> viewModel.toggleFavorite(uri) },
                onDeleteMedia = { item -> itemToDelete = item },
            )
```

ALL + grid reuses the **same** `VideoLibraryView` for mixed audio+video (`visibleAll`), so 1:1 tiles apply to audio in that mode too.

---

## 1. VideoLibraryView — 1:1 grid

**What is there**

```61:91:app/src/main/java/com/mediaflow/app/ui/library/components/VideoLibraryView.kt
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            ...
        ) {
            items(items, key = { it.id }) { item ->
                ...
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f),
                        ) {
                            MediaArtwork(
                                artworkUrl = preferredArtworkUrl(item.thumbnailUri),
                                mediaType = MediaType.VIDEO,
                                ...
                                fillMax = true,
```

`MediaArtwork` uses `ContentScale.Crop` (`MediaArtwork.kt` ~121). Widescreen posters (16:9 / 4:3) are **center-cropped into squares**. Contrast: `GalleryScreen` grid uses `aspectRatio(1.25f)` and `ContentResolver.loadThumbnail` frames, not Coil on `thumbnailUri`.

**Gaps**

- [ ] **Aspect.** No 16:9 / 4:3 / 1.25 tile. 1:1 is hardcoded; no adaptive cells (`GridCells.Adaptive`).
- [ ] **ALL-grid reuse.** Audio items in ALL+grid get `MediaType.VIDEO` artwork + “Video” subtitle (`R.string.player_media_video`) regardless of `item.mediaType`.
- [ ] **No list mode on VIDEO.** `showGridToggle = selectedFilter == ALL` only. Video tab always grid; audio tab always list. Recents bar still renders on VIDEO (sort affordance without sort).
- [ ] **Play is open-only.** `onPlayItem` → `onOpenItem`; does **not** `playQueue(visibleVideo, index)` unlike AUDIO / ALL list / Favorites. Queue/next from a video grid tap is undefined.
- [ ] **Overflow stubs.** `onAddToPlaylist = { }` no-op. `onAddToQueue = null` (menu item hidden). AUDIO/ALL pass real callbacks + `AddToPlaylistSheet`.
- [ ] **No progress / resume.** Audio rows use `progressMap`. Video tiles ignore it. Duration badge only if `durationSeconds != null` (gallery MediaStore rows often omit duration).
- [ ] **Now-playing.** Title tint if `playingMediaId == uri`. No overlay, equalizer, or playing ring on the square.
- [ ] **Space / avatar.** `preferredArtworkUrl(item.thumbnailUri)` — no `space?.host?.avatarUrl` fallback (audio/all/favorites do). Video Spaces would lose host art.
- [ ] **A11y / tests.** Grid has `testTag("video_library_grid")`. Individual tiles have **no** test tag / content description. Tile click target is the whole `Column` (art + title + overflow). Overflow `IconButton` competes with parent `clickable`.
- [ ] **Empty.** `EmptyLibraryState` for zero items; no loading / error from `uiState.errorMessage`.
- [ ] **Density.** Title `maxLines = 2` + overflow row under a square on a 2-col phone grid; long titles collide with the 3-dot menu.

**Later tests (do not implement UI now)**

- Compose: VIDEO chip → `video_library_grid` when fixtures exist; empty copy when none.
- Tile `aspectRatio` / semantics if/when tags are added.
- ALL + grid toggle (`library_view_toggle`) mounts `VideoLibraryView` with mixed types (document current VIDEO subtitle bug).

---

## 2. Missing sort

**What is there**

- Recents bar icon `Icons.Outlined.SwapVert` is **not clickable**. No dropdown. No `onClick`. Copy is always `R.string.library_recents` (“Recién añadido”).
- `LibraryUiState` has no `sortOrder`.
- ALL list/grid: `visibleAll` is `allItems.sortedByDescending { it.createdAt }` then search-filtered (`LibraryScreen.kt` ~69–73).
- AUDIO / VIDEO / Favorites: **filter by query only**. Order = `LibraryViewModel` list order.
- Gallery query: `DATE_ADDED DESC` (`MediaStoreGalleryRepository`). Downloads observe: `sortedByDescending { createdAt }`. Overlay **preserves gallery order**, does not re-sort mixed overlay.

**Gaps**

- [ ] **No user sort.** Cannot choose Recents / Title / Duration / Type. SwapVert is decoration (false control).
- [ ] **VIDEO ≠ ALL.** Video tab is not sorted in the screen layer. If overlay or gallery order changes, VIDEO and ALL-grid of the same files can diverge.
- [ ] **Search does not re-rank.** Title/`fileName` contains; no recency boost, no uploader.
- [ ] **No persistence** of last sort or list/grid (`isGrid` is `remember`, lost on process death; VIDEO has no grid flag at all).
- [ ] **Playlists / Favorites** have their own order (playlist `mediaUris`, favorites filter) — out of Video grid scope but same recents chrome is hidden there.

**Later tests**

- Assert Recents label is shown on ALL / AUDIO / VIDEO.
- Assert SwapVert has no click action / no sort menu nodes.
- Unit: `visibleVideo` order equals VM `videoItems` order (no extra sort) vs `visibleAll` `createdAt` DESC.

---

## 3. Missing multi-select

**What is there**

- Per-item overflow: play, add to playlist (no-op on video), favorite, share, delete (one `DeleteMediaDialog`).
- `LibraryUiState` has no `selectedIds` / `selectionMode`.
- Tiles: `Modifier.clickable { onPlayItem(item) }` only. No `combinedClickable`, long-press, or checkbox.
- Gallery (separate screen) is **single** `selectedId` + play/delete — not library multi-select. Downloads selection tests are format-id, not library tiles.

**Gaps**

- [ ] Long-press / select-all / range select: **absent**.
- [ ] Bulk delete, bulk favorite, bulk add-to-playlist, bulk share, bulk add-to-queue: **absent**.
- [ ] No selection app bar / count / clear.
- [ ] No CAB vs playback conflict (tap always plays).
- [ ] Share is overflow-only, one URI.

**Later tests**

- Video tile click invokes play, not selection.
- No `selected` semantics / checkboxes on `video_library_grid`.
- After a future implementation: selection mode, count, bulk delete confirmation.

---

## 4. Thumbnails — `file:` image vs video files

### Intended gate (already coded)

```32:71:app/src/main/java/com/mediaflow/app/ui/common/media/MediaArtwork.kt
private val MEDIA_FILE_EXTENSIONS = setOf(
    "mp4", "m4a", "mp3", "webm", "mkv", "mov", "aac", "opus", "ogg", "wav", "m4v", "m3u8", "m3u",
)
fun isLoadableArtworkUrl(url: String?): Boolean { ... }
fun preferredArtworkUrl(...): String? =
    listOf(thumbnailUri, spaceAvatarUrl).firstOrNull(::isLoadableArtworkUrl)
```

| URI | Loadable? | Video tile result |
|---|---|---|
| `file:///…/thumbs/{id}.jpg` (and png/webp/gif) | yes | Coil `File(path)`, crop into 1:1 |
| `file:/…/thumbs/{id}.jpg` (`File.toURI()` style, **two** slashes) | yes (`startsWith("file:")` + image ext) | `coilArtworkModel` → `Uri.parse.path` → `File` |
| `https://…/hqdefault.jpg` | yes | Coil HTTP |
| `content://…/images/…` | yes if image path/ext | Coil |
| `file:///…/clip.mp4` / `.mkv` / `.webm` / `.m4v` | **no** | Placeholder `Videocam`, Coil never called |
| `content://media/external/video/media/{id}` | **no** (`/video` is not `/images`; ext empty) | Placeholder. **Does not** use `loadThumbnail` unlike Gallery. |
| `null` / blank | no | Placeholder |

`ThumbnailPersister.fileUri` uses `Uri.fromFile` (`file:///…`) **on purpose**:

```158:159:data/src/main/kotlin/com/mediaflow/data/download/ThumbnailPersister.kt
    /** Coil and isLoadableArtworkUrl need file:/// (three slashes), not File.toURI()'s file:/. */
    private fun fileUri(file: File): String = Uri.fromFile(file).toString()
```

Older identity doc still suggests `File(thumbs, "$id.jpg").toURI()` (`docs/design/mediaflow-ui-identity.md` ~311) — **do not** regress to that in persist. UI already accepts both image forms.

### Remaining thumbnail gaps for the video grid

- [ ] **Gallery videos have no `thumbnailUri`.** Overlay only fills when a completed download shares id / `localUri` / `sourceUrl` **and** that download’s thumb passes `isLoadableArtworkUrl`. Mismatch → empty square forever (no MediaStore frame).
- [ ] **Never pass `localUri` of the video as art.** `VideoLibraryView` is correct (`preferredArtworkUrl(item.thumbnailUri)` only). Regression: `thumbnailUri ?: localUri` would feed `.mp4` to Coil or still fail the gate and look “broken empty”. Tests already: `ArtworkUriTest.rejectsLocalMediaFiles`, `prefersThumbnailOverAvatarAndIgnoresMediaUri`.
- [ ] **Sidecar next to media.** yt-dlp harvest moves jpg/webp into `thumbs/` and **deletes** the sidecar so finish() cannot treat the image as the media file (`ThumbnailPersister.harvestNearby`, `YtDlpRuntimeTest.findOutputFile ignores thumbnail sidecars`). Grid must show `thumbs/` URI, not the `.mp4`.
- [ ] **`file:` vs `file:///`.** Persister emits three slashes. `isLoadableArtworkUrl` + Coil path both handle two-slash image URIs. Video extensions rejected in **both** slash forms. Coil must receive `File`, not a raw `file:/` string, for local images (`coilArtworkModel`).
- [ ] **Crop vs letterbox.** Loadable 16:9 JPEG is cropped square; no blur-fill / `ContentScale.Fit`.
- [ ] **HTTP leftover.** Overlay uses loadable URL even if still `https://` (not yet persisted). Offline after process death needs local `file:///thumbs`. Store round-trip is covered in `PlatformDownloadStoreTest`.
- [ ] **Notification/player** HTTP-only art is player-QA (`player-qa-findings.md`); library grid is Coil+File. Do not conflate in library tests.

**Later tests**

- Keep/extend `ArtworkUriTest`: mp4/m3u8 rejected; jpg `file:///` and `file:/` accepted; overlay copies thumb onto gallery video row.
- Compose (when fixtures exist): tile with `thumbnailUri=file:///…/thumbs/x.jpg` vs `thumbnailUri=file:///…/clip.mp4` vs `null` — only the jpeg hits Coil; others placeholder. **Do not** assert Coil loads a video file.
- `ThumbnailPersisterTest`: URI starts with `file:` and path contains `/thumbs/`; sidecar deleted; media `.mp4` remains.

---

## 5. Checklist for the next Library-QA test pass

Write tests only unless parent asks to implement. Prefer unit + existing Robolectric Compose style (`LibraryScreenTest`, `ArtworkUriTest`).

### Must cover (current product)

1. [ ] Library header + VIDEO chip (`library_filter_video`) still render.
2. [ ] VIDEO empty state strings (`library_video_empty_title` / `_subtitle`) when no videos.
3. [ ] `video_library_grid` when video items exist (needs injectable VM or a composable-level `VideoLibraryView` test).
4. [ ] `isLoadableArtworkUrl` / `preferredArtworkUrl` matrix: image `file:///`, image `file:/`, video `file:///…mp4`, `content://…/video/…`, https jpg, https mp4/m3u8.
5. [ ] `overlayThumbnails` copies persistable thumb onto MediaStore video; does not copy `.mp4` `localUri` into `thumbnailUri`.
6. [ ] Recents bar visible on VIDEO; **no** `library_view_toggle` on VIDEO; toggle present on ALL.
7. [ ] Video overflow “Añadir a playlist” currently no-ops (document; fail closed if someone wires a crash).
8. [ ] Video tap path is `onOpenItem` only (no `playQueue` on VIDEO).

### Must not assert as done (features missing)

9. [ ] User-facing sort menu / change of order from SwapVert.
10. [ ] Multi-select / long-press / bulk actions.
11. [ ] Non-1:1 video tiles / MediaStore `loadThumbnail` in Library.
12. [ ] Queue / add-to-playlist from `VideoLibraryView` matching AUDIO.

### Implementation follow-ups (other agents; not this pass)

- Sort: make SwapVert a real menu; apply same comparator to AUDIO/VIDEO/ALL; persist.
- Multi-select: selection mode on grid; bulk delete/playlist; do not play on tap while selecting.
- Grid: 16:9 (or 1.25) for video-only tab; do not force `MediaType.VIDEO` on ALL-grid audio; list mode on VIDEO.
- Thumbs: MediaStore `loadThumbnail` fallback when `thumbnailUri` missing; keep rejecting media files; persist `file:///` not `File.toURI()`.
- Wire `onAddToPlaylist` / `playQueue` on video like audio.

---

## File index

- `/home/isaac/MediaFlow/app/src/main/java/com/mediaflow/app/ui/library/components/VideoLibraryView.kt`
- `/home/isaac/MediaFlow/app/src/main/java/com/mediaflow/app/ui/library/components/LibraryRecentsBar.kt`
- `/home/isaac/MediaFlow/app/src/main/java/com/mediaflow/app/ui/library/LibraryScreen.kt`
- `/home/isaac/MediaFlow/app/src/main/java/com/mediaflow/app/ui/library/LibraryViewModel.kt`
- `/home/isaac/MediaFlow/app/src/main/java/com/mediaflow/app/ui/common/media/MediaArtwork.kt`
- `/home/isaac/MediaFlow/data/src/main/kotlin/com/mediaflow/data/download/ThumbnailPersister.kt`
- `/home/isaac/MediaFlow/data/src/main/kotlin/com/mediaflow/data/repository/MediaStoreGalleryRepository.kt`
- `/home/isaac/MediaFlow/app/src/test/java/com/mediaflow/app/ui/common/ArtworkUriTest.kt`
- `/home/isaac/MediaFlow/app/src/test/java/com/mediaflow/app/ui/LibraryScreenTest.kt`
- `/home/isaac/MediaFlow/app/src/main/java/com/mediaflow/app/ui/gallery/GalleryScreen.kt` (contrast: 1.25 + `loadThumbnail`, not library)
