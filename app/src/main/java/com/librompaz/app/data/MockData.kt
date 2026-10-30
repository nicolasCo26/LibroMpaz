package com.librompaz.app.data

import com.librompaz.app.model.Anotacion
import com.librompaz.app.model.Asignatura
import com.librompaz.app.model.Calificacion
import com.librompaz.app.model.CargaDocente
import com.librompaz.app.model.Curso
import com.librompaz.app.model.EstadoAsistencia
import com.librompaz.app.model.Estudiante
import com.librompaz.app.model.Evaluacion
import com.librompaz.app.model.ObjetivoAprendizaje
import com.librompaz.app.model.Perfil
import com.librompaz.app.model.RegistroAsistencia
import com.librompaz.app.model.RegistroAuditoria
import com.librompaz.app.model.SesionClase
import com.librompaz.app.model.TipoAnotacion
import com.librompaz.app.model.Usuario

/**
 * Datos 100% FICTICIOS (Ley 19.628 y Ley 21.719: prohibido usar nombres, RUT, antecedentes
 * académicos reales o diagnósticos PIE).
 *
 * Ya NO se usan directamente en las pantallas: LibroMpazRepository.poblarSiVacia() los inserta
 * en Room la primera vez que se abre la app (base vacía). Desde ahí, todo se lee y se guarda
 * en SQLite.
 *
 * Supuesto: basta una muestra reducida (2 cursos) en vez
 * de los ~270 estudiantes del colegio.
 */
object MockData {

    private const val DOCENTE_MAT = "Docente de Matemática"
    private const val DOCENTE_LEN = "Docente de Lenguaje"

    /** Usuarios de prueba para el login (ver README). */
    val usuarios = listOf(
        Usuario("U1", DOCENTE_MAT, "docente.mat@libromp.cl", "docente123", Perfil.DOCENTE),
        Usuario("U2", DOCENTE_LEN, "docente.len@libromp.cl", "docente123", Perfil.DOCENTE),
        Usuario("U3", "Coordinación UTP", "utp@libromp.cl", "utp123", Perfil.UTP),
        Usuario("U4", "Inspectoría General", "inspectoria@libromp.cl", "inspectoria123", Perfil.INSPECTORIA),
        Usuario("U5", "Equipo Directivo", "direccion@libromp.cl", "direccion123", Perfil.DIRECCION),
    )

    val cursos = listOf(Curso("C3A", "3° Básico A"), Curso("C4A", "4° Básico A"))

    val asignaturas = listOf(
        Asignatura("MAT", "Matemática"),
        Asignatura("LEN", "Lenguaje y Comunicación"),
        Asignatura("CN", "Ciencias Naturales"),
    )

    val estudiantes = (1..8).map { Estudiante("E3A-$it", "Estudiante 3A-%02d".format(it), "C3A") } +
        (1..8).map { Estudiante("E4A-$it", "Estudiante 4A-%02d".format(it), "C4A") }

    /** El docente U1 tiene Matemática en 3°A y 4°A; U2 tiene Lenguaje en 4°A y Ciencias en 3°A. */
    val cargas = listOf(
        CargaDocente("CD1", "U1", "C3A", "MAT"),
        CargaDocente("CD2", "U1", "C4A", "MAT"),
        CargaDocente("CD3", "U2", "C4A", "LEN"),
        CargaDocente("CD4", "U2", "C3A", "CN"),
    )

    val objetivos = listOf(
        ObjetivoAprendizaje("OA-MAT-1", "MAT", "OA 1", "Contar números del 0 al 1.000."),
        ObjetivoAprendizaje("OA-MAT-2", "MAT", "OA 2", "Leer y representar números hasta el 1.000."),
        ObjetivoAprendizaje("OA-MAT-3", "MAT", "OA 3", "Resolver adiciones y sustracciones."),
        ObjetivoAprendizaje("OA-MAT-4", "MAT", "OA 4", "Reconocer figuras 2D y 3D."),
        ObjetivoAprendizaje("OA-LEN-1", "LEN", "OA 1", "Leer textos con fluidez."),
        ObjetivoAprendizaje("OA-LEN-2", "LEN", "OA 2", "Comprender textos narrativos."),
        ObjetivoAprendizaje("OA-LEN-3", "LEN", "OA 3", "Escribir textos breves con un propósito."),
        ObjetivoAprendizaje("OA-CN-1", "CN", "OA 1", "Describir necesidades de los seres vivos."),
        ObjetivoAprendizaje("OA-CN-2", "CN", "OA 2", "Observar y clasificar materiales."),
    )

