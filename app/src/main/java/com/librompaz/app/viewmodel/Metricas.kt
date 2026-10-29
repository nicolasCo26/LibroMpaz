package com.librompaz.app.viewmodel

import com.librompaz.app.model.Anotacion
import com.librompaz.app.model.Asignatura
import com.librompaz.app.model.Calificacion
import com.librompaz.app.model.CargaDocente
import com.librompaz.app.model.Curso
import com.librompaz.app.model.EstadoAsistencia
import com.librompaz.app.model.Estudiante
import com.librompaz.app.model.Evaluacion
import com.librompaz.app.model.RegistroAsistencia
import com.librompaz.app.model.ResumenCurso
import com.librompaz.app.model.SesionClase
import com.librompaz.app.model.TipoAnotacion

// =====================================================================================
// Cálculos de las vistas de supervisión (UTP, Inspectoría y Dirección), como funciones puras:
// reciben listas y devuelven el resultado. El ViewModel las aplica sobre los Flow de Room, y
// también se pueden probar con JUnit (ver app/src/test/.../MetricasTest.kt).
// =====================================================================================

/** Desde cuántas ausencias (no justificadas) un estudiante aparece en las alertas de Inspectoría. */
const val UMBRAL_INASISTENCIAS = 3

/** Desde cuántas anotaciones negativas un estudiante requiere intervención de Inspectoría. */
const val UMBRAL_ANOTACIONES_NEGATIVAS = 2

/**
 * Porcentaje de asistencia (0 a 100) de un conjunto de registros: presentes + atrasados sobre
 * el total. Ausente y justificado cuentan como inasistencia. null si no hay registros.
 */
fun calcularPorcentajeAsistencia(registros: List<RegistroAsistencia>): Int? {
    if (registros.isEmpty()) return null
    val asistieron = registros.count {
        it.estado == EstadoAsistencia.PRESENTE || it.estado == EstadoAsistencia.ATRASADO
    }
    return Math.round(asistieron * 100.0 / registros.size).toInt()
}

/** Porcentaje de asistencia de cada curso (cursoId -> %). */
fun calcularPorcentajesPorCurso(asistencias: List<RegistroAsistencia>): Map<String, Int> =
    asistencias.groupBy { it.cursoId }
        .mapNotNull { (cursoId, registros) -> calcularPorcentajeAsistencia(registros)?.let { cursoId to it } }
        .toMap()

/** Estudiantes con UMBRAL_INASISTENCIAS o más ausencias, de mayor a menor. */
fun calcularInasistenciasReiteradas(
    asistencias: List<RegistroAsistencia>,
    estudiantes: List<Estudiante>
): List<Pair<Estudiante, Int>> {
    val ausenciasPorEstudiante = asistencias
        .filter { it.estado == EstadoAsistencia.AUSENTE }
        .groupingBy { it.estudianteId }
        .eachCount()
    return estudiantes
        .mapNotNull { estudiante -> ausenciasPorEstudiante[estudiante.id]?.let { estudiante to it } }
        .filter { (_, ausencias) -> ausencias >= UMBRAL_INASISTENCIAS }
        .sortedByDescending { it.second }
}

/** Anotaciones negativas de los estudiantes que acumulan UMBRAL_ANOTACIONES_NEGATIVAS o más. */
fun calcularAnotacionesIntervencion(anotaciones: List<Anotacion>): List<Anotacion> {
    val negativas = anotaciones.filter { it.tipo == TipoAnotacion.NEGATIVA }
    val estudiantesEnRiesgo = negativas
        .groupingBy { it.estudianteId }
        .eachCount()
        .filterValues { it >= UMBRAL_ANOTACIONES_NEGATIVAS }
        .keys
    return negativas.filter { it.estudianteId in estudiantesEnRiesgo }
}

/** OA ya trabajados en un curso y asignatura, según las sesiones registradas. */
fun calcularOasTrabajados(sesiones: List<SesionClase>, cursoId: String?, asignaturaId: String?): Set<String> =
    sesiones
        .filter { it.cursoId == cursoId && it.asignaturaId == asignaturaId }
        .flatMap { it.oaIds }
        .toSet()

