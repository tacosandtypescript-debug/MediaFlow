# X Spaces LIVE — flujo real vs MediaFlow

Especificación para **Subagente 2**: player LIVE + auto-descarga de repetición al terminar.  
Análisis únicamente del código existente. **No inventar campos que X no entrega.**

Identidad visual: seguir `docs/design/mediaflow-ui-identity.md` (cobre, rojo LIVE `#F04455`, Compose modular, sin botones falsos).

---

## 0. Cómo entra un Space hoy (camino real)

```
URL (x.com/i/spaces/<id> o tweet /status/<id>)
  → YtDlpSourceResolver.analyze (fast-path X, sin yt-dlp si GraphQL responde)
      → XSpaceMetadataResolver.resolveFromUrl / resolve
          → guest/activate.json (token anónimo)
          → GraphQL AudioSpaceById
          → si falta audioStreamUrl y hay media_key:
                GET api.x.com/1.1/live_video_stream/status/{media_key}
          → si GraphQL falla: yt-dlp JSON (fallback)
      → XSpaceRepositoryImpl.saveSpace  (disco: mediaflow_spaces.json)
  → Home: XSpaceCard
      → "Escuchar en vivo" si state==LIVE && audioStreamUrl != null
      → AppNavigation navega a player/{Uri.encode(streamUrl)}
  → PlayerViewModel.open(hlsUrl)
      → getSpaceForMedia(hlsUrl)  (mapa in-memory: id / url / audioStreamUrl)
      → PlayerService.openMedia(..., isLive=true)  [libmpv, único motor]
      → MediaPlaybackService.start(..., spaceId, spaceUrl, autoDownload)
```

**No usado por la app:** `ResolveLiveSpaceUseCase`, `XLiveSpaceResolver`, `XLiveStreamClient`.  
Existen, tienen tests, **no están inyectados** en Home ni Player. Subagente 2 **no** debe crear un segundo camino de player. El HLS ya se abre con libmpv.

---

## 1. Inventario de campos

Leyenda: **X** = GraphQL AudioSpace / live_video_stream / tweet card. **yt-dlp** = extractor TwitterSpaces o JSON genérico. **cache** = `XSpaceStore` / `PendingLiveDownloadRepositoryImpl`. **UI** = se muestra. **Wiring** = hueco o error.

