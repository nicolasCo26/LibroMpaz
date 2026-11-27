package com.librompaz.app.viewmodel

import android.app.Application
import android.net.Uri
import com.librompaz.app.data.MockData
import com.librompaz.app.data.repository.LibroMpazRepository
import com.librompaz.app.model.EstadoAsistencia
import com.librompaz.app.model.Perfil
import com.librompaz.app.model.TipoAnotacion
import com.librompaz.app.model.Usuario
import com.librompaz.app.recursosnativos.ExportadorCsv
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response

/**
 * Prueba unitaria del VIEWMODEL con Kotest + MockK + kotlinx-coroutines-test.
 * Se prueba el ViewModel REAL; lo único simulado es el repositorio (no hay Room ni red).
 * Los datos de prueba son los mismos datos ficticios de la app (MockData).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibroMpazViewModelTest : StringSpec({

    val docenteMat = MockData.usuarios[0]       // Matemática en 3°A y 4°A
    val inspectoria = MockData.usuarios[3]
    val direccion = MockData.usuarios[4]
    val ev1 = MockData.evaluaciones[0]           // C3A, MAT
    val an1 = MockData.anotaciones[0]            // firmada por el docente de Matemática
    val an4 = MockData.anotaciones[3]            // firmada por el docente de Lenguaje

    // viewModelScope usa Dispatchers.Main, que no existe fuera de Android: se reemplaza por uno de prueba.
    var dispatcher = StandardTestDispatcher()
    lateinit var repo: LibroMpazRepository

    beforeTest {
        dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        repo = mockk(relaxed = true)
        every { repo.cursos } returns flowOf(MockData.cursos)
        every { repo.asignaturas } returns flowOf(MockData.asignaturas)
        every { repo.estudiantes } returns flowOf(MockData.estudiantes)
        every { repo.objetivos } returns flowOf(MockData.objetivos)
        every { repo.cargas } returns flowOf(MockData.cargas)
        every { repo.asistencias } returns flowOf(MockData.asistencias)
        every { repo.sesiones } returns flowOf(MockData.sesiones)
        every { repo.evaluaciones } returns flowOf(MockData.evaluaciones)
        every { repo.calificaciones } returns flowOf(MockData.calificaciones)
        every { repo.anotaciones } returns flowOf(MockData.anotaciones)
        every { repo.auditoria } returns flowOf(MockData.auditoria)
        every { repo.cargasDe(any()) } answers { flowOf(MockData.cargas.filter { it.docenteId == firstArg<String>() }) }
        MockData.usuarios.forEach { u -> coEvery { repo.buscarUsuario(u.usuario) } returns u }
    }
    afterTest { Dispatchers.resetMain() }

    fun nuevoViewModel() = LibroMpazViewModel(mockk<Application>(relaxed = true), repo)

    /** Crea el ViewModel e inicia sesión con el usuario indicado. */
    suspend fun TestScope.conSesion(usuario: Usuario): LibroMpazViewModel {
        val vm = nuevoViewModel()
        vm.onUsuarioChange(usuario.usuario)
        vm.onClaveChange(usuario.clave)
        vm.ingresar {}
        advanceUntilIdle()
        return vm
    }

    fun http(codigo: Int) = HttpException(Response.error<Any>(codigo, "{}".toResponseBody()))

    // ------------------------------- Inicio y login -------------------------------

    "al iniciar pobla la base, queda listo y sincroniza con los microservicios" {
        runTest(dispatcher) {
            val vm = nuevoViewModel()
            vm.datosListos.value shouldBe false
            advanceUntilIdle()
            vm.datosListos.value shouldBe true
            vm.conectado.value shouldBe true
            coVerify { repo.poblarSiVacia() }
            coVerify { repo.sincronizar() }
        }
    }

    "si los microservicios están apagados queda sin conexión" {
        runTest(dispatcher) {
            coEvery { repo.sincronizar() } throws IOException("sin red")
            val vm = nuevoViewModel()
            advanceUntilIdle()
            vm.conectado.value shouldBe false
            vm.sincronizando.value shouldBe false
        }
    }

    "login con campos vacíos muestra los errores y no consulta el repositorio" {
        runTest(dispatcher) {
            val vm = nuevoViewModel()
            vm.ingresar {}
            advanceUntilIdle()
            vm.login.value.errores.usuario.shouldNotBeNull()
            vm.login.value.errores.clave.shouldNotBeNull()
            coVerify(exactly = 0) { repo.buscarUsuario(any()) }
        }
    }

    "login con clave incorrecta muestra un error genérico" {
        runTest(dispatcher) {
            val vm = nuevoViewModel()
            vm.onUsuarioChange(docenteMat.usuario)
            vm.onClaveChange("otra-clave")
            vm.ingresar {}
            advanceUntilIdle()
            vm.login.value.errores.general shouldBe "Correo o clave incorrectos"
            vm.usuarioActivo.value.shouldBeNull()
        }
    }

    "login correcto deja el usuario activo, limpia la clave y avisa el perfil" {
        runTest(dispatcher) {
            val vm = nuevoViewModel()
            var perfil: Perfil? = null
            vm.onUsuarioChange(docenteMat.usuario)
            vm.onClaveChange(docenteMat.clave)
            vm.ingresar { perfil = it }
            advanceUntilIdle()
            perfil shouldBe Perfil.DOCENTE
            vm.usuarioActivo.value shouldBe docenteMat
            vm.login.value.clave shouldBe ""
        }
    }

    "el docente solo ve sus cargas y logout limpia la sesión" {
        runTest(dispatcher) {
            val vm = conSesion(docenteMat)
            vm.misCargas.value.map { it.id } shouldContainExactly listOf("CD1", "CD2")
            vm.seleccionarCurso("C3A", "MAT")
            vm.logout()
            vm.usuarioActivo.value.shouldBeNull()
            vm.cursoSeleccionadoId.value.shouldBeNull()
        }
    }

    // ------------------------------- Datos de referencia -------------------------------

    "las consultas de apoyo filtran por curso, asignatura y estudiante" {
        runTest(dispatcher) {
            val vm = nuevoViewModel()
            advanceUntilIdle()
            vm.cursoPorId("C3A")?.nombre shouldBe "3° Básico A"
            vm.asignaturaPorId("MAT")?.nombre shouldBe "Matemática"
            vm.estudiantePorId("E3A-1")?.nombreFicticio shouldBe "Estudiante 3A-01"
            vm.estudiantesDelCurso("C4A") shouldHaveSize 8
            vm.objetivosDe("MAT") shouldHaveSize 4
            vm.sesionesDe("C3A", "MAT") shouldHaveSize 2
            vm.oasTrabajados("C3A", "MAT") shouldBe setOf("OA-MAT-1", "OA-MAT-2", "OA-MAT-3")
            vm.evaluacionesDe("C3A", "MAT").map { it.id } shouldContainExactly listOf("EV1")
            vm.calificacionesDe("EV1") shouldHaveSize 2
            vm.anotacionesDe("E3A-2") shouldHaveSize 2
            vm.anotacionesPorTipo(TipoAnotacion.NEGATIVA) shouldHaveSize 2
            vm.anotacionesPorTipo(null) shouldHaveSize 4
        }
    }

    "las métricas derivadas se calculan con los datos del repositorio" {
        runTest(dispatcher) {
            val vm = nuevoViewModel()
            advanceUntilIdle()
            vm.porcentajesAsistencia.value shouldContainKey "C3A"
            vm.alertasInasistencia.value.map { it.first.id } shouldContainExactly listOf("E3A-2")
            vm.alertasAnotaciones.value.map { it.id }.toSet() shouldBe setOf("AN2", "AN3")
            vm.desfases.value.isNotEmpty() shouldBe true
            vm.resumenCursos.value shouldHaveSize 2
            vm.auditoria.value shouldHaveSize 2
        }
    }

    // ------------------------------- Asistencia y sesiones -------------------------------

    "inspectoría puede guardar la asistencia de cualquier curso" {
        runTest(dispatcher) {
            val vm = conSesion(inspectoria)
            var listo = false
            vm.guardarAsistencia("C4A", "2026-10-09", mapOf("E4A-1" to EstadoAsistencia.AUSENTE)) { listo = true }
            advanceUntilIdle()
            listo shouldBe true
            coVerify { repo.guardarAsistencia(inspectoria, "C4A", "2026-10-09", any(), any()) }
        }
    }

    "un docente no puede guardar asistencia de un curso que no tiene asignado" {
        runTest(dispatcher) {
            coEvery { repo.asistenciaDe("C3A", "2026-10-01") } returns MockData.asistencias.filter { it.cursoId == "C3A" && it.fecha == "2026-10-01" }
            val sinCarga = Usuario("U9", "Docente nuevo", "nuevo@libromp.cl", "docente123", Perfil.DOCENTE)
            coEvery { repo.buscarUsuario(sinCarga.usuario) } returns sinCarga
            val vm = conSesion(sinCarga)
            vm.guardarAsistencia("C3A", "2026-10-09", mapOf("E3A-1" to EstadoAsistencia.PRESENTE)) {}
            advanceUntilIdle()
            coVerify(exactly = 0) { repo.guardarAsistencia(any(), any(), any(), any(), any()) }
            vm.asistenciaGuardada("C3A", "2026-10-01")["E3A-3"] shouldBe EstadoAsistencia.ATRASADO
            vm.validarFechaAsistencia("2999-01-01").shouldNotBeNull()
        }
    }

    "guardarSesion() valida y solo guarda si no hay errores" {
        runTest(dispatcher) {
            val vm = conSesion(docenteMat)
            vm.guardarSesion("C3A", "MAT", "2026-10-09", "", emptyList(), "corto") {}.hayErrores shouldBe true
            var listo = false
            val errores = vm.guardarSesion("C3A", "MAT", "2026-10-09", "Bloque 1", listOf("OA-MAT-4"), "Figuras 2D y 3D del entorno.") { listo = true }
            advanceUntilIdle()
            errores.hayErrores shouldBe false
            listo shouldBe true
        }
    }

    // ------------------------------- Calificaciones (microservicio) -------------------------------

    "crearEvaluacion() con datos inválidos devuelve los errores y no llama al servidor" {
        runTest(dispatcher) {
            val vm = conSesion(docenteMat)
            val errores = vm.crearEvaluacion("C3A", "MAT", "ab", "2026-10-10") {}
            advanceUntilIdle()
            errores.nombre.shouldNotBeNull()
            coVerify(exactly = 0) { repo.crearEvaluacion(any(), any(), any(), any(), any(), any()) }
        }
    }

    "crearEvaluacion() válida llama al repositorio y avisa al terminar" {
        runTest(dispatcher) {
            val vm = conSesion(docenteMat)
            var listo = false
            vm.crearEvaluacion("C3A", "MAT", "  Prueba 2  ", "2026-10-10") { listo = true }
            advanceUntilIdle()
            listo shouldBe true
            coVerify { repo.crearEvaluacion(docenteMat, "C3A", "MAT", "Prueba 2", "2026-10-10", any()) }
        }
    }

    "si el microservicio no responde, muestra el error y no avisa éxito" {
        runTest(dispatcher) {
            coEvery { repo.crearEvaluacion(any(), any(), any(), any(), any(), any()) } throws IOException("sin red")
            val vm = conSesion(docenteMat)
            var listo = false
            vm.crearEvaluacion("C3A", "MAT", "Prueba 2", "2026-10-10") { listo = true }
            advanceUntilIdle()
            listo shouldBe false
            vm.conectado.value shouldBe false
            vm.errorServidor.value!! shouldContain "Sin conexión"
            vm.limpiarErrorServidor()
            vm.errorServidor.value.shouldBeNull()
        }
    }

    "actualizarEvaluacion() envía la evaluación corregida" {
        runTest(dispatcher) {
            val vm = conSesion(docenteMat)
            vm.actualizarEvaluacion(ev1, "", "2026-10-10") {}.nombre.shouldNotBeNull()
            vm.actualizarEvaluacion(ev1, "Prueba corregida", "2026-09-26") {}
            advanceUntilIdle()
            coVerify { repo.actualizarEvaluacion(docenteMat, ev1.copy(nombreInstrumento = "Prueba corregida", fecha = "2026-09-26"), any()) }
        }
    }

    "guardarCalificaciones() rechaza notas fuera de rango y envía las válidas como número" {
        runTest(dispatcher) {
            val vm = conSesion(docenteMat)
            vm.guardarCalificaciones(ev1, mapOf("E3A-1" to "8,0")) {} shouldContainKey "E3A-1"
            vm.errorDeNota("6,5").shouldBeNull()

            val errores = vm.guardarCalificaciones(ev1, mapOf("E3A-1" to "6,5", "E3A-3" to "5.0", "E3A-2" to "")) {}
            advanceUntilIdle()
            errores shouldBe emptyMap()
            coVerify { repo.guardarCalificaciones(docenteMat, "EV1", mapOf("E3A-1" to 6.5, "E3A-3" to 5.0), any()) }
        }
    }

    "eliminarEvaluacion() con error 404 deja el mensaje del servidor" {
        runTest(dispatcher) {
            coEvery { repo.eliminarEvaluacion(any(), any(), any()) } throws http(404)
            val vm = conSesion(docenteMat)
            vm.eliminarEvaluacion(ev1) {}
            advanceUntilIdle()
            vm.errorServidor.value!! shouldContain "404"
        }
    }

    // ------------------------------- Hoja de vida (microservicio) -------------------------------

    "registrarAnotacion() llama al repositorio solo si es válida y el curso es del docente" {
        runTest(dispatcher) {
            val vm = conSesion(docenteMat)
            val estudiante = MockData.estudiantes.first()
            vm.validarNuevaAnotacion(null, null, "").hayErrores shouldBe true
            var listo = false
            vm.registrarAnotacion(estudiante, TipoAnotacion.POSITIVA, "Participa activamente en clase.") { listo = true }
            advanceUntilIdle()
            listo shouldBe true
            coVerify { repo.registrarAnotacion(docenteMat, estudiante.id, TipoAnotacion.POSITIVA, any(), any()) }
        }
    }

    "solo quien firmó puede corregir o eliminar una anotación" {
        runTest(dispatcher) {
            val vm = conSesion(docenteMat)
            vm.puedeModificar(an1) shouldBe true
            vm.puedeModificar(an4) shouldBe false

            vm.actualizarAnotacion(an4, TipoAnotacion.NEGATIVA, "Texto de otra persona.") {}
            vm.eliminarAnotacion(an4) {}
            advanceUntilIdle()
            coVerify(exactly = 0) { repo.actualizarAnotacion(any(), any(), any(), any(), any()) }
            coVerify(exactly = 0) { repo.eliminarAnotacion(any(), any(), any()) }
        }
    }

    "el firmante corrige y elimina su anotación" {
        runTest(dispatcher) {
            val vm = conSesion(docenteMat)
            vm.actualizarAnotacion(an1, null, "corto") {}.hayErrores shouldBe true
            vm.actualizarAnotacion(an1, TipoAnotacion.OBSERVACION, "  Texto corregido por el docente.  ") {}
            vm.eliminarAnotacion(an1) {}
            advanceUntilIdle()
            coVerify { repo.actualizarAnotacion(docenteMat, an1, TipoAnotacion.OBSERVACION, "Texto corregido por el docente.", any()) }
            coVerify { repo.eliminarAnotacion(docenteMat, an1, any()) }
        }
    }

    "un 403 del microservicio se muestra como no autorizado" {
        runTest(dispatcher) {
            coEvery { repo.eliminarAnotacion(any(), any(), any()) } throws http(403)
            val vm = conSesion(docenteMat)
            vm.eliminarAnotacion(an1) {}
            advanceUntilIdle()
            vm.errorServidor.value!! shouldContain "403"
            vm.conectado.value shouldBe true   // el servidor sí respondió
        }
    }

    // ------------------------------- Dirección: recurso nativo -------------------------------

    "solo Dirección puede exportar el reporte y la exportación queda auditada" {
        runTest(dispatcher) {
            mockkObject(ExportadorCsv)
            coEvery { ExportadorCsv.escribir(any(), any(), any()) } returns true
            val uri = mockk<Uri>()

            var resultadoDocente: Boolean? = null
            conSesion(docenteMat).exportarReporte(uri) { resultadoDocente = it }
            resultadoDocente shouldBe false

            var resultado: Boolean? = null
            conSesion(direccion).exportarReporte(uri) { resultado = it }
            advanceUntilIdle()
            resultado shouldBe true
            coVerify { repo.registrarExportacion(direccion, any()) }
            unmockkObject(ExportadorCsv)
        }
    }

    "la lista de evaluaciones se actualiza sola cuando cambia el Flow de Room" {
        runTest(dispatcher) {
            val flujo = MutableStateFlow(MockData.evaluaciones)
            every { repo.evaluaciones } returns flujo
            val vm = nuevoViewModel()
            advanceUntilIdle()
            vm.evaluaciones.value shouldHaveSize 2
            flujo.value = MockData.evaluaciones.take(1)
            advanceUntilIdle()
            vm.evaluaciones.value shouldHaveSize 1
        }
    }
})