/**
 * Desfases u omisiones en el registro de notas (RF-09):
 *  - cargas (curso + asignatura) que todavía no tienen ninguna evaluación;
 *  - evaluaciones en las que faltan notas de algunos estudiantes del curso.
 */
fun calcularDesfases(
    cargas: List<CargaDocente>,
    evaluaciones: List<Evaluacion>,
    calificaciones: List<Calificacion>,
    estudiantes: List<Estudiante>,
    cursos: List<Curso>,
    asignaturas: List<Asignatura>
): List<String> {
    fun nombreCurso(id: String) = cursos.find { it.id == id }?.nombre ?: id
    fun nombreAsignatura(id: String) = asignaturas.find { it.id == id }?.nombre ?: id

    val sinEvaluaciones = cargas
        .filter { carga -> evaluaciones.none { it.cursoId == carga.cursoId && it.asignaturaId == carga.asignaturaId } }
        .map { "${nombreCurso(it.cursoId)} · ${nombreAsignatura(it.asignaturaId)}: sin evaluaciones registradas" }

    val notasIncompletas = evaluaciones.mapNotNull { evaluacion ->
        val totalCurso = estudiantes.count { it.cursoId == evaluacion.cursoId }
        val ingresadas = calificaciones.count { it.evaluacionId == evaluacion.id }
        val faltan = totalCurso - ingresadas
        if (faltan > 0) {
            "${evaluacion.nombreInstrumento} (${nombreCurso(evaluacion.cursoId)} · " +
                "${nombreAsignatura(evaluacion.asignaturaId)}): faltan $faltan de $totalCurso notas"
        } else {
            null
        }
    }
    return sinEvaluaciones + notasIncompletas
}

/** Métricas consolidadas de cada curso para el panel de Dirección (RF-12). */
fun calcularResumenPorCurso(
    cursos: List<Curso>,
    asistencias: List<RegistroAsistencia>,
    sesiones: List<SesionClase>,
    evaluaciones: List<Evaluacion>,
    calificaciones: List<Calificacion>,
    anotaciones: List<Anotacion>,
    estudiantes: List<Estudiante>
): List<ResumenCurso> = cursos.map { curso ->
    val evaluacionesDelCurso = evaluaciones.filter { it.cursoId == curso.id }.map { it.id }.toSet()
    val estudiantesDelCurso = estudiantes.filter { it.cursoId == curso.id }.map { it.id }.toSet()
    ResumenCurso(
        cursoId = curso.id,
        porcentajeAsistencia = calcularPorcentajeAsistencia(asistencias.filter { it.cursoId == curso.id }),
        sesionesRegistradas = sesiones.count { it.cursoId == curso.id },
        calificacionesRegistradas = calificaciones.count { it.evaluacionId in evaluacionesDelCurso },
        anotacionesNegativas = anotaciones.count {
            it.tipo == TipoAnotacion.NEGATIVA && it.estudianteId in estudiantesDelCurso
        }
    )
}

/** Contenido del reporte CSV que exporta Dirección: solo métricas por curso, sin datos de estudiantes. */
fun generarReporteCsv(resumen: List<ResumenCurso>, cursos: List<Curso>, generadoPor: String, fechaHora: String): String =
    buildString {
        appendLine("Reporte Libro MPAZ;Generado por $generadoPor;$fechaHora")
        appendLine("Curso;Asistencia (%);Clases registradas;Notas registradas;Anotaciones negativas")
        resumen.forEach { fila ->
            val curso = cursos.find { it.id == fila.cursoId }?.nombre ?: fila.cursoId
            appendLine(
                listOf(
                    curso,
                    fila.porcentajeAsistencia?.toString() ?: "-",
                    fila.sesionesRegistradas?.toString() ?: "-",
                    fila.calificacionesRegistradas?.toString() ?: "-",
                    fila.anotacionesNegativas?.toString() ?: "-"
                ).joinToString(";")
            )
        }
    }