| Campo | Origen real | Persistido | UI | Notas / wiring |
|---|---|---|---|---|
| **URL** | Parser (`XUrlParser`) + URL original del usuario | `XSpace.url` | No en player LIVE; Home usa el input | Normalizada a `https://x.com/...`. Navegación LIVE usa **HLS**, no la URL de X. |
| **Space id** | Path `/i/spaces/{id}` o card tweet `audio_space_id` / `id` | `XSpace.id` clave de store | No visible en LIVE player | Clave canónica. **Debe ser dedup de descargas.** |
| **Título** | GQL `metadata.title`; fallback yt-dlp `title`; último `"X Space"` | sí | Home card, player, notificación, miniplayer | El fallback `"X Space"` es etiqueta, no un título de X. |
| **Host** | GQL `creator_results.result.legacy` (`name`, `screen_name`, `rest_id`, `profile_image_url_https`) | `XSpace.host` | Home, AudioPlayerView, notificación | Rol `HOST`. |
| **Co-hosts** | GQL `participants.admins` (distintos del creator) | `cohosts` | Home chips "Speakers"; player chips (máx. 4) | X llama `admins`; MediaFlow mapea a `COHOST`. |
| **Speakers** | GQL `participants.speakers` | `speakers` | Home + player (handles) | Lista **parcial** que X expone, no el histórico completo. |
| **Listeners (lista)** | GQL `participants.listeners` (muestra corta) | `participants` | Home: recuento `participants.size` | **No** es el censo real. No fabricar lista. |
| **Oyentes en vivo** | GQL `total_live_listeners` | `liveListenersCount` | Home badge LIVE; LivePlayerView chip si > 0 | Snapshot al analizar. **No hay polling** → se queda congelado. |
| **Reproducciones replay** | GQL `total_replay_watched` | `replayCount` | Home footer solo si no hay oyentes | 0 si X no lo manda. |
| **Avatar / “thumbnail”** | Avatar host GQL (se reemplaza `_normal` → `_400x400`); yt-dlp `thumbnail` solo fallback | avatar en host; **no hay campo thumbnail de Space** | Home HostAvatar; player `preferredArtworkUrl`; miniplayer `artworkUrl` | X **no** da portada de Space. Usar avatar o placeholder `GraphicEq`. Nunca `.m3u8`/`.m4a` a Coil. |
| **created_at** | GQL `created_at` (epoch ms) | `createdAtMs` | No | |
| **started_at** | GQL `started_at` (epoch ms) | `startedAtMs` | No en LIVE | Válido para “empezó a las…” **solo si** > 0. |
| **ended_at** | GQL `ended_at` (string numérica en respuestas reales) | `endedAtMs` | No | Parser: `optString.toLongOrNull`. |
| **Duración** | Solo ` (endedAt − startedAt) / 1000 ` si ambos existen; si no, yt-dlp `duration` | `durationSeconds` (0 por defecto) | Home `formattedDuration` (**también en LIVE** → `"--"`) | **NUNCA** inventar duración en LIVE. Ocultar en LIVE. Mostrar solo si > 0 **y** `ENDED`. |
| **LIVE vs ENDED** | GQL `metadata.state`: `Running`/`NotStarted`/`Ended`/`TimedOut` → `XSpaceState` | `state` | Home badges; player LIVE branch | Player **no actualiza** `currentSpace.state` al terminar. `PlayerUiState.isLive` sigue true. |
| **UPCOMING** | `NotStarted` | sí | Home “PROGRAMADO”; analyze error | No hay UI de espera programada en player. |
| **recordingAvailable** | GQL `is_space_available_for_replay` | sí | Home “Grabación disponible” / “Sin grabación” | **Bug:** `resolve()` pone `recordingAvailable=true` si obtiene HLS live (`media_key`). Un LIVE con stream **no** es replay. |
| **audioStreamUrl** | 1) yt-dlp `formats[].url`  2) `live_video_stream/status` `source.location` o `noRedirectPlaybackUrl` | sí | No se muestra; se usa como `mediaUri` | HLS Periscope/pscp. Caduca. Live y replay **pueden ser URLs distintas**. |
| **media_key** | GQL `metadata.media_key` | **no persistido** (solo dentro de `rawMetadata`) | No | Necesario para pedir HLS. No exponer en UI. |
| **replay URL** | Misma API de stream **después** de ENDED, o yt-dlp | `audioStreamUrl` al re-resolver; `PendingLiveDownload.replayStreamUrl` | Botón “Descargar Space” si Available | Puede tardar minutos tras el corte. **No fabricar URL.** |
| **Connection state** | No existe en X ni en el modelo Space | — | Buffering = `PREPARING` | `NetworkPlaybackMonitor` reabre HLS si live + error/preparing al volver red. No hay estado “Reconectando”. |
| **Playback state** | libmpv → `EnginePlaybackState` | no (sesión) | Play/Pause LIVE | `eof-reached` **y** `MPV_EVENT_END_FILE` emiten `PlaybackFinished` **dos veces**. |
| **Download state** | `DownloadItem` + `PendingLiveDownloadStatus` | `pending_live_downloads.json` + store descargas | LiveEndedContent: cola / processing; Descargas | Dedup actual **no usa space id**. |
| **Auto-download** | Preferencia local, no de X | `PendingLiveDownload.autoDownloadAfterEnd` por **spaceId** | `AutoDownloadToggle` | Persistencia **sí existe** en disco. UI arranca `false` y solo lee el repo si `getSpaceForMedia` encuentra el Space. Toggle posterior **no** actualiza el extra del Service (el Service **sí** relee el repo al terminar). |
| **Errores X** | HTTP guest/GQL, mensajes yt-dlp | no | Home `errorMessage`; player card genérica | Enum `XSpaceError` **nunca se usa**. Copy ya está en ES en el enum. |
| **speakerSegments** | Nada de X | campo vacío persistible | No | Reservado diarización. **No rellenar.** |
| **Guest token** | `POST guest/activate.json` + bearer público | memoria (XLiveStreamClient cachea; resolver GraphQL **no**) | No | Anónimo. No cookies. Espacios privados → fallo, no login. |

