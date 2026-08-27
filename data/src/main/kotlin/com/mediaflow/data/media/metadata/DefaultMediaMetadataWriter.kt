package com.mediaflow.data.media.metadata

import android.util.Log
import java.io.File

/**
 * Default implementation of [MediaMetadataWriter] supporting MP4, M4A, MOV and MP3 containers.
 * Uses atomic temporary files to ensure original files are never corrupted in case of failure.
 */
class DefaultMediaMetadataWriter : MediaMetadataWriter {

    companion object {
        private const val TAG = "MediaMetadataWriter"
    }

    override fun writeMetadata(file: File, metadata: MediaMetadata): Result<Unit> = runCatching {
        if (!metadata.hasContent) {
            return@runCatching
        }

        require(file.exists() && file.isFile && file.length() > 0L) {
            "El archivo multimedia no existe o está vacío: ${file.absolutePath}"
        }

        val extension = file.extension.lowercase()
        val tempFile = File(file.parentFile, "${file.name}.meta_tmp_${System.currentTimeMillis()}")

        try {
            when (extension) {
                "mp4", "m4a", "mov" -> {
                    Mp4MetadataEditor.writeMetadata(file, tempFile, metadata)
                }
                "mp3" -> {
                    Id3MetadataEditor.writeMetadata(file, tempFile, metadata)
                }
                else -> {
                    Log.d(TAG, "Formato .$extension no requiere edición de átomos en contenedor local")
                    return@runCatching
                }
            }

            if (tempFile.exists() && tempFile.length() > 0L) {
                val success = tempFile.copyTo(file, overwrite = true)
                require(success.exists() && success.length() > 0L) {
                    "Error al reemplazar el archivo original con la versión etiquetada"
                }
            } else {
                throw IllegalStateException("El archivo temporal de metadata no se generó correctamente")
            }
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }
}
