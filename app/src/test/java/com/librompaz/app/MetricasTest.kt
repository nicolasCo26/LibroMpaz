package com.librompaz.app

import com.librompaz.app.data.MockData
import com.librompaz.app.model.Anotacion
import com.librompaz.app.model.EstadoAsistencia
import com.librompaz.app.model.RegistroAsistencia
import com.librompaz.app.model.TipoAnotacion
import com.librompaz.app.viewmodel.calcularAnotacionesIntervencion
import com.librompaz.app.viewmodel.calcularDesfases
import com.librompaz.app.viewmodel.calcularInasistenciasReiteradas
import com.librompaz.app.viewmodel.calcularPorcentajeAsistencia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pruebas de los cálculos de UTP, Inspectoría y Dirección con los datos ficticios de MockData. */
class MetricasTest {

    private fun registro(estudiante: String, fecha: String, estado: EstadoAsistencia) =
        RegistroAsistencia("${estudiante}_$fecha", estudiante, "C3A", fecha, estado, "Docente", "$fecha 08:00")

    @Test
    fun porcentaje_cuentaAtrasadoComoAsistencia() {
        val registros = listOf(
            registro("E1", "2026-10-01", EstadoAsistencia.PRESENTE),
            registro("E2", "2026-10-01", EstadoAsistencia.ATRASADO),
            registro("E3", "2026-10-01", EstadoAsistencia.AUSENTE),
            registro("E4", "2026-10-01", EstadoAsistencia.JUSTIFICADO),
        )
        assertEquals(50, calcularPorcentajeAsistencia(registros))
    }

    @Test
    fun porcentaje_sinRegistros_esNull() {
        assertNull(calcularPorcentajeAsistencia(emptyList()))
    }

    @Test
    fun inasistenciasReiteradas_detectaAlEstudianteConTresAusencias() {
        val alertas = calcularInasistenciasReiteradas(MockData.asistencias, MockData.estudiantes)
        assertEquals(listOf("E3A-2"), alertas.map { it.first.id })
        assertEquals(3, alertas.first().second)
    }

    @Test
    fun intervencion_requiereDosNegativasDelMismoEstudiante() {
        val anotaciones = listOf(
            Anotacion("A1", "E1", TipoAnotacion.NEGATIVA, "x", "2026-10-01 10:00", "D"),
            Anotacion("A2", "E1", TipoAnotacion.NEGATIVA, "y", "2026-10-02 10:00", "D"),
            Anotacion("A3", "E2", TipoAnotacion.NEGATIVA, "z", "2026-10-02 10:00", "D"),
            Anotacion("A4", "E2", TipoAnotacion.POSITIVA, "w", "2026-10-03 10:00", "D"),
        )
        assertEquals(setOf("E1"), calcularAnotacionesIntervencion(anotaciones).map { it.estudianteId }.toSet())
    }

    @Test
    fun desfases_detectaNotasFaltantesYCargasSinEvaluacion() {
        val desfases = calcularDesfases(
            MockData.cargas, MockData.evaluaciones, MockData.calificaciones,
            MockData.estudiantes, MockData.cursos, MockData.asignaturas
        )
        assertTrue(desfases.any { it.contains("faltan 6 de 8 notas") })
        assertTrue(desfases.any { it.contains("4° Básico A · Matemática: sin evaluaciones") })
    }
}