### 1.1 Qué entrega X de verdad (contrato)

GraphQL `AudioSpaceById` (guest):

- `metadata.title`, `state`, `created_at`, `started_at`, `ended_at`
- `is_space_available_for_replay` (bool; **no implica HLS listo**)
- `total_live_listeners`, `total_replay_watched`
- `media_key`
- `creator_results` (host)
- `participants.admins | speakers | listeners` (muestra)

REST `live_video_stream/status/{media_key}`:

- `source.location` / `source.noRedirectPlaybackUrl` (HLS)

Tweet card (si la URL es `/status/`):

- `binding_values` key `id` o `audio_space_id`

yt-dlp (`twitter:spaces` / `TwitterSpaces`):

- `title`, `uploader`, `uploader_id`, `thumbnail`, `duration`, `was_live`, `formats[].url`

### 1.2 Qué MediaFlow puede persistir

| Store | Archivo | Contenido |
|---|---|---|
| `XSpaceStore` | `filesDir/mediaflow_spaces.json` | `XSpace` completo (incl. HLS que caduca, `rawMetadata`) |
| `PendingLiveDownloadRepositoryImpl` | `filesDir/pending_live_downloads.json` | por **spaceId**: título, host, url X, auto-download, status, replay URL, downloadId, error |
| `Media3DownloadRepository` | índice Media3 + platform store | `DownloadItem`; `id` = SHA-256(`sourceUrl\|fileName`)[:24] |
| `PlayerService` / mpv | sesión | isLive, posición, artworkUrl |

El mapa `mediaId → spaceId` de `XSpaceRepositoryImpl` es **solo memoria**; al relanzar se reconstruye desde `id`, `url`, `audioStreamUrl`.

### 1.3 Qué NUNCA fabricar

- Duración LIVE (ni elapsed fingido como duración total).
- Replay URL si `audioStreamUrl == null`.
- `recordingAvailable = true` solo porque hay HLS en vivo.
- Lista completa de oyentes.
- Miniatura de Space distinta del avatar (salvo yt-dlp `thumbnail` real).
- Botón Descargar / “listo” si X dice processing o replay off.
- Segundo motor de audio.
- Login / cookies de X.

---

## 2. Modelos vs ViewModels vs pantallas

```
XSpace / XSpaceState / XParticipant
    ↑ parse XSpaceMetadataResolver
LiveSpaceSource          ← no usado en UI
ReplayResolutionResult   ← monitor + VM
LiveSpaceEndState        ← PlayerUiState.liveEndState
PendingLiveDownload      ← repo persistente
PlayerServiceState.isLive
PlayerUiState.isLive = service.isLive || space.isLive
```

| Dato | Modelo | ViewModel | Pantalla |
|---|---|---|---|
| Título / host / avatar | `XSpace` | `currentSpace` | Home card, `AudioPlayerView`, notificación |
| LIVE | `state==LIVE` | `effectiveLive` heurística HTTP | `PlayerScreen` rama LIVE vs audio |
| Fin de Space | GQL ENDED | `liveEndState` | `LiveEndedContent` **solo si no ActiveLive** |
| Auto-download | pending repo | `isAutoDownloadEnabled` | `AutoDownloadToggle` |
| Replay | `ReplayResolutionResult.Available` | `downloadSpaceReplay` | botón Descargar |
| Miniplayer LIVE | — | `serviceState.isLive` | texto “En directo” + barra roja 100% |
| Errores Space | `XSpaceError` (muerto) | `serviceState.errorMessage` | card genérica |

**Heurística peligrosa** (`PlayerViewModel.open`):

```text
effectiveLive = isLive
  || space.isLive
  || (http && !space.isEnded && !*.mp4 && !*.m4a)
```

