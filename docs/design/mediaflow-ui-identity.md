# MediaFlow — Identidad visual y auditoría UI

**Identidad (una frase):** Señal persistente.

No es un clon de Spotify. No usar `#1DB954`. No usar crema + serif “IA genérica”. No usar verde ácido sobre negro. El primario actual `#7C3AED` (Material purple) se **retira**. MediaFlow es un **archivo de señal**: capturas un enlace (YouTube / X Space), lo guardas, lo reescuchas. Paleta de **onda corta**: tinta azul-noche, cobre de indicador, rojo LIVE urgente, rosa-archivo para favoritos.

**Modos:** `ThemeMode.SYSTEM | LIGHT | DARK` (ya existe). Arquitectura actual se mantiene. Composables modulares. Sin botones falsos.

**Especificación para Subagente 2.** Implementar en el orden P0 → P1 → P2 al final de este documento. No reescribir navegación ni el motor de playback.

---

## 1. Auditoría (estado actual)

- **Color / tokens** — `app/.../ui/theme/Color.kt`, `Theme.kt`: primario Material `#7C3AED` / `#8B5CF6` en chips, nav, progreso, miniplayer, filas “playing”. Light/dark existen (`ThemeMode` + `lerpSchemes`), pero el tema interpola **450 ms** (fuera de 200–350). El fondo `MediaFlowBackground.kt` es un degradado vertical genérico surface, no una señal.

- **Tipografía** — `Theme.kt` `MediaFlowTypography`: Default Compose (sin deps de fuentes). Display 40 / Headline 34 / Title 20 / Body 17. Jerarquía inflada para móvil; Home usa `headlineLarge` + primario púrpura para el título de la app (`HomeScreen.kt`). Tiempos (`mm:ss`) usan `String.format` con la fuente por defecto, no tabular/mono.

- **Espaciado** — Home `20.dp` horizontales; Biblioteca `16.dp`; Descargas `16.dp`; Settings `16.dp`. Miniplayer `padding 12/6`. Inconsistente 14/16/18/20. Listas de biblioteca/playlists/favoritos añaden `bottom = 120.dp` para el miniplayer (bien), Home y Settings no reservan hueco extra.

- **Cards / botones** — Cards `shapes.large` 28 dp + elevación 2–6 (`HomeScreen`, `DownloadsScreen`, `PlaylistCard`). CTA Home `DownloadButton.kt` es `Button` full-width Material default (sin altura 52 ni cobre). Chips de biblioteca (`LibraryTabs.kt`) usan púrpura seleccionado. `LibraryMediaSelector.kt` segmentos con `padding vertical 8.dp` (**< 48 dp**).

- **Jerarquía** — Home: marca enorme + card URL + análisis + selector + filename + CTA + bloque “recientes” **siempre vacío** (`HomeScreen.kt` ~250–274: `EmptyState` estático, no lee descargas reales). Biblioteca: header “Tu biblioteca” + Audio/Video + chips; Favoritos tiene hero rosa; Playlists fila + cover 64. Player: vinilo rotatorio (`AudioPlayerView.kt`) ignora `thumbnailUri` salvo avatar de Space.

- **Nav + miniplayer** — `AppNavigation.kt`: MiniPlayer encima de `NavigationBar` (4 tabs: Home, Biblioteca, Descargas, Ajustes). Miniplayer visible si `playbackState != IDLE && filePath`. Artwork 46 dp. Skip next **sí** está cableado y se oculta si `!hasNext`. Player fullscreen oculta bottom bar (ruta `player/{mediaId}`).

- **Vacío / carga / error** — `EmptyState.kt` (animación 450 ms) y `EmptyLibraryState.kt`. Copy mixto: strings.xml vs hardcode. Player error: card + “Volver”. Buffering: “Cargando...” / “Buffering...” (`PlayerScreen.kt`). Análisis: `SourceAnalysisCard`. Settings: switches **locales no persistidos** (Wi‑Fi, notificaciones, almacenamiento deshabilitado siempre `false`) — controles falsos (`SettingsScreen.kt`).

