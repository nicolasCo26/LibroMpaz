package com.librompaz.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.librompaz.app.data.remote.Feriado
import com.librompaz.app.data.remote.FeriadosApi
import com.librompaz.app.data.repository.FeriadosRepository
import com.librompaz.app.ui.components.TarjetaFeriados
import com.librompaz.app.viewmodel.FeriadosViewModel
import java.io.IOException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Prueba de UI de un componente aislado: la tarjeta de feriados con una API FALSA (sin internet).
 * Comprueba que la interfaz muestre el estado de éxito y el de error con su botón Reintentar.
 */
@RunWith(AndroidJUnit4::class)
class TarjetaFeriadosTest {

    @get:Rule
    val compose = createComposeRule()

    /** API falsa: devuelve la lista para 2026 (vacía para otros años) o lanza el error si es null. */
    private class ApiFalsa(var feriados: List<Feriado>?) : FeriadosApi {
        override suspend fun feriados(anio: Int, pais: String): List<Feriado> {
            val lista = feriados ?: throw IOException("sin red")
            return if (anio == 2026) lista else emptyList()
        }
    }

    @Test
    fun muestraLosProximosFeriados() {
        val api = ApiFalsa(listOf(Feriado("2026-12-25", "Navidad")))
        val viewModel = FeriadosViewModel(FeriadosRepository(api)) { "2026-11-27" }

        compose.setContent { TarjetaFeriados(viewModel) }

        compose.onNodeWithText("2026-12-25 · Navidad").assertIsDisplayed()
        Thread.sleep(1_000)
    }

    @Test
    fun sinInternetMuestraErrorYReintentarVuelveACargar() {
        val api = ApiFalsa(feriados = null)
        val viewModel = FeriadosViewModel(FeriadosRepository(api)) { "2026-11-27" }

        compose.setContent { TarjetaFeriados(viewModel) }
        compose.onNodeWithText("Sin conexión", substring = true).assertIsDisplayed()
        Thread.sleep(1_000)

        api.feriados = listOf(Feriado("2026-12-08", "Inmaculada Concepción"))
        compose.onNodeWithText("Reintentar").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("Inmaculada Concepción", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
        Thread.sleep(1_000)
    }
}
