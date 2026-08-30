# MediaFlow

Aplicación Android para descargar y reproducir audio y vídeo desde YouTube, YouTube Music, X Spaces y fuentes similares.

**Download and play local audio/video on Android — Kotlin, Jetpack Compose, MediaFlow purple (`#7C3AED`).**

Paquete: `com.mediaflow.app` · Repositorio privado: [tacosandtypescript-debug/MediaFlow](https://github.com/tacosandtypescript-debug/MediaFlow) · rama `master`.

La interfaz está pensada en español. El acento visual es el morado MediaFlow (`#7C3AED` / `#8B5CF6`), no un clon de otras apps de música.

---

## Qué hace

MediaFlow analiza un enlace HTTPS, descarga el contenido (audio o vídeo) y lo guarda en el dispositivo. La biblioteca (**Biblioteca**) mezcla descargas completadas con archivos propios publicados en MediaStore (`Movies/MediaFlow/`, `Music/MediaFlow/`). El reproductor usa **libmpv** en local, con pantalla **Reproduciendo** (Now Playing), miniplayer y notificaciones de reproducción y de descarga.

### Inicio

- Pegar un enlace (YouTube, YouTube Music, X Space, etc.).
- Elegir **Audio** o **Vídeo**, calidad y nombre de archivo opcional.
- Analizar la fuente (título, miniatura, formatos) y descargar.
- Listas: puede encolar cada vídeo de una lista detectada.
- X Spaces: escuchar en vivo cuando hay stream, esperar repetición cuando el Space termina, o programar descarga al finalizar (sin inventar archivos si X no publica grabación).
- Accesos de **descargas recientes**.

### Biblioteca

- Filtros: todo, vídeos, audio, favoritos, playlists.
- Vídeos en mosaico (cuadrícula); audio en lista.
- Ordenar (más recientes, más antiguos, tamaño, duración, nombre).
- Búsqueda por título / nombre de archivo.
- Selección múltiple (pulsación larga): compartir o eliminar.
- Favoritos y playlists locales.

### Reproductor y notificaciones

- Now Playing a pantalla completa, cola, favorito, error si el archivo no está.
- Servicio en primer plano de reproducción (`MediaPlaybackService`) y notificaciones de progreso de descarga.
- Ajustes: tema claro/oscuro/automático, notificaciones, Wi-Fi only.

---

## Stack técnico

| Capa | Tecnología |
|------|------------|
| UI | Kotlin, Jetpack Compose, Material 3 |
| Navegación | Navigation Compose |
| Extracción | [yt-dlp](https://github.com/yt-dlp/yt-dlp) vía `dev.ffmpegkit-maintained:yt-dlp-android` (módulo `:data`) |
| Reproducción | libmpv (`mpv-android-lib`) |
| Almacenamiento | MediaStore + historial de descargas |
| Imágenes | Coil |
| JVM | 17 · `minSdk` 24 · `compileSdk` / `targetSdk` 37 |

No hay claves de API de terceros en el árbol de la app para el flujo principal de descarga.

---

## Arquitectura

Gradle (`settings.gradle.kts`):

| Módulo | Rol |
|--------|-----|
| `:app` | UI Compose (`com.mediaflow.app`), ViewModels, navegación Inicio / Biblioteca / Descargas / Ajustes / Reproductor |
| `:domain` | Casos de uso e interfaces (análisis, descarga, galería, motor de reproducción) |
| `:data` | yt-dlp, MediaStore, servicios de descarga y `MediaPlaybackService`, resolvers de URL (YouTube / X) |
| `:core:model` | Modelos puros (`DownloadItem`, `MediaType`, `Playlist`, `XSpace`, …) |

La extracción no se llama desde la UI: `:app` depende de `:data` y `:core:model`; yt-dlp queda aislado en `:data`.

---

## Compilar

Requisitos: **JDK 17**, Android SDK, conexión para dependencias Gradle.

```bash
./gradlew :app:assembleDebug
```

APK debug ARM64:

```text
app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

También se generan splits `x86_64` y un APK universal (ABI splits en `app/build.gradle.kts`).

### Instalar con adb

```bash
adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

En emulador x86_64 usa el APK `x86_64` correspondiente.

Tests unitarios (Robolectric / JUnit en `:app`, `:data`, `:domain`):

```bash
./gradlew test
```

---

## Uso responsable

La app descarga contenido que el usuario indica por URL y lo reproduce **en el dispositivo**. Respeta los términos de cada plataforma y la legislación local. Este repositorio es **privado / no publicado** como producto de tienda.

---

## Licencia

Proyecto privado, no publicado. El archivo `LICENSE` del repo está vacío.