- **Light/dark** — Tokens duales en `Color.kt`. `ThemeViewModel` + segmented en Ajustes. Favoritos (`FavoritePink #D95B9B`) y LIVE (`#F04455`) ya semánticos. Light miniplayer `#F3EDFD` (lavanda púrpura) hay que rehacer.

- **Targets 48 dp** — Incumplen: MiniPlayer art 46; `PlaybackControls` rewind/forward `44.dp`; `PlayerSecondaryActions` speed `38.dp`; `LibraryMediaSelector` ~36 dp; chips FilterChip por defecto ~32. IconButtons de Descargas sí 48 (M3 default). Play/Pause player 72 (bien, dominante).

- **Controles falsos / no soportados** — No hay shuffle en el código (bien, no inventarlo). `PlaybackControls.kt`: SkipPrevious está **enabled si `hasPrevious || !isLive`**, o sea activo sin pista anterior (falso). SkipNext se dibuja deshabilitado si `!hasNext` (ocultar, no ghost). Cola **sí** está cableada (`PlayerQueueSheet`, `hasNext`/`playNext`). No hay drag-to-reorder de cola. Home “recientes” siempre vacío. Settings Wi‑Fi / calidad / notificaciones / almacenamiento no persisten.

- **Miniaturas rotas** — `DownloadItem.thumbnailUri` existe (`core/model/.../DownloadItem.kt`) y se persiste en `PlatformDownloadStore.kt`, **nunca se asigna** al crear el item (`YtDlpPlatformDownloader.kt`, `Media3DownloadRepository.toDomainItem()`, `DownloadRequest` no tiene campo). `YtDlpRuntime` `writethumbnail: false`. UI usa `thumbnailUri ?: localUri` (audio/vídeo como imagen → Coil falla). `MediaArtwork.kt`: `AsyncImage` **sin** `placeholder`/`error`; si URL vacía hay icono; si URL es un `.m4a` queda caja vacía/rota. MiniPlayer: `artworkUrl ?: mediaUri`. Player audio: solo `space.host.avatarUrl`. Descargas: **cero artwork** en `DownloadCard`. Tras muerte de proceso: store puede tener `thumbnailUri=null` para siempre.

---

## 2. Paleta (hex)

LIVE permanece **rojo urgente**. Favoritos **distintos** del LIVE y del primario.

### Oscuro — Señal de medianoche

| Token | Hex | Uso |
|---|---|---|
| `ink` / background | `#0B1118` | Fondo app |
| `panel` / surface | `#161E2C` | Cards, sheets |
| `panelRaised` | `#1E2838` | Elevado, miniplayer, nav |
| `copper` / primary | `#D2783A` | CTA, progreso, tab activo, play |
| `onCopper` | `#1A120C` | Texto sobre primario |
| `live` | `#F04455` | LIVE only |
| `favorite` | `#C45C86` | Corazón / tab Favoritos |
| `text` | `#F2EEE8` | Título |
| `textMuted` | `#9AA3B2` | Subtítulo |
| `line` | `#2A3546` | Bordes |

### Claro — Papel de archivo

| Token | Hex | Uso |
|---|---|---|
| `paper` / background | `#F3F0EB` | Fondo (papel, no crema de lujo) |
| `sheet` / surface | `#FFFFFF` | Cards |
| `sheetMuted` | `#E8E4DC` | Chips, inputs |
| `copper` / primary | `#B85F28` | CTA, tab activo |
| `onCopper` | `#FFFFFF` | Texto sobre primario |
| `live` | `#F04455` | LIVE |
| `favorite` | `#B24A74` | Favoritos |
| `text` | `#1A1F28` | Título |
| `textMuted` | `#5C6573` | Subtítulo |
| `line` | `#D5D0C6` | Bordes |

**No usar:** `#7C3AED`, `#8B5CF6`, `#1DB954`, `#00FF7F`, `#0B0E15` púrpura-negro.

**Mapeo Color.kt (Subagente 2):**

