# Player — Spotify Now Playing, MediaFlow purple

Not a Spotify clone. No `#1DB954`. No Spotify wordmark. Primary remains MediaFlow purple (`#7C3AED` / `#8B5CF6`).

This spec is the **audio** full-screen Now Playing only. Live X Spaces (`LivePlayerView`) and video (`PlayerSurface` + overlay controls) stay on their own layouts.

## Hierarchy (top → bottom, dark full-screen)

1. **Top bar** — back (`Volver`) left, optional share right. 48 dp targets. No large header, no centered “Reproduciendo” pill.
2. **Album cover** — the protagonist. Centered square, **70–80% of width**, corner radius **8–12 dp**, elevation/shadow. Placeholder icon if there is no art.
3. **Title block** — ExtraBold, 1–2 lines ellipsis, `onSurface`. Under it: artist then album as muted `bodySmall`. Favorite (heart) on the **right**, 48 dp (`player_favorite`).
4. **Seek** — 4 dp track, round thumb, elapsed **left**, duration **right**, tabular/monospace. Times sit close under the bar (`player_seek` / `player_timeline`).
5. **Primary transport** — centered row: −10 s, Play/Pause **64–72 dp** filled circle, +10 s. Generous spacing. Tags: `player_skip_back`, `play_pause`, `player_skip_forward`.
6. **Secondary row** — smaller, discrete: speed, queue, share, delete (and add-to-playlist) if already wired. **No shuffle** (not wired).
7. **States**
   - Playing / Paused: icon on the play button.
   - Loading (`PREPARING`): spinner on the play button (no center overlay).
   - Error: existing error card copy (`player_error_title` / subtitle / `Volver`).

## Tokens

- Background: `colorScheme.background` (Ink). Optional top wash of `primary` ~28% alpha — never Spotify green.
- Played seek + play button fill: `primary`. Thumb: `primary`. Track: `customColors.progressTrack`.
- Title: `onSurface` ExtraBold. Artist/album: `onSurfaceVariant` `bodySmall`.
- Times: `FontFamily.Monospace`.

## Wiring

- Seek math stays in the ViewModel. UI only calls existing `onSeekTo` / `onScrubbingChanged`.
- Spanish strings. Keep test tags (`player_header_back_btn`, `player_share_btn`, `dominant_play_pause_btn`, …) and add the Now Playing tags above.
- No window animations. No new playback actions.

## Composition

`PlayerScreen` branches: live → `LivePlayerView`; audio → `AudioNowPlaying`; video → existing surface + controls. Cover lives in `AudioPlayerView` (`heroCover` for Now Playing; compact cover + Space details for live / tests).
