# Superficies externas de reproducción — estilo Spotify, púrpura MediaFlow

No es un clon de Spotify. **No** wordmark Spotify. **No** `#1DB954`. Primario: **MediaFlow purple `#7C3AED`**. UI **oscuro primero**, copy en **español**.

Estas superficies **no** inventan un lock-screen Activity. Android pinta:

1. **Notificación** — `NotificationCompat.MediaStyle` + token de `MediaSession`
2. **Pantalla de bloqueo / centro de control** — nativo, vía `MediaSession` metadata + `PlaybackState`
3. **Widget de inicio** — `AppWidgetProvider` mini player (RemoteViews)

Hoy: `PlaybackNotificationManager` (canal `mediaflow_playback_channel`, id `1002`) y `MediaSessionController` (`MediaFlowPlaybackSession`). El metadata **debe** alimentarse siempre (título real, artista, bitmap http **y** `file://`). Widget: no existe aún.

---

## 1. Jerarquía visual (las tres superficies)

Orden fijo, compacto, sin chrome extra:

1. **Portada** — protagonista. Cuadrado, esquinas 8–12 dp. Placeholder `ic_widget_art_placeholder` (nota / disco) si no hay arte. Nunca caja vacía.
2. **Título** — 1 línea, ellipsis, blanco `#F5F3FF`, semibold.
3. **Artista** — 1 línea, `#A78BFA` / `#C4B5FD` muted, regular. No “Reproduciendo archivo local” como subtítulo genérico si hay artista.
4. **Transporte** — **solo** Anterior · Play/Pause · Siguiente. Iconos distintos playing vs paused. Sin Stop, shuffle, seek, likes en estas superficies.

Alineación tipo mini Spotify: **arte izquierda** → textos al centro (arriba título, abajo artista) → **3 botones a la derecha**. Espaciado interno 8–12 dp. Targets táctiles 40–48 dp.

---

## 2. Notificación MediaStyle

Canal: `CHANNEL_ID = mediaflow_playback_channel` · nombre **Reproducción de medios** · `IMPORTANCE_LOW` · sin badge · `VISIBILITY_PUBLIC`.

| Campo | Fuente |
|---|---|
| `setLargeIcon` | bitmap portada (http + archivo local) |
| `setContentTitle` | `METADATA_KEY_TITLE` / `serviceState.title` |
| `setContentText` | artista / host (`METADATA_KEY_ARTIST`). Live: `Host · EN VIVO` |
| `setSmallIcon` | `R.drawable.ic_stat_mediaflow` (silueta, no `ic_media_play` de sistema) |
| Color acento | `#7C3AED` (`setColor` + `setColorized(true)` donde el OEM lo respete) |

**Acciones (orden, compact 0–2):**

| Índice | Acción existente | Icono playing | Icono paused | Título ES |
|---|---|---|---|---|
| 0 | `com.mediaflow.action.PREV` | `ic_media_previous` | igual | Anterior |
| 1 | `PLAY` o `PAUSE` | `ic_media_pause` | `ic_media_play` | Pausar / Reproducir |
| 2 | `com.mediaflow.action.NEXT` | `ic_media_next` | igual | Siguiente |

Quitar **Stop** de la fila compacta (sigue existiendo `ACTION_STOP` en sesión / swipe dismiss cuando paused).

```
MediaStyle()
  .setMediaSession(sessionToken)   // obligatorio — une notificación + lock screen
  .setShowActionsInCompactView(0, 1, 2)
  .setShowCancelButton(true)       // pre-L: cancel → ACTION_STOP
```

Content tap: `PendingIntent` → `MainActivity` con extra `extra_open_now_playing` + `mediaId` (ruta Compose `player/{mediaId}`). Flags: `SINGLE_TOP | CLEAR_TOP`.

`setOngoing(isPlaying)`. Compact vs expandido: el sistema coloca large-icon + 3 acciones; no RemoteViews custom.

Live: sin PREV/NEXT si no hay cola; Play/Pause sí. Subtítulo con EN VIVO, no barra de seek (MediaStyle no la pinta en live si duration = 0).

---

## 3. Lock screen (nativo)

**No hay Activity de lock screen.** El OEM lee:

- `MediaSession.setMetadata` — `TITLE`, `ARTIST`, `ALBUM`, `DURATION`, `ALBUM_ART` + `ART` (mismo bitmap que la notificación).
- `MediaSession.setPlaybackState` — `STATE_PLAYING` / `PAUSED` / `BUFFERING`; acciones `PLAY | PAUSE | PLAY_PAUSE | SKIP_TO_NEXT | SKIP_TO_PREVIOUS` (+ `SEEK_TO` si **no** live).
- Token en `MediaStyle.setMediaSession` para que la notificación y el lock screen sean **la misma sesión**.