- Reemplazar `PrimaryPurple` / `PrimaryLight` / `PrimaryBright*` / `ProgressPlayed*` / `ChipSelected*` / `NavigationSelected*` por cobre.
- `FavoritePink` → `#C45C86` / light `#B24A74`.
- `LiveRed` se queda `#F04455`.
- `LibraryRowPlaying*` borde cobre, no violeta.
- `MiniPlayerBackgroundDark` → `#1E2838`; light → `#FFFFFF` con borde `#D5D0C6`.
- Gradiente primario: `#E08A4A` → `#B85F28` (no púrpura).
- Error: `#E25555` (distinto de LIVE por contexto: mensajes, no badge).
- Success (progreso descarga): `#3D9B7A` (verde archivo, **no** Spotify).
- Buffering: cobre 60 % alpha, no lila.

---

## 3. Tipo (fuentes de sistema, sin deps)

Usar `Typography()` base (Roboto / sistema). `FontFamily.Monospace` solo para tiempos.

| Estilo | Size / Weight | Uso |
|---|---|---|
| `headlineMedium` | 24 sp / SemiBold | Títulos de pantalla (Home, Biblioteca) |
| `titleLarge` | 20 sp / SemiBold | Títulos de sección, título player |
| `titleMedium` | 16 sp / SemiBold | Filas de media, cards descarga |
| `bodyLarge` | 16 sp / Regular, LH 24 | Subtítulos Home, settings |
| `bodyMedium` | 14 sp / Regular, LH 20 | Meta, host, estado |
| `labelLarge` | 14 sp / SemiBold | Chips, CTA, tabs |
| `labelSmall` | 11 sp / Medium | LIVE, duración overlay |
| Mono `labelMedium` | 13 sp / Medium | `mm:ss`, `%`, bytes |

Bajar `displaySmall`/`headlineLarge` 40/34: no usar en UI de producto. Color de título de pantalla = `onSurface`, **no** primary.

---

## 4. Componentes

### Cards
- Radio **16 dp** listas; **20 dp** hero (Space, análisis).
- Color `surface`. Borde 1 dp `line`, **elevación 0** (deja el púrpura elevado).
- Playing: borde 1.5 dp cobre, fondo `copper` 10 % alpha.
- LIVE Space: borde izquierdo 3 dp `#F04455`.

### Botones
- Primario (Descargar, Reproducir): filled cobre, height **52 dp**, radio 14, `onCopper`.
- Tonal: `sheetMuted` / `panelRaised`, height 48.
- IconButton: **48×48** mínimo. Play player **64–72**. Mini play **48**.
- No ghost skip. Si `!hasPrevious` / `!hasNext`: **no componer** el icono (el slot desaparece; rewind/forward ocupan).

### Chips
- Radio 12, height **40** (hit 48 con padding de fila).
- Seleccionado: fondo cobre, texto `onCopper`.
- Favoritos seleccionado: fondo `#C45C86`, texto blanco (no cobre).
- LIVE chip: fondo `#F04455`, texto blanco, sin pulse infinito (opacidad 1).

### Diálogos / sheets / inputs
- Dialog: surface, radio 20, scrim 60 %.
- Sheet: `panelRaised` / blanco, handle 32×4, radio top 20.
- Input URL: height 52, radio 14, borde `line`; válido = borde cobre; error = error (no LIVE).
- Switch / segmented: track cobre cuando on. Segmented Ajustes height 48.

### Miniplayer
- Height contenido ≥ 56 + barra 3 dp. Radio 12. Margen 8 horizontal / 4 vertical.
- Artwork **48**. Play 48. Skip next **solo si `hasNext`**.
- Barra progreso cobre; LIVE: barra sólida `#F04455` 100 % (sin scrub).

---

## 5. Motion (200–350 ms)

