package com.librompaz.app.viewmodel

import com.librompaz.app.data.remote.Feriado
import com.librompaz.app.data.remote.FeriadosApi
import com.librompaz.app.data.repository.FeriadosRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * Pruebas de la API EXTERNA de feriados: repositorio (con la interfaz Retrofit simulada) y
 * ViewModel (con el repositorio simulado y una fecha de "hoy" fija).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FeriadosViewModelTest : StringSpec({

    val feriados2026 = listOf(
        Feriado("2026-09-18", "Independencia Nacional"),
        Feriado("2026-12-08", "Inmaculada Concepción"),
        Feriado("2026-12-25", "Navidad")
    )
    val feriados2027 = listOf(Feriado("2027-01-01", "Año Nuevo"))

    var dispatcher = StandardTestDispatcher()
    beforeTest {
        dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
    }
    afterTest { Dispatchers.resetMain() }

    "el repositorio junta el año pedido y el siguiente, ordenados por fecha" {
        val api = mockk<FeriadosApi>()
        coEvery { api.feriados(2026, "CL") } returns feriados2026.reversed()
        coEvery { api.feriados(2027, "CL") } returns feriados2027

        val lista = FeriadosRepository(api).feriadosDesde(2026)

        lista.map { it.date } shouldContainExactly listOf("2026-09-18", "2026-12-08", "2026-12-25", "2027-01-01")
        coVerify(exactly = 1) { api.feriados(2026, "CL") }
    }

    "al iniciar pasa de Cargando a Exito y calcula los próximos feriados" {
        runTest(dispatcher) {
            val repo = mockk<FeriadosRepository>()
            coEvery { repo.feriadosDesde(2026) } returns feriados2026 + feriados2027

            val vm = FeriadosViewModel(repo) { "2026-11-27" }
            vm.uiState.value shouldBe FeriadosUiState.Cargando
            advanceUntilIdle()

            vm.uiState.value.shouldBeInstanceOf<FeriadosUiState.Exito>()
            vm.proximos(2).map { it.localName } shouldContainExactly listOf("Inmaculada Concepción", "Navidad")
            vm.feriadoEn("2026-12-25")?.localName shouldBe "Navidad"
            vm.feriadoEn("2026-12-26").shouldBeNull()
        }
    }

    "sin internet queda en Error y Reintentar vuelve a consultar" {
        runTest(dispatcher) {
            val repo = mockk<FeriadosRepository>()
            coEvery { repo.feriadosDesde(any()) } throws IOException("sin red")

            val vm = FeriadosViewModel(repo) { "2026-11-27" }
            advanceUntilIdle()
            vm.uiState.value.shouldBeInstanceOf<FeriadosUiState.Error>().mensaje shouldContain "Sin conexión"
            vm.proximos() shouldBe emptyList()

            coEvery { repo.feriadosDesde(any()) } returns feriados2027
            vm.cargar()
            advanceUntilIdle()
            vm.uiState.value shouldBe FeriadosUiState.Exito(feriados2027)
        }
    }
})
