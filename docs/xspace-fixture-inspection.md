# X Space fixture inspection (guest HTML + public endpoints)

Inspected 2026-08-30 from this worktree. **No GraphQL field was invented.** Guest pages often require login; HTTP 200 HTML still leaked Space cards. Direct `/i/spaces/{id}` returned 307 → `/peek` and the peek body was a generic error in this fetch environment.

Named fixtures:

| Role | Status tweet | Space id / URL |
|---|---|---|
| LIVE | https://x.com/barvabe/status/2094108808637305253?s=46 | `1rGmqplYpggGy` — https://x.com/i/spaces/1rGmqplYpggGy |
| ENDED/REPLAY | https://x.com/respaldodeandre/status/2094078407109710217?s=46 | `1NGarowkqQlJj` — https://x.com/i/spaces/1NGarowkqQlJj |

HTTP: both status URLs `200 text/html`. Space URLs `307` to `/peek`. Guest GraphQL was not captured in this pass (login wall / peek error). Classifications below use **only** strings visible on those pages plus known public API names already used by `XSpaceMetadataResolver` (`AudioSpaceById`, `live_video_stream/status`).

Legend: **AVAILABLE** observed on the fixture page. **UNAVAILABLE** not present. **APPROXIMATE** present but not a precise census. **REQUIRES_EXTRA_QUERY** needs GraphQL or `live_video_stream` (not in HTML). **DYNAMIC** changes while LIVE.

## LIVE fixture (`barvabe` / `1rGmqplYpggGy`)

Observed on the tweet page (still **Listen live** at inspect time; LIVE-only fields are not marked ended):

- Title: `Santo Rosario y Ángelus. 📿🙏🏻`
- Host: Bárbara V. `@barvabe`, Host badge, avatar
- CTA: **Listen live**
- Audience chip: **22 in this Space** (snapshot; avatars of a few people)
- Space URL / id from CTA
- Tweet time `5:03 PM · Aug 30, 2026`, public metrics (views/likes) — tweet, not Space GraphQL

Not on the page: duration, `Play recording`, `tuned in`, replay length, HLS URL, `media_key`, remote DVR window, seek bar, speaker diarization.

| Field | Class | Evidence |
|---|---|---|
| space id / URL | AVAILABLE | `/i/spaces/1rGmqplYpggGy` |
| title | AVAILABLE | card title |
| host name/handle/avatar/role | AVAILABLE | Host chip |
| state LIVE | AVAILABLE | Listen live |
| live listener count | DYNAMIC | “22 in this Space”; GraphQL name `total_live_listeners` not in HTML |
| participant avatars | APPROXIMATE | three faces, not a full list |
| co-hosts / speakers list | UNAVAILABLE | not listed beyond host |
| duration | UNAVAILABLE | no clock |
| ended_at / started_at | REQUIRES_EXTRA_QUERY | GraphQL `started_at` / `ended_at` |
| recordingAvailable | UNAVAILABLE | no Play recording |
| replayCount | UNAVAILABLE | no “tuned in” |
| HLS `audioStreamUrl` | REQUIRES_EXTRA_QUERY | `live_video_stream/status/{media_key}` |
| protocol HLS | REQUIRES_EXTRA_QUERY | CSP allows `*.video.pscp.tv`; URL not in HTML |
| remote DVR window | UNAVAILABLE | no DVR/playlist fields |
| live seek | UNAVAILABLE | must not claim seek without DVR |
| media_key | REQUIRES_EXTRA_QUERY | GraphQL only |
| speakerSegments | UNAVAILABLE | not delivered by X |

## ENDED / REPLAY fixture (`respaldodeandre` / `1NGarowkqQlJj`)

Observed:

- Card title: `RESACATULIA #343: MAÑANA TURBULENTA` (tweet body is a longer different title)
- Host: Relatos de André, Host
- CTA: **Play recording**
- **190 tuned in**
- Date **Aug 30**
- Duration **1:53:48** (formatted on card; seconds not raw)
- Space id `1NGarowkqQlJj`

| Field | Class | Evidence |
|---|---|---|
| space id / URL | AVAILABLE | `/i/spaces/1NGarowkqQlJj` |
| title | AVAILABLE | card (may differ from tweet text) |
| host | AVAILABLE | Host |
| state ENDED + replay | AVAILABLE | Play recording |
| duration | AVAILABLE | `1:53:48` (display; GraphQL uses started/ended) |
| historical audience | APPROXIMATE | “190 tuned in” — not live; likely `total_live_listeners` or similar, not a live census |
| replayCount | REQUIRES_EXTRA_QUERY / UNAVAILABLE on HTML | no separate replay-watch number |
| recordingAvailable | AVAILABLE | Play recording |
| HLS replay URL | REQUIRES_EXTRA_QUERY | same live_video_stream after end |
| seek on replay | AVAILABLE (capability) | VOD-like HLS once URL is fetched; not proven from HTML |
| remote DVR | UNAVAILABLE | ended replay is not live DVR |
| live listeners now | UNAVAILABLE | not live |

## Stream / capabilities contract

- Protocol after extra query: **HLS** (`.m3u8` on `*.video.pscp.tv`), never assumed from HTML alone.
- **Remote DVR:** not observed. `XSpaceStreamInfo.remoteDvrWindowSeconds` stays `null`. **LIVE seek is false** unless a window is later parsed from a playlist.
- **ENDED seek:** allowed only after HLS replay URL exists (`seekSupported` when ended + `.m3u8`).
- LIVE vs ENDED differ only as: listeners DYNAMIC vs APPROXIMATE; duration UNAVAILABLE vs AVAILABLE; seek UNAVAILABLE vs AVAILABLE (replay); recording UNAVAILABLE vs AVAILABLE.

## Profile without Space vs profile with LIVE Space

Guest HTML, no follow/push APIs:

**Profile with LIVE Space** (`https://x.com/barvabe`): header label **Spaces Host**; docked card **Listen live**, **N in this Space**, live title/host; may also show older **Play recording** cards.

**Profile without a LIVE Space** (`https://x.com/respaldodeandre` at inspect): no **Listen live** / **in this Space**. May still show an ended **Play recording** card and tweet linking the Space. Bio is ordinary; no live host chip in the same way.

**Not observed anywhere:** follow-user notifications, WebSocket live badges, or push payloads. Do not implement profile live detection via notification APIs.

## Mapper rule

If GraphQL omits `total_live_listeners`, `liveListenersCount` stays `0` and field availability is **UNAVAILABLE** — never a fabricated audience.
