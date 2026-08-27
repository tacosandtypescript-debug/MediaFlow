package com.mediaflow.core.model

/**
 * Normalized errors for X Space operations.
 */
enum class XSpaceError(val userMessage: String) {
    X_SPACE_NOT_FOUND("El X Space no existe o fue eliminado."),
    X_SPACE_RECORDING_UNAVAILABLE("La grabación de este Space no está disponible o fue desactivada por el autor."),
    X_AUTH_REQUIRED("Este Space requiere inicio de sesión en X."),
    X_METADATA_UNAVAILABLE("No se pudo obtener la información pública del Space."),
    X_PARTICIPANTS_UNAVAILABLE("La lista de participantes no está disponible."),
    X_DOWNLOAD_FAILED("No se pudo descargar el audio del Space."),
    X_CONTENT_UNSUPPORTED("El enlace de X no contiene un Space válido o descargable.")
}