| Evento | Spec |
|---|---|
| Fade/slide nav | 250 ms `FastOutSlowIn` (ya en `AppNavigation`) |
| Miniplayer enter/exit | 280 ms slide+fade |
| Theme lerp | **300 ms** (hoy 450 en `Theme.kt`) |
| EmptyState | 280 ms (hoy 450) |
| Home stagger | **máx 280 ms**, delay ≤ 80 entre bloques (hoy 500 + delays 660) |
| Play/Pause crossfade | 200 ms |
| Favorite scale | spring existente OK |
| Vinilo player | **quitar rotación infinita**; crossfade artwork 250 ms |

Sin shimmer falso de red. Análisis: `LinearProgressIndicator` indeterminado real.

---

## 6. Wireframes ASCII

### Home
```
+----------------------------------+
| MediaFlow                        |
| Pega un enlace y archívalo       |
+----------------------------------+
| [  https://...            (x) ]  |  input 52
+----------------------------------+
| [LIVE] X Space · @host           |  solo si Space
|  avatar 56  título               |
|  [ Escuchar en directo ]         |  rojo LIVE
+----------------------------------+
| Análisis                         |
|  [thumb 72] título · 12:04       |
|  formato chips (uno seleccionado)|
+----------------------------------+
| Audio | Video                    |  48 dp
+----------------------------------+
| Nombre de archivo                |
+----------------------------------+
| [======== Descargar ahora =====] |  52 cobre
+----------------------------------+
| Recientes (solo si hay items)    |
| [72] título                 3:21 |
+----------------------------------+
| [mini 48] título    [>] [>>?]    |
| ================================ |
| Inicio  Biblio  Descargas  Ajust.|
+----------------------------------+
```

### Biblioteca
```
| Tu biblioteca                    |
| [ Audio          |   Video     ] |  48
| (Todos) (Favoritos) (Playlists)  |  chips 40; Favoritos rosa si activo
|----------------------------------|
| [48] título              04:12 * |
|      host · Space                |
| [48] ...                         |
|           (padding bottom 120)   |
```

Video: grid 2 col, thumb 16:9 + duración overlay + overflow 48.

### Descargas
```
|          Descargas               |
| [48] título                      |
|      42 % · 12 MB / 28 MB        |
|      ========----                |
|              [pause 48] [stop 48]|
```
Vacío: icono + copy + CTA “Ir a Inicio” (único botón).

### Player (audio, no LIVE)
```
| <  Reproduciendo                 |
|                                  |
|         [ artwork 220 sq ]       |  radio 20, NO vinilo
|                                  |
| Título                           |
| Host / archivo            [heart]|
| 1:02 ========---- 4:18           |  mono
|    [ -10 ]  [  PLAY 64 ]  [ +10 ]|
|    [ << ] solo si hasPrevious    |
|    [ >> ] solo si hasNext        |
|  1.0x    +lista    cola    borrar|
```
LIVE: sin seek, sin ±10, sin cola; badge LIVE; play/pause; auto-descarga si existe.

### MiniPlayer
```
| [48 art] Título            [>][>>]|
|          Host · LIVE?             |
| ================================= |
```
`>>` solo `hasNext`. Tap abre player. Play/pause no navega.

### Playlists
```
| Tus playlists        [ + Nueva ] |
| [cover 64] Nombre                |
|            12 audios      [play] |
```

### Favoritos
```
| [rosa 72 corazón] Tus favoritos  |
|  8 audios · 1h 12m   [Reproducir]|
| [48] ...  corazón activo rosa    |
```

### Ajustes
```
| Apariencia                       |
| [ Sistema | Claro | Oscuro ]     |  48, persistido
| Acerca de                        |
| versión                          |
```
**Ocultar** Wi‑Fi only, calidad default, notificaciones, almacenamiento hasta que haya repositorio. No switches muertos.

---

## 7. Copy vacío / error (ES)

Usar `strings.xml`. Tono: directo, sin disculpas, sin emojis.