`PlayerScreen` llama `open(mediaUri)` **sin** `isLive=true`. Un HLS de Space funciona por la heurística; un HTTP no-Space también se marca LIVE.

`getSpaceForMedia`: si el mapa falla y **solo hay un Space** guardado, **devuelve ese**. Riesgo de metadata ajena.

---

## 3. Detección de fin (conservar; solo cablear)

Señal de playback (no es X):

1. libmpv `eof-reached` → `PlaybackFinished`
2. libmpv `MPV_EVENT_END_FILE` → **otro** `PlaybackFinished` (duplicado)
3. Fallo de load → `PlaybackError`

`PlayerService` en LIVE **reenvía** todos los eventos (no auto-avanza cola).

Confirmación **real** (sí es X) — **mantener** `LiveSpaceEndMonitor.verifySpaceEnded`:

1. Hasta 3 intentos, backoff 3s → 6s → 12s.
2. Cada intento: `XSpaceMetadataResolver.resolve`.
3. `LIVE` → `Processing("El Space sigue activo en directo.")` (corte de red, no fin).
4. `ENDED` / `TIMED_OUT` → `XSpaceReplayResolver.resolveReplay`.
5. `UNKNOWN` reintenta; al agotar, `resolveReplay`.

`XSpaceReplayResolver`:

| Estado Space | audioStreamUrl | recordingAvailable | Resultado |
|---|---|---|---|
| UPCOMING / LIVE | — | — | Processing (aún no replay) |
| ENDED/TIMED_OUT/UNKNOWN | no null | — | **Available(url, space)** |
| ENDED… | null | **true** | **Processing** (X está generando replay) |
| ENDED… | null | **false** | **NotAvailable** (host no guardó) |
| excepción | — | — | Error |

### 3.1 Bugs del cableado actual

| Bug | Dónde | Efecto |
|---|---|---|
| VM solo reacciona a `PlaybackError`, no a `PlaybackFinished` | `PlayerViewModel` | Corte limpio del HLS: UI sigue “en vivo”; overlay de fin **no aparece**. |
| Service reacciona a Finished **y** Error | `MediaPlaybackService.handleStreamEnded` | Descarga en background **sin** actualizar UI. |
| Finished se emite **dos veces** | `MpvPlaybackEngine` eof + END_FILE | Doble `verifySpaceEnded` + carrera de descargas. |
| Service no reintenta `Processing` | `handleStreamEnded` | Si el replay tarda, **nunca** auto-descarga. |
| VM `checkReplayAgain` = un shot | botón “Comprobar de nuevo” | Sin backoff; spam si el usuario pulsa; sin auto-retry. |
| `currentSpace.state` no pasa a ENDED | VM | Header/badge siguen LIVE; `isLive` permanece true. |
| `recordingAvailable=true` con HLS live | `XSpaceMetadataResolver.resolve` | Home puede decir “Grabación disponible” con cache sucio. |
| Dedup `download.id.contains(spaceId)` | Service | `id` es hex SHA; **nunca** contiene el Space id. Home descarga `x.com/i/spaces/id`; auto usa HLS pscp → **dos descargas**. |
| `XSpaceError` no cableado | core | Copy de error no reutilizada. |
| `ResolveLiveSpaceUseCase` huérfano | domain/data | No tocar para un segundo player. |

---

## 4. Plan de implementación (Subagente 2)

Objetivo de producto: reproducir el LIVE (ya existe), mostrar estado honesto, al terminar **esperar replay real** y auto-descargar **una vez** por Space si el usuario lo pidió.

### 4.1 Archivos a tocar

**Datos / dominio (wiring, no rediseño de APIs X)**

