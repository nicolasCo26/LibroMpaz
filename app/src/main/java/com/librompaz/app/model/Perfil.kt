package com.librompaz.app.model

/**
 * Los cuatro perfiles del caso (ver Requerimientos_DSY1105-005, "Actores"). Ningún estudiante
 * ni apoderado tiene acceso a la app (privacidad hermética).
 *
 * Supuesto (a confirmar con el CITT): los roles son excluyentes; cada usuario tiene uno solo.
 */
enum class Perfil(val etiqueta: String) {
    DOCENTE("Docente"),
    UTP("Unidad Técnico-Pedagógica (UTP)"),
    INSPECTORIA("Inspectoría"),
    DIRECCION("Dirección")
}