    // ---- Asistencia de ejemplo: 5 días; todos presentes salvo los casos de esta tabla ----
    private val diasConAsistencia = listOf("2026-10-01", "2026-10-02", "2026-10-05", "2026-10-06", "2026-10-07")

    private val excepciones = mapOf(
        ("E3A-2" to "2026-10-01") to EstadoAsistencia.AUSENTE,
        ("E3A-2" to "2026-10-05") to EstadoAsistencia.AUSENTE,
        ("E3A-2" to "2026-10-07") to EstadoAsistencia.AUSENTE,   // 3 ausencias -> alerta en Inspectoría
        ("E3A-3" to "2026-10-01") to EstadoAsistencia.ATRASADO,
        ("E3A-6" to "2026-10-06") to EstadoAsistencia.JUSTIFICADO,
        ("E4A-5" to "2026-10-02") to EstadoAsistencia.AUSENTE,
        ("E4A-5" to "2026-10-06") to EstadoAsistencia.AUSENTE,
        ("E4A-4" to "2026-10-05") to EstadoAsistencia.ATRASADO,
    )

    val asistencias: List<RegistroAsistencia> = diasConAsistencia.flatMap { fecha ->
        estudiantes.map { estudiante ->
            RegistroAsistencia(
                id = "${estudiante.id}_$fecha",
                estudianteId = estudiante.id,
                cursoId = estudiante.cursoId,
                fecha = fecha,
                estado = excepciones[estudiante.id to fecha] ?: EstadoAsistencia.PRESENTE,
                registradoPor = if (estudiante.cursoId == "C3A") DOCENTE_MAT else DOCENTE_LEN,
                fechaHoraRegistro = "$fecha 08:05"
            )
        }
    }

    val sesiones = listOf(
        SesionClase("S1", "C3A", "MAT", "2026-10-01", "Bloque 1", listOf("OA-MAT-1"), "Conteo de 10 en 10 hasta 1.000.", DOCENTE_MAT, "2026-10-01 09:30"),
        SesionClase("S2", "C3A", "MAT", "2026-10-05", "Bloque 2", listOf("OA-MAT-2", "OA-MAT-3"), "Representación de números en la recta numérica.", DOCENTE_MAT, "2026-10-05 10:15"),
        SesionClase("S3", "C4A", "LEN", "2026-10-02", "Bloque 1", listOf("OA-LEN-1"), "Lectura en voz alta de un cuento breve.", DOCENTE_LEN, "2026-10-02 09:40"),
    )

    val evaluaciones = listOf(
        Evaluacion("EV1", "C3A", "MAT", "Prueba unidad 1", "2026-09-25"),
        Evaluacion("EV2", "C4A", "LEN", "Control de lectura", "2026-09-30"),
    )

    // EV1 queda incompleta a propósito (solo 2 notas de 8) para que UTP vea una omisión.
    val calificaciones = listOf(
        Calificacion("EV1_E3A-1", "EV1", "E3A-1", 6.2, DOCENTE_MAT),
        Calificacion("EV1_E3A-2", "EV1", "E3A-2", 4.5, DOCENTE_MAT),
    ) + estudiantes.filter { it.cursoId == "C4A" }.mapIndexed { i, estudiante ->
        Calificacion("EV2_${estudiante.id}", "EV2", estudiante.id, listOf(5.5, 6.0, 4.8, 6.7, 3.9, 5.1, 6.3, 5.8)[i], DOCENTE_LEN)
    }

    val anotaciones = listOf(
        Anotacion("AN1", "E3A-1", TipoAnotacion.POSITIVA, "Ayuda a sus compañeros durante la actividad.", "2026-09-30 10:15", DOCENTE_MAT),
        Anotacion("AN2", "E3A-2", TipoAnotacion.NEGATIVA, "Interrumpe la clase en reiteradas ocasiones.", "2026-10-01 11:40", DOCENTE_MAT),
        Anotacion("AN3", "E3A-2", TipoAnotacion.NEGATIVA, "No respeta los turnos de habla.", "2026-10-05 10:20", DOCENTE_MAT),
        Anotacion("AN4", "E4A-3", TipoAnotacion.OBSERVACION, "Se observa desmotivado durante la lectura.", "2026-10-02 09:55", DOCENTE_LEN),
    )

    val auditoria = listOf(
        RegistroAuditoria("AU1", DOCENTE_MAT, Perfil.DOCENTE, "CREAR", "Asistencia 3° Básico A, 2026-10-01", "2026-10-01 08:05"),
        RegistroAuditoria("AU2", DOCENTE_MAT, Perfil.DOCENTE, "CREAR", "Sesión de clase 3° Básico A, Matemática", "2026-10-01 09:30"),
    )
}