- `data/.../live/LiveSpaceEndMonitor.kt` — conservar verify; añadir espera de replay con backoff acotado.
- `data/.../live/XSpaceReplayResolver.kt` — **no cambiar contrato**; opcionalmente reutilizar `XSpace` ya resuelto para no pegarle dos veces a GraphQL.
- `data/.../live/PendingLiveDownloadRepositoryImpl.kt` — persistir intentos; status `RESOLVING_REPLAY`.
- `domain/.../live/PendingLiveDownload.kt` — campos `attemptCount`, `nextRetryAtMs` (opcional, persistibles).
- `data/.../spaces/XSpaceMetadataResolver.kt` — `recordingAvailable` **solo** de `is_space_available_for_replay`; no marcarlo true por HLS live.
- `data/.../repository/XSpaceRepositoryImpl.kt` — quitar fallback “si hay un solo Space”.
- `data/.../player/background/MediaPlaybackService.kt` — un solo disparo de fin; retry replay; dedup space id.
- `data/.../player/MpvPlaybackEngine.kt` — **un** `PlaybackFinished` por cierre (eof **o** END_FILE, no ambos). No reescribir libmpv.

**UI / VM**

- `app/.../player/PlayerViewModel.kt` — Finished+Error LIVE; single-flight; persistir toggle; actualizar `XSpace` ENDED; no descargar duplicado.
- `app/.../player/PlayerUiState.kt` — si hace falta: `isBroadcastLive` vs `isLiveSession` (LIVE real vs overlay fin).
- `app/.../player/PlayerScreen.kt` — rama LIVE: **sin** seek ±10; header según estado.
- `app/.../player/components/LivePlayerView.kt` — layout cobre; badge LIVE real; artwork avatar.
- `app/.../player/live/LiveEndedContent.kt` — copy “Esperando repetición”; sin botón si auto-retry activo.
- `app/.../player/live/AutoDownloadToggle.kt` — chip cobre seleccionado; 48 dp.
- `app/.../player/components/LiveStatusBadge.kt` — `isLive` obligatorio desde el caller (quitar default true en llamadas LIVE).
- `app/.../player/miniplayer/MiniPlayer.kt` — badge LIVE claro; no duplicar “En directo” como autor.
- `app/.../player/components/PlayerHeaderContext.kt` — LIVE / FINALIZADO según estado.
- `app/src/main/res/values/strings.xml` — copy ES.
- Tests: monitor backoff, dedup space id, toggle persistido, VM no doble-download.

**No tocar**

- Nuevo player, rewrite libmpv, shuffle, seek LIVE, botones de login X, `speakerSegments` fake.
- `XLiveSpaceResolver` / `ResolveLiveSpaceUseCase` salvo reutilizar internamente el mismo `XSpace` (opcional). Home fast-path se queda.

### 4.2 Single-flight de fin

Una sola corrutina por `spaceId` (Mutex / `Job` cancelable) compartida en **espíritu** entre VM y Service:

**Recomendado:** el **Service** es dueño de verify + auto-download (sobrevive al salir del PlayerScreen). El **VM observa** `pendingDownloadRepo.observePendingDownloads()` + resultado del monitor **o** re-llama `verifySpaceEnded` solo para pintar `LiveSpaceEndState`, **sin** `startDownload` si el Service ya lo hizo.

Si se mantiene lógica en ambos:  
`pendingRepo.getPendingDownload(spaceId)` con status `DOWNLOADING|COMPLETED|RESOLVING_REPLAY` → **return**.

Al confirmar ENDED: `spaceRepository.saveSpace(space.copy(state=ENDED, audioStreamUrl=replay o previo, recordingAvailable=flag X))`.

### 4.3 Replay con delay (no spam)

Cuando monitor confirma ENDED y resolver devuelve **Processing** (`recordingAvailable && url==null`):

1. UI: `EndedReplayProcessing` / copy **“Esperando repetición”**.
2. Status pending: `RESOLVING_REPLAY`.
3. Backoff **acotado**, no bucle infinito:

| Intento | Espera |
|---|---|
| 1 | 5 s (el monitor ya esperó ~3–12 s) |
| 2 | 15 s |
| 3 | 30 s |
| 4 | 60 s |
| 5 | 120 s |
| tope | **8 intentos** o ~10 min |

Luego: `NotAvailable` con “La repetición no está lista. Inténtalo más tarde.” + botón **una** vez “Comprobar de nuevo” (reinicia **un** ciclo, no un while).