| Clave | Texto |
|---|---|
| Home subtítulo | `Pega un enlace de YouTube o un X Space y archívalo.` |
| Home recientes vacío | **No mostrar el bloque** si no hay descargas. |
| Descargas vacío título | `Todavía no hay descargas` |
| Descargas vacío sub | `Empieza desde Inicio con un enlace. El progreso aparece aquí.` |
| CTA | `Ir a Inicio` |
| Biblioteca audio | `No hay audios todavía` / `Descarga un audio o un X Space para escucharlo aquí.` |
| Biblioteca video | `No hay videos todavía` / `Descarga un video para verlo aquí.` |
| Favoritos | `Aún no hay favoritos` / `Toca el corazón en un audio o un Space para guardarlo aquí.` |
| Playlists | `Todavía no hay playlists` / `Crea una para agrupar audios y Spaces.` CTA `Crear playlist` |
| Player error | `No se pudo reproducir` / `El archivo no está disponible o está dañado.` CTA `Volver` |
| Buffering | `Cargando…` (no “Buffering...”) |
| Análisis fallido | `No se pudo leer el enlace. Comprueba la URL e inténtalo de nuevo.` |
| Miniplayer LIVE | `En directo` |
| Snackbar descarga | `Descarga añadida. Síguela en Descargas.` |

---

## 8. Plan de miniaturas (persistencia + proceso)

Objetivo: artwork real en Descargas, Biblioteca, listas, Player, MiniPlayer **tras muerte de proceso**. Placeholder nunca “roto”.

### Modelo (ya está)
- `DownloadItem.thumbnailUri: String?` — `core/model/.../DownloadItem.kt`
- Encode/decode JSON — `data/.../PlatformDownloadStore.kt`

### Huecos a cerrar
1. `DownloadRequest` **sin** `thumbnailUrl`. Añadir `thumbnailUrl: String? = null`.
2. `YtDlpPlatformDownloader.start` y `Media3DownloadRepository.toDomainItem` no copian thumbnail.
3. `YtDlpRuntime` `writethumbnail = false`.
4. `SourceInfo.thumbnailUrl` sí llega en análisis (`YtDlpSourceResolver`) y se muestra en Home (`SourceAnalysisCard.ThumbnailPreview`) — **se pierde al descargar**.
5. `PlayerServiceState.artworkUrl` se rellena al `openMedia` / cola (`PlaybackQueueItem.artworkUrl`) — LibraryViewModel ya pasa `thumbnailUri ?: localUri` (el fallback `localUri` es el bug).

### Implementación (preferida, robusta)

**A. Guardar URL remota al encolar (inmediato)**  
Al `start` de descarga, copiar `HomeUiState.sourceInfo.thumbnailUrl` (o avatar Space) a `DownloadItem.thumbnailUri` y persistir vía store. La UI puede mostrar HTTP mientras dura la sesión.

**B. Materializar fichero local al completar (sobrevive process death + URLs caducas)**  
- Directorio: `context.filesDir/thumbs/{downloadId}.jpg` (o `.webp` si el bytes lo es).
- Tras COMPLETED (y en paralelo si la URL es HTTP): descargar bytes de `SourceInfo.thumbnailUrl` con OkHttp/HttpURLConnection (ya hay patrón en `SourceAnalysisCard` / `XSpaceCard` via `URL.openStream()`).
- Alternativa yt-dlp: `writethumbnail = true` y mover el fichero escrito junto al media → `thumbs/{id}`. **Si se activa writethumbnail, no depender solo de eso**: Spaces usan avatar, no thumb de yt-dlp.
- Actualizar item: `copy(thumbnailUri = File(thumbs, "$id.jpg").toURI().toString())` y `store.save`.
- Idempotente: si el fichero existe, no re-descargar.

