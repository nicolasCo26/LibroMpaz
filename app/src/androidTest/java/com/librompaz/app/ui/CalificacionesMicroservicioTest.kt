package com.librompaz.app.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.librompaz.app.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Prueba de INTERFAZ de punta a punta con el microservicio ms-calificaciones.
 * REQUIERE los microservicios encendidos en el computador (puertos 8081 y 8082).
 *
 * Recorre: login docente -> curso -> Calificaciones -> crear una evaluación (POST) -> elegirla ->
 * eliminarla (DELETE). Deja la base del servidor igual que como estaba.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class CalificacionesMicroservicioTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun pausa(ms: Long = 700) = Thread.sleep(ms)

    @Test
    fun docenteCreaYEliminaUnaEvaluacionEnElMicroservicio() {
        val nombre = "Prueba UI ${System.currentTimeMillis() % 10_000}"

        compose.waitUntilAtLeastOneExists(hasText("Ingresar"), timeoutMillis = 15_000)
        compose.onNodeWithText("Correo institucional").performTextInput("docente.mat@libromp.cl")
        compose.onNodeWithText("Clave").performTextInput("docente123")
        compose.onNodeWithText("Ingresar").performClick()

        compose.waitUntilAtLeastOneExists(hasText("3° Básico A"), timeoutMillis = 5_000)
        compose.onNodeWithText("3° Básico A").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Calificaciones"), timeoutMillis = 5_000)
        compose.onNodeWithText("Calificaciones").performClick()

        // POST /api/evaluaciones
        compose.waitUntilAtLeastOneExists(hasText("Nueva evaluación"), timeoutMillis = 5_000)
        pausa()
        compose.onNodeWithText("Nombre del instrumento").performTextInput(nombre)
        pausa()
        compose.onNodeWithText("Crear").performClick()
        compose.waitUntilAtLeastOneExists(hasText("creada", substring = true), timeoutMillis = 10_000)
        pausa(1_000)

        // Elegirla en la lista desplegable
        compose.onNodeWithText("Elige una evaluación").performClick()
        // La opción de la lista se ve como "<nombre> (<fecha>)".
        compose.waitUntilAtLeastOneExists(hasText("$nombre (", substring = true), timeoutMillis = 5_000)
        compose.onNodeWithText("$nombre (", substring = true).performClick()
        pausa()

        // DELETE /api/evaluaciones/{id}
        compose.onNodeWithContentDescription("Eliminar evaluación").performClick()
        pausa()
        compose.onNodeWithText("Eliminar").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Evaluación eliminada."), timeoutMillis = 10_000)
        pausa(1_000)
    }
}