Errores de red: mismo backoff; no martillar GraphQL.

`Available` + auto-download on → `startDownload` **una vez**.

### 4.4 Dedup de descargas (clave = space id)

Antes de `startDownload`:

1. `pending.get(spaceId)` DOWNLOADING/COMPLETED/READY_TO_DOWNLOAD con `downloadId` → no-op.
2. `observeDownloads()`: `fileName` contiene `_{spaceId}` **o** `XSpaceRepository.getSpaceForMedia(item.id/sourceUrl)?.id == spaceId`.
3. `DownloadRequest.fileName = "Space_{host}_{spaceId}.m4a"` (ya es así).
4. Preferir `sourceUrl = space.url` (`https://x.com/i/spaces/{id}`) para yt-dlp **si** el replay ya está Available (el extractor coge el HLS). Si se usa la URL HLS, el SHA cambia → el paso 1–2 es obligatorio.

No encolar Home “Descargar ahora” + auto-replay del mismo Space.

### 4.5 Preferencia auto-descarga

Ya persiste en `setAutoDownloadEnabled(spaceId, ...)`. Completar wiring:

1. Al `open` LIVE: si no hay space por HLS, resolver por `XUrlParser` / `getSpace(id)` / mapa `audioStreamUrl`.
2. `isAutoDownloadEnabled.value = repo.isAutoDownloadEnabled(spaceId)` (default **false** si no hay fila).
3. Toggle siempre escribe disco **antes** de pintar éxito.
4. Al terminar, Service: `autoDownloadWhenEnded \|\| repo.isAutoDownloadEnabled(spaceId)`.
5. No crear fila pending con `autoDownloadAfterEnd=true` por defecto al abrir LIVE.

### 4.6 UI `LivePlayerView` (identidad cobre)

```
+----------------------------------+
| [↓]     (EN VIVO)          [ ]   |  badge #F04455; FINALIZADO si ended
|                                  |
|         [ avatar 220 sq ]        |  radio 20, MediaArtwork, NO vinilo
|                                  |
| Título Space                     |  titleLarge 20 SemiBold
| Host: Nombre (@handle)           |  bodyMedium muted
| [@spk1] [@spk2]  (si X los dio)  |  máx 4; no inventar
| N oyentes   solo si count > 0    |
|                                  |
|           ( PLAY 72 cobre )      |  sin ±10, sin skip, sin seek
| [ Descargar cuando termine ]     |  chip 40/hit 48; selected cobre
| ✓ Se descargará al finalizar     |  solo si toggle ON
+----------------------------------+
```

Tras fin (misma pantalla, **no** otro player):

```
| TRANSMISIÓN FINALIZADA           |
| Este Space ha finalizado         |
| Esperando repetición…    spinner |  Processing / resolving
| [ Descargar Space ]              |  solo Available y auto-download OFF
| Descarga añadida a la cola       |  Started — CTA único: ir a Descargas no hace falta botón extra
| El host no guardó la grabación   |  NotAvailable — sin botón fake
```

- Cards radio 20, borde `line`, elevación 0; LIVE: borde 1.5 rojo o barra izq 3 dp.
- `LivePlayerView` debe pasar `artworkUrl` / avatar a `AudioPlayerView`.
- `PlayerScreen` LIVE: **desactivar** `onDoubleTapLeft/Right` seek.
- Miniplayer: artwork 48; si `isLive && space LIVE`: pill o texto `En directo` en `#F04455` **una vez**; subtítulo = host, no segundo “En directo”; barra sólida LIVE. Si sesión terminó, quitar LIVE.

### 4.7 Copy ES (`strings.xml`, tono directo, sin emojis)