**C. PlayerService**  
Al abrir / `playQueue`: `artworkUrl = item.thumbnailUri` (file:// o https). **Nunca** `localUri` de audio/vídeo.

**D. MediaArtwork (único loader)**  
```
datos válidos = http(s) | content:// | file:// de imagen (jpg/png/webp/gif)
si inválido o null → placeholder
AsyncImage: placeholder + error = mismo Box (gradiente ink→panel + icono Audiotrack / Videocam / GraphicEq)
crossfade 250
nunca pasar .m4a/.mp4/.webm a Coil
```
Descargas: `DownloadCard` fila con `MediaArtwork(size=56, artworkUrl=item.thumbnailUri)` + placeholder.
Player `AudioPlayerView`: cuadrado 220, `MediaArtwork` (no vinilo). Space: avatar si no hay thumb.
MiniPlayer: `serviceState.artworkUrl` only.

**E. Fallbacks de contenido (orden)**  
`item.thumbnailUri` → `space.host.avatarUrl` **solo si es URL de imagen** → placeholder. Quitar `?: item.localUri` y `?: mediaUri` en Library, Favorites, Playlists, MiniPlayer, Delete dialog, PlaylistCover.

**F. Tests**  
Store round-trip `thumbnailUri`; downloader escribe path; MediaArtwork no llama Coil con uri de media.

---

## 9. Orden de implementación (Subagente 2)

### P0 — Identidad + honestidad de UI (un PR mental, primero)
1. `Color.kt` + `Theme.kt`: paleta cobre, LIVE, favoritos; lerp 300 ms; tipografía tabla §3.
2. `PlaybackControls.kt`: ocultar skip prev/next si no hay pista; targets 48; rewind/forward 48.
3. `SettingsScreen.kt`: quitar switches no cableados (dejar Apariencia + Acerca de).
4. `HomeScreen.kt`: quitar bloque “recientes” estático; stagger ≤ 280 ms; título `onSurface` 24 sp; CTA 52 cobre.
5. `MediaArtwork.kt`: placeholder/error; filtro de URI; usarlo en Descargas.
6. MiniPlayer 48 art; theme tokens cobre.

### P0 — Miniaturas (mismo ciclo, data)
7. `DownloadRequest.thumbnailUrl`.
8. Pasar URL desde Home/ViewModel al start.
9. Persistir en downloader + Media3 mapper + store (ya serializa).
10. Job: copiar a `filesDir/thumbs/{id}` al completar; actualizar `thumbnailUri`.
11. `PlayerService` / `LibraryViewModel.playQueue`: `artworkUrl = thumbnailUri`.
12. Quitar fallbacks a `localUri`/`mediaUri` en listas, player, miniplayer, covers.

### P1 — Pantallas
13. Biblioteca: selector 48, chips 40, fila playing cobre, Favoritos chip rosa.
14. Descargas: card con thumb + estados (colores success/error, no púrpura).
15. Player: artwork cuadrado; copy buffering; secondary actions 48; speed 48.
16. Empty copy → strings.xml (§7).
17. Nav indicator cobre 12 %; iconos selected cobre.

### P2 — Pulido
18. Playlists/Favorites/Settings visual pass (hero favoritos rosa, no púrpura).
19. Dialogs/sheets tokens.
20. `DownloadButton` comment obsoleto (“never starts a real download”).
21. Logs `android.util.Log` en Home/Downloads: quitar de UI.
22. Verificar ThemeMode light+dark en Home, Biblioteca, Player, MiniPlayer.

**No hacer:** shuffle, drag cola, fake next, nuevas fuentes, cambiar grafos de nav, reescribir mpv/yt-dlp salvo `writethumbnail`/thumb file.

---

## 10. Constraints (checklist Subagente 2)

- [ ] Arquitectura actual (ViewModels, `PlayerService`, stores).
- [ ] Sin shuffle / cola drag / prev-next sin cola.
- [ ] Sin botones que no hagan lo que dicen.
- [ ] Composables existentes (`MediaArtwork`, `AudioMediaRow`, `EmptyState`); no monolitos.
- [ ] `ThemeMode` LIGHT + DARK + SYSTEM.
- [ ] LIVE = `#F04455` únicamente.
- [ ] Favoritos ≠ LIVE ≠ primario.
- [ ] Hit target 48 dp en controles táctiles.
- [ ] Motion 200–350 ms.
- [ ] `thumbnailUri` local persistido; Coil + placeholder; visible tras process death.
- [ ] Copy en español, `strings.xml` para textos de usuario nuevos.
)
