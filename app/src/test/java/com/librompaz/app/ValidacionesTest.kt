package com.librompaz.app

import com.librompaz.app.viewmodel.parsearNota
import com.librompaz.app.viewmodel.validarAnotacion
import com.librompaz.app.viewmodel.validarEvaluacion
import com.librompaz.app.viewmodel.validarFecha
import com.librompaz.app.viewmodel.validarLogin
import com.librompaz.app.viewmodel.validarNota
import com.librompaz.app.viewmodel.validarSesion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pruebas de las reglas de validación (sin emulador: clic derecho > Run 'ValidacionesTest'). */
class ValidacionesTest {

    private val hoy = "2026-10-29"

    @Test
    fun login_camposVacios_tienenError() {
        val errores = validarLogin("", "")
        assertNotNull(errores.usuario)
        assertNotNull(errores.clave)
    }

    @Test
    fun login_correoInvalido_tieneError() {
        assertEquals("El correo no tiene un formato válido", validarLogin("docente.mat", "docente123").usuario)
    }

    @Test
    fun login_valido_noTieneErrores() {
        assertFalse(validarLogin("docente.mat@libromp.cl", "docente123").hayErrores)
    }

    @Test
    fun nota_dentroDeRango_esValida() {
        assertNull(validarNota("1,0"))
        assertNull(validarNota("5.5"))
        assertNull(validarNota("7"))
    }

    @Test
    fun nota_fueraDeRango_tieneError() {
        assertEquals("Va de 1,0 a 7,0", validarNota("0,9"))
        assertEquals("Va de 1,0 a 7,0", validarNota("7,1"))
    }

    @Test
    fun nota_conTextoODosDecimales_tieneError() {
        assertEquals("Debe ser un número", validarNota("seis"))
        assertEquals("Usa un decimal (ej: 5,5)", validarNota("5,55"))
    }

    @Test
    fun nota_enBlanco_noEsError() {
        assertNull(validarNota("  "))
    }

    @Test
    fun parsearNota_aceptaComaYPunto() {
        assertEquals(5.5, parsearNota("5,5")!!, 0.001)
        assertEquals(5.5, parsearNota("5.5")!!, 0.001)
    }

    @Test
    fun fecha_futuraOInvalida_tieneError() {
        assertEquals("La fecha no puede ser futura", validarFecha("2026-12-01", hoy))
        assertEquals("Usa el formato AAAA-MM-DD", validarFecha("2026-02-31", hoy))
        assertEquals("Usa el formato AAAA-MM-DD", validarFecha("29/10/2026", hoy))
        assertNull(validarFecha(hoy, hoy))
    }

    @Test
    fun evaluacion_sinNombre_tieneError() {
        val errores = validarEvaluacion(" ", hoy, hoy)
        assertTrue(errores.hayErrores)
        assertNotNull(errores.nombre)
        assertNull(errores.fecha)
    }

    @Test
    fun sesion_sinOaNiContenido_tieneErrores() {
        val errores = validarSesion(hoy, "Bloque 1", emptyList(), "corto", hoy)
        assertNotNull(errores.objetivos)
        assertNotNull(errores.contenido)
        assertNull(errores.bloque)
    }

    @Test
    fun anotacion_completa_noTieneErrores() {
        assertFalse(validarAnotacion(true, true, "Participa activamente en clases.").hayErrores)
        assertTrue(validarAnotacion(false, true, "Participa activamente en clases.").hayErrores)
    }
}
