package com.librompaz.app.model

/**
 * Los cuatro perfiles de la app. Ningún estudiante
 * ni apoderado tiene acceso a la app (privacidad hermética).
 *
 * Supuesto: los roles son excluyentes; cada usuario tiene uno solo.
 */
enum class Perfil(val etiqueta: String) {
    DOCENTE("Docente"),
    UTP("Unidad Técnico-Pedagógica (UTP)"),
    INSPECTORIA("Inspectoría"),
    DIRECCION("Dirección")
}