Callbacks ya en `MediaSessionController`: `onPlay` / `onPause` / `onSkipToNext` / `onSkipToPrevious` / `onSeekTo` / `onStop`. Cablear `ACTION_SKIP_*` en `updatePlaybackState` (hoy faltan).

Si no hay metadata, el lock screen muestra el nombre del paquete: **llamar `updateMetadata` en cada cambio de pista y de arte**.

---

## 4. Widget mini player

**Clase:** `com.mediaflow.app.widget.MiniPlayerWidgetProvider` (`AppWidgetProvider`).  
**Layout:** `res/layout/widget_mini_player.xml`  
**Info:** `res/xml/mini_player_widget_info.xml` — `minWidth` 250 dp, `minHeight` 72 dp, `resizeMode` horizontal, `updatePeriodMillis` 0 (push desde el servicio).  
**Preview:** `widget_mini_player_preview`.

### Estructura XML (ids)

```
RelativeLayout / LinearLayout  @id/widget_root
  ImageView                    @id/widget_artwork          // 56×56 dp, scaleType centerCrop
  LinearLayout vertical        @id/widget_text_block        // weight 1
    TextView                   @id/widget_title             // maxLines 1, ellipsize end
    TextView                   @id/widget_artist
  LinearLayout horizontal      @id/widget_controls
    ImageButton                @id/widget_btn_prev
    ImageButton                @id/widget_btn_play_pause    // icono cambia playing/paused
    ImageButton                @id/widget_btn_next
```

Empty overlay (GONE si hay pista): `widget_empty` + `widget_empty_label` (“Nada en reproducción”).

### Dims / colores

| Token | Valor |
|---|---|
| Fondo root | `#121016` (oscuro, no verde) |
| Radio root | 12 dp |
| Padding | 8 dp |
| Arte | 56 dp, radio 8 dp |
| Gap arte–texto | 10 dp |
| Título | 14 sp, `#F5F3FF` |
| Artista | 12 sp, `#C4B5FD` |
| Iconos transporte | 24 dp, tint `#EDE9FE` |
| Play/Pause activo | tint `#7C3AED` o círculo 36 dp fill `#7C3AED` + icono `#F5F3FF` |
| Disabled skip | alpha 38% si `!hasPrevious` / `!hasNext` |
| Empty texto | `#A78BFA` |

### Clicks (PendingIntent)

| Vista | Intent |
|---|---|
| `widget_root`, `widget_artwork`, `widget_text_block` | Activity → Now Playing (`extra_open_now_playing`, `mediaId`) |
| `widget_empty` | Activity → Home / Biblioteca |
| `widget_btn_prev` | Service `com.mediaflow.action.PREV` |
| `widget_btn_play_pause` | `PLAY` o `PAUSE` según estado |
| `widget_btn_next` | `com.mediaflow.action.NEXT` |

Request codes distintos (10–14) + `FLAG_UPDATE_CURRENT | IMMUTABLE`.

Broadcast de actualización: el `MediaPlaybackService` llama `MiniPlayerWidgetProvider.updateAll(context, state)` en cada `PlayerServiceState` (playing, título, arte, hasNext/Prev). No depender de `updatePeriodMillis`.

---

## 5. Estados

| Estado | Notificación | Lock screen | Widget |
|---|---|---|---|
| **Playing** | ongoing, icono pause, arte + título | `STATE_PLAYING`, pause visible | pause icon, botones vivos |
| **Paused** | no ongoing (swipeable), icono play | `STATE_PAUSED` | play icon |
| **Buffering** | mismo que playing + título intacto | `STATE_BUFFERING` | play/pause deshabilitado 0.5s o spinner no (RemoteViews: dejar pause) |
| **Empty / IDLE** | cancelar notificación (`NOTIFICATION_ID`) | `isActive = false` o `STATE_NONE` | empty overlay VISIBLE, controles GONE |
| **Error** | cancelar o título de error breve | `STATE_ERROR` | empty o último título + play |
| **Live** | sin prev/next compactos si no cola | sin SEEK | prev/next GONE o disabled |

Play vs pause: **nunca el mismo drawable**. Play = triángulo; Pause = dos barras.

---

## 6. Copy ES

- Anterior / Reproducir / Pausar / Siguiente  
- Canal: Reproducción de medios  
- Vacío widget: Nada en reproducción  
- ContentDescription: Portada, Anterior, Reproducir, Pausar, Siguiente  

---

## 7. Implementación (fuera de este doc)

- `PlaybackNotificationManager`: 3 acciones PREV / PLAY-PAUSE / NEXT; `setMediaSession`; arte local.  
- `MediaSessionController.updatePlaybackState`: `ACTION_SKIP_TO_NEXT/PREVIOUS`.  
- `updateMetadata` en cada pista.  
- Widget provider + layout ids de la §4.  
- Deep link Now Playing desde notificación y widget.

No inventar controles. No Activity de lock screen. No verde Spotify.