| Clave sugerida | Texto |
|---|---|
| `space_live_badge` | `EN VIVO` |
| `space_ended_badge` | `FINALIZADO` |
| `space_listen_live` | `Escuchar en vivo` |
| `space_host_format` | `Host: %1$s` |
| `space_listeners_format` | `%d oyentes` |
| `space_auto_download_off` | `Descargar cuando termine` |
| `space_auto_download_on` | `Descarga programada al terminar` |
| `space_auto_download_hint` | `Se descargará al finalizar` |
| `space_ended_title` | `Este Space ha finalizado` |
| `space_ended_pill` | `Transmisión finalizada` |
| `space_waiting_replay` | `Esperando repetición` |
| `space_waiting_replay_body` | `X todavía está generando la grabación. No se inventa el archivo.` |
| `space_replay_ready` | `La grabación está lista.` |
| `space_download_replay` | `Descargar Space` |
| `space_download_queued` | `Descarga añadida a la cola` |
| `space_no_replay` | `El host no guardó la grabación.` |
| `space_replay_timeout` | `La repetición no está lista. Inténtalo más tarde.` |
| `space_check_again` | `Comprobar de nuevo` |
| `space_still_live` | `El Space sigue en directo.` |
| `space_upcoming` | `Este Space todavía no ha comenzado (Programado).` |
| `miniplayer_live` | `En directo` (ya existe) |

Reutilizar `XSpaceError.userMessage` en análisis Home cuando el caso coincida.

### 4.8 Conexión / playback

- Buffering: indicador existente `Cargando…`.
- Red: conservar `NetworkPlaybackMonitor` (reabrir HLS). No declarar fin por un error único: **siempre** `verifySpaceEnded`.
- Play/Pause LIVE: ya correcto (sin seek).
- No mostrar `durationMs` de mpv en LIVE (HLS a veces reporta ventana, no duración del Space).

### 4.9 Miniatura

Orden: `host.avatarUrl` https → placeholder Space.  
Al auto-descargar: `DownloadRequest.thumbnailUrl = host.avatarUrl` (ya). No usar HLS como thumb.

---

## 5. Tests mínimos

- `LiveSpaceEndMonitor`: LIVE → Processing; ENDED+url → Available; ENDED+recording+sin url → Processing; no más de `maxRetries` hits.
- Nuevo: Processing luego Available en intento N; tope de intentos sin bucle.
- `PendingLiveDownloadRepository`: toggle on/off round-trip disco; `isAutoDownloadEnabled` false si no hay fila.
- Dedup: dos `startDownload` mismo `spaceId` → un `DownloadItem`.
- VM: `PlaybackFinished` LIVE dispara overlay; segundo evento no segunda descarga.
- `XSpaceMetadataResolver`: LIVE + HLS no fuerza `recordingAvailable`.
- Compose: `LivePlayerView` muestra EN VIVO / toggle; `LiveEndedContent` “Esperando repetición”.

---

## 6. Resumen ejecutivo para el padre (10 puntos)

1. X (guest GraphQL + `live_video_stream`) da: id, url, título, state, host/admins/speakers, counts, timestamps, `is_space_available_for_replay`, `media_key`, HLS. No da portada de Space, duración LIVE, lista real de oyentes ni replay instantáneo.
2. MediaFlow persiste `XSpace` y pending auto-download por **spaceId**; el player LIVE ya usa libmpv con el HLS del fast-path Home. `ResolveLiveSpaceUseCase` está huérfano.
3. Fin real = `LiveSpaceEndMonitor` + `XSpaceReplayResolver` (GQL ENDED, no el eof de mpv). Conservar.
4. Replay = re-resolve; Available solo con URL real; Processing si replay flag y HLS aún null (delay de X).
5. Bugs: Finished duplicado; VM ignora Finished; Service no espera replay; dedup no usa space id; `recordingAvailable` sucio; UI LIVE no pasa a “finalizado”; heurística HTTP=live.
6. Auto-download **sí** se escribe en JSON; falla si no hay Space en el mapa HLS o si el replay llega tarde.
7. Nunca fabricar duración, replay, thumbs ni botones de descarga “listos”.
8. Subagente 2: cablear single-flight, backoff “Esperando repetición”, dedup `spaceId`, persistir toggle, UI cobre modular.
9. Copy ES en `strings.xml`; miniplayer LIVE una sola etiqueta `En directo`.
10. Spec: `docs/design/x-spaces-live-flow.md`.
