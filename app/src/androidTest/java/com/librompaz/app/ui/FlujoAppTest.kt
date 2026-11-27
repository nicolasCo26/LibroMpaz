package com.librompaz.app.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
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
 * Pruebas de INTERFAZ (instrumentadas): abren la app real en el emulador y la usan como lo haría
 * una persona (escriben, tocan botones y navegan). Se ven en pantalla mientras corren.
 *
 * Ejecutar: clic derecho en esta clase > Run 'FlujoAppTest'  (o ./gradlew connectedDebugAndroidTest)
 * Las pausas cortas (pausa()) solo existen para que el recorrido se pueda seguir con la vista.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class FlujoAppTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun pausa(ms: Long = 700) = Thread.sleep(ms)

    /** Espera a que la base local esté lista (el botón muestra "Ingresar") y escribe las credenciales. */
    private fun ingresar(correo: String, clave: String) {
        compose.waitUntilAtLeastOneExists(hasText("Ingresar"), timeoutMillis = 15_000)
        compose.onNodeWithText("Correo institucional").performTextInput(correo)
        pausa()
        compose.onNodeWithText("Clave").performTextInput(clave)
        pausa()
        compose.onNodeWithText("Ingresar").performClick()
    }

    @Test
    fun loginVacioMuestraLosErroresBajoCadaCampo() {
        compose.waitUntilAtLeastOneExists(hasText("Ingresar"), timeoutMillis = 15_000)
        pausa()
        compose.onNodeWithText("Ingresar").performClick()
        pausa()
        compose.onNodeWithText("Ingresa tu correo institucional").assertIsDisplayed()
        compose.onNodeWithText("Ingresa tu clave").assertIsDisplayed()
    }

    @Test
    fun loginConClaveIncorrectaMuestraErrorGeneral() {
        ingresar("docente.mat@libromp.cl", "clave-mala")
        compose.waitUntilAtLeastOneExists(hasText("Correo o clave incorrectos"), timeoutMillis = 5_000)
        pausa()
    }

    @Test
    fun docenteRegistraLaAsistenciaDeSuCurso() {
        ingresar("docente.mat@libromp.cl", "docente123")

        // Pantalla "Mis cursos": solo sus cargas.
        compose.waitUntilAtLeastOneExists(hasText("Hola, Docente de Matemática"), timeoutMillis = 5_000)
        pausa()
        compose.onNodeWithText("3° Básico A").performClick()

        // Libro del curso -> Asistencia del día
        compose.waitUntilAtLeastOneExists(hasText("Asistencia del día"), timeoutMillis = 5_000)
        pausa()
        compose.onNodeWithText("Asistencia del día").performClick()

        compose.waitUntilAtLeastOneExists(hasText("Todos presentes"), timeoutMillis = 5_000)
        pausa()
        compose.onNodeWithText("Todos presentes").performClick()
        pausa()
        compose.onNodeWithText("Guardar asistencia").performClick()
        pausa()

        // Diálogo de confirmación: "Guardar" la primera vez, "Sobrescribir" si ya existía ese día.
        val sobrescribe = compose.onAllNodes(hasText("Sobrescribir")).fetchSemanticsNodes().isNotEmpty()
        compose.onNodeWithText(if (sobrescribe) "Sobrescribir" else "Guardar").performClick()
        compose.waitUntilAtLeastOneExists(hasText("guardada.", substring = true), timeoutMillis = 5_000)
        pausa(1_000)

        // Volver con la flecha de la barra superior.
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Hoja de vida"), timeoutMillis = 5_000)
        pausa()
    }

    @Test
    fun inspectoriaVeSuPanelConLosFeriadosDeLaApiExterna() {
        ingresar("inspectoria@libromp.cl", "inspectoria123")
        compose.waitUntilAtLeastOneExists(hasText("Panel Inspectoría"), timeoutMillis = 5_000)
        compose.onNodeWithText("Próximos feriados (sin clases)").assertIsDisplayed()
        pausa(1_500)
        compose.onNodeWithText("Alertas", substring = true).performClick()
        compose.waitUntilAtLeastOneExists(hasText("Alertas"), timeoutMillis = 5_000)
        pausa(1_000)
    }
}
