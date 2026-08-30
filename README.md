# MediaFlow

**Descarga y reproduce audio y vídeo en Android** a partir de un enlace HTTPS público.

Interfaz en español. Identidad visual morada MediaFlow (`#7C3AED`), no un clon de otras apps de música.

[![Release](https://img.shields.io/github/v/release/tacosandtypescript-debug/MediaFlow?include_prereleases)](https://github.com/tacosandtypescript-debug/MediaFlow/releases)
[![License: MIT](https://img.shields.io/badge/license-MIT-7C3AED.svg)](LICENSE)

Paquete `com.mediaflow.app` · versión **1.2.5** (`versionCode` 4) · rama `master`.

---

## Instalar

APKs firmadas en debug (sideload; hay que permitir orígenes desconocidos):

| APK | Para |
|-----|------|
| [MediaFlow-1.2.5-arm64-v8a-debug.apk](https://github.com/tacosandtypescript-debug/MediaFlow/releases/download/v1.2.5/MediaFlow-1.2.5-arm64-v8a-debug.apk) (~93 MB) | Teléfonos ARM64 (la mayoría, p. ej. Samsung Galaxy A36) |
| [MediaFlow-1.2.5-universal-debug.apk](https://github.com/tacosandtypescript-debug/MediaFlow/releases/download/v1.2.5/MediaFlow-1.2.5-universal-debug.apk) (~216 MB) | Cualquier ABI |

Todas las versiones: [Releases](https://github.com/tacosandtypescript-debug/MediaFlow/releases).

```bash
adb install -r MediaFlow-1.2.5-arm64-v8a-debug.apk
```

No está publicada en Google Play.

---

## Qué hace

Pegas un enlace, MediaFlow lo analiza y puedes **descargarlo** o **reproducirlo**. Los archivos quedan en el teléfono (`Music/MediaFlow/`, `Movies/MediaFlow/`) y en **Biblioteca**.

Sitios que suele reconocer: **YouTube**, **YouTube Music**, **TikTok**, **Instagram**, **Facebook**, **X Spaces**. Solo contenido público. Sin inicio de sesión y **sin eludir DRM**.

### Inicio
- Pegar URL, elegir Audio o Vídeo, calidad y nombre opcional.
- Analizar (título, portada, formatos reales) y descargar.
- Listas: encola cada ítem detectado.
- X Spaces: escuchar en vivo si X publica stream; **Reproducir repetición** y **Descargar Space** si ya terminó. Grabar en vivo no se corta al pausar el audio.
- Descargas recientes en la parrilla.

### Biblioteca
- Filtros: todo, vídeos (mosaico), audio (lista), favoritos, playlists.
- Orden, búsqueda, selección múltiple, compartir, eliminar.
- Reproducir todo, aleatorio, reordenar la cola arrastrando.
- Portadas en la mayor resolución que publique la fuente.

### Reproductor
- Now Playing de audio, vídeo o Space (pantalla propia, no la de una canción).
- Miniplayer, cola, ±10 s, siguiente/anterior.
- Visualizadores de audio desde el PCM del reproductor (sin micrófono). Se pueden apagar.
- Barras del sistema alineadas con el color del cover.

### Descargas
- Cola con notificación de progreso.
- Spaces HLS con varios fragmentos en paralelo.

---

## Uso responsable

Tú eres responsable de los enlaces que pegas y de la ley de tu país. MediaFlow no evade protecciones de las plataformas ni sustituye una tienda oficial.

---

## Privacidad

- Sin cuentas, sin cookies de sesión, sin API keys de Google / X / TikTok en este repositorio.
- `local.properties`, keystores, APKs de build, caches de Gradle y worktrees de git **no se suben**.
- Los tokens de invitado de X se piden en tiempo de ejecución; no son secretos de desarrollador.

---

## Compilar

Requisitos: **JDK 17** y Android SDK.

```bash
git clone https://github.com/tacosandtypescript-debug/MediaFlow.git
cd MediaFlow
./gradlew :app:assembleDebug
./gradlew test
```

| Salida | Uso |
|--------|-----|
| `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk` | Teléfonos |
| `app/build/outputs/apk/debug/app-x86_64-debug.apk` | Emulador x86_64 |
| `app/build/outputs/apk/debug/app-universal-debug.apk` | Universal |

---

## Arquitectura

| Módulo | Rol |
|--------|-----|
| `:app` | UI Compose, navegación Inicio / Biblioteca / Descargas / Ajustes / Player |
| `:domain` | Casos de uso e interfaces |
| `:data` | yt-dlp, libmpv, MediaStore, Spaces, descargas |
| `:core:model` | Modelos (`DownloadItem`, `XSpace`, …) |

| Capa | Tecnología |
|------|------------|
| UI | Kotlin, Jetpack Compose, Material 3 |
| Extracción | [yt-dlp](https://github.com/yt-dlp/yt-dlp) (`yt-dlp-android`) |
| Reproducción | libmpv |
| Imágenes | Coil |
| SDK | minSdk 24 · targetSdk 37 · JDK 17 |

---

## Licencia

[MIT](LICENSE)
