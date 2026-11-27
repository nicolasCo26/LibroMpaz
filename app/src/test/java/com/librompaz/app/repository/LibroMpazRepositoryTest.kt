package com.librompaz.app.repository

import com.librompaz.app.data.local.AcademicoDao
import com.librompaz.app.data.local.AnotacionDao
import com.librompaz.app.data.local.AsistenciaDao
import com.librompaz.app.data.local.AuditoriaDao
import com.librompaz.app.data.local.CalificacionDao
import com.librompaz.app.data.local.SesionDao
import com.librompaz.app.data.local.UsuarioDao
import com.librompaz.app.data.remote.AnotacionDto
import com.librompaz.app.data.remote.AnotacionesApi
import com.librompaz.app.data.remote.CalificacionesApi
import com.librompaz.app.data.remote.NotaDto
import com.librompaz.app.data.remote.PlanillaNotas
import com.librompaz.app.data.repository.LibroMpazRepository
import com.librompaz.app.model.Anotacion
import com.librompaz.app.model.Calificacion
import com.librompaz.app.model.EstadoAsistencia
import com.librompaz.app.model.Evaluacion
import com.librompaz.app.model.Perfil
import com.librompaz.app.model.RegistroAsistencia
import com.librompaz.app.model.RegistroAuditoria
import com.librompaz.app.model.TipoAnotacion
import com.librompaz.app.model.Usuario
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.slot
import java.io.IOException
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response

/**
 * Prueba unitaria del REPOSITORIO con Kotest + MockK.
 * Se prueba la clase real; se simulan (mock) los DAO de Room y las interfaces Retrofit de los
 * microservicios, así que no hay base de datos ni llamadas de red. La transacción de Room se
 * reemplaza por una función que ejecuta el bloque directamente.
 */
class LibroMpazRepositoryTest : StringSpec({

    val docente = Usuario("U1", "Docente de Matemática", "docente.mat@libromp.cl", "docente123", Perfil.DOCENTE)
    val evaluacion = Evaluacion("EV1", "C3A", "MAT", "Prueba unidad 1", "2026-09-25")
    val anotacion = Anotacion("AN1", "E3A-1", TipoAnotacion.POSITIVA, "Ayuda a sus compañeros.", "2026-09-30 10:15:00", docente.nombre)

    lateinit var usuarioDao: UsuarioDao
    lateinit var academicoDao: AcademicoDao
    lateinit var asistenciaDao: AsistenciaDao
    lateinit var sesionDao: SesionDao
    lateinit var calificacionDao: CalificacionDao
    lateinit var anotacionDao: AnotacionDao
    lateinit var auditoriaDao: AuditoriaDao
    lateinit var calificacionesApi: CalificacionesApi
    lateinit var anotacionesApi: AnotacionesApi
    lateinit var repo: LibroMpazRepository

    // Respuesta de error de Retrofit con el código indicado (por ejemplo 403 o 500).
    fun <T> error(codigo: Int): Response<T> = Response.error(codigo, "{}".toResponseBody())

    beforeTest {
        // relaxed = true: las funciones no configuradas no hacen nada (no lanzan error).
        usuarioDao = mockk(relaxed = true)
        academicoDao = mockk(relaxed = true)
        asistenciaDao = mockk(relaxed = true)
        sesionDao = mockk(relaxed = true)
        calificacionDao = mockk(relaxed = true)
        anotacionDao = mockk(relaxed = true)
        auditoriaDao = mockk(relaxed = true)
        calificacionesApi = mockk()
        anotacionesApi = mockk()
        repo = LibroMpazRepository(
            usuarioDao, academicoDao, asistenciaDao, sesionDao, calificacionDao, anotacionDao, auditoriaDao,
            calificacionesApi, anotacionesApi, enTransaccion = { bloque -> bloque() }
        )
    }

    "sincronizar() reemplaza la copia local y descarta datos con claves foráneas inválidas" {
        val desconocida = Evaluacion("EVX", "C9Z", "MAT", "Curso que no existe", "2026-09-25")
        coEvery { calificacionesApi.evaluaciones() } returns listOf(evaluacion, desconocida)
        coEvery { calificacionesApi.calificaciones() } returns listOf(
            Calificacion("EV1_E3A-1", "EV1", "E3A-1", 6.2, docente.nombre),
            Calificacion("EVX_E3A-1", "EVX", "E3A-1", 5.0, docente.nombre)
        )
        coEvery { anotacionesApi.anotaciones() } returns listOf(anotacion, anotacion.copy(id = "AN9", estudianteId = "NADIE"))
        coEvery { academicoDao.idsCursos() } returns listOf("C3A")
        coEvery { academicoDao.idsAsignaturas() } returns listOf("MAT")
        coEvery { academicoDao.idsEstudiantes() } returns listOf("E3A-1")

        repo.sincronizar()

        coVerifyOrder {
            calificacionDao.eliminarTodasLasEvaluaciones()
            calificacionDao.insertarEvaluaciones(listOf(evaluacion))
            calificacionDao.guardarNotas(listOf(Calificacion("EV1_E3A-1", "EV1", "E3A-1", 6.2, docente.nombre)))
            anotacionDao.eliminarTodas()
            anotacionDao.insertarTodas(listOf(anotacion))
        }
    }

    "sincronizar() sin red lanza IOException y no toca la copia local" {
        coEvery { calificacionesApi.evaluaciones() } throws IOException("sin red")

        shouldThrow<IOException> { repo.sincronizar() }
        coVerify(exactly = 0) { calificacionDao.eliminarTodasLasEvaluaciones() }
    }

    "crearEvaluacion() hace POST y guarda en Room lo que respondió el microservicio" {
        val enviada = slot<Evaluacion>()
        coEvery { calificacionesApi.crearEvaluacion(capture(enviada)) } answers { enviada.captured }

        repo.crearEvaluacion(docente, "C3A", "MAT", "Prueba 2", "2026-10-10", "detalle")

        enviada.captured.nombreInstrumento shouldBe "Prueba 2"
        enviada.captured.id.length shouldBe 36   // UUID generado por la app
        coVerify { calificacionDao.insertarEvaluacion(enviada.captured) }
        coVerify { auditoriaDao.insertar(match { it.accion == "CREAR" && it.usuario == docente.nombre }) }
    }

    "si el POST falla no se guarda nada en Room" {
        coEvery { calificacionesApi.crearEvaluacion(any()) } throws IOException("sin red")

        shouldThrow<IOException> { repo.crearEvaluacion(docente, "C3A", "MAT", "Prueba 2", "2026-10-10", "detalle") }
        coVerify(exactly = 0) { calificacionDao.insertarEvaluacion(any()) }
        coVerify(exactly = 0) { auditoriaDao.insertar(any()) }
    }

    "actualizarEvaluacion() hace PUT y actualiza la copia local" {
        val editada = evaluacion.copy(nombreInstrumento = "Prueba corregida")
        coEvery { calificacionesApi.actualizarEvaluacion("EV1", editada) } returns editada

        repo.actualizarEvaluacion(docente, editada, "detalle")

        coVerify { calificacionDao.actualizarEvaluacion(editada) }
        coVerify { auditoriaDao.insertar(match { it.accion == "MODIFICAR" }) }
    }

    "eliminarEvaluacion() con 204 borra la copia local" {
        coEvery { calificacionesApi.eliminarEvaluacion("EV1") } returns Response.success(204, Unit)

        repo.eliminarEvaluacion(docente, evaluacion, "detalle")

        coVerify { calificacionDao.eliminarEvaluacion(evaluacion) }
        coVerify { auditoriaDao.insertar(match { it.accion == "ELIMINAR" }) }
    }

    "eliminarEvaluacion() con 404 igual borra la copia local (ya no existía en el servidor)" {
        coEvery { calificacionesApi.eliminarEvaluacion("EV1") } returns error(404)

        repo.eliminarEvaluacion(docente, evaluacion, "detalle")

        coVerify { calificacionDao.eliminarEvaluacion(evaluacion) }
    }

    "eliminarEvaluacion() con 500 lanza HttpException y conserva la copia local" {
        coEvery { calificacionesApi.eliminarEvaluacion("EV1") } returns error(500)

        val e = shouldThrow<HttpException> { repo.eliminarEvaluacion(docente, evaluacion, "detalle") }
        e.code() shouldBe 500
        coVerify(exactly = 0) { calificacionDao.eliminarEvaluacion(any()) }
    }

    "guardarCalificaciones() envía la planilla completa y reemplaza las notas locales" {
        val planilla = slot<PlanillaNotas>()
        val respuesta = listOf(Calificacion("EV1_E3A-1", "EV1", "E3A-1", 6.5, docente.nombre))
        coEvery { calificacionDao.notasDe("EV1") } returns emptyList()
        coEvery { calificacionesApi.guardarNotas("EV1", capture(planilla)) } returns respuesta

        repo.guardarCalificaciones(docente, "EV1", mapOf("E3A-1" to 6.5), "detalle")

        planilla.captured shouldBe PlanillaNotas(docente.nombre, listOf(NotaDto("E3A-1", 6.5)))
        coVerifyOrder {
            calificacionDao.eliminarNotasDe("EV1")
            calificacionDao.guardarNotas(respuesta)
        }
        coVerify { auditoriaDao.insertar(match { it.accion == "CREAR" }) }
    }

    "guardarCalificaciones() sobre notas existentes queda auditado como MODIFICAR" {
        coEvery { calificacionDao.notasDe("EV1") } returns listOf(Calificacion("EV1_E3A-1", "EV1", "E3A-1", 4.0, docente.nombre))
        coEvery { calificacionesApi.guardarNotas("EV1", any()) } returns emptyList()

        repo.guardarCalificaciones(docente, "EV1", emptyMap(), "detalle")

        coVerify { auditoriaDao.insertar(match { it.accion == "MODIFICAR" }) }
    }

    "registrarAnotacion() hace POST con la firma del usuario y guarda la respuesta" {
        val dto = slot<AnotacionDto>()
        coEvery { anotacionesApi.crear(capture(dto)) } returns anotacion

        repo.registrarAnotacion(docente, "E3A-1", TipoAnotacion.POSITIVA, "Ayuda a sus compañeros.", "detalle")

        dto.captured.firmadoPor shouldBe docente.nombre
        coVerify { anotacionDao.insertar(anotacion) }
    }

    "actualizarAnotacion() hace PUT y sobrescribe la copia local" {
        val corregida = anotacion.copy(tipo = TipoAnotacion.OBSERVACION)
        coEvery { anotacionesApi.actualizar("AN1", any()) } returns corregida

        repo.actualizarAnotacion(docente, anotacion, TipoAnotacion.OBSERVACION, "Texto corregido.", "detalle")

        coVerify { anotacionDao.insertar(corregida) }
        coVerify { auditoriaDao.insertar(match { it.accion == "MODIFICAR" }) }
    }

    "eliminarAnotacion() con 403 (no la firmó) lanza error y no borra nada" {
        coEvery { anotacionesApi.eliminar("AN1", docente.nombre) } returns error(403)

        val e = shouldThrow<HttpException> { repo.eliminarAnotacion(docente, anotacion, "detalle") }
        e.code() shouldBe 403
        coVerify(exactly = 0) { anotacionDao.eliminar(any()) }
    }

    "eliminarAnotacion() con 204 borra la copia local" {
        coEvery { anotacionesApi.eliminar("AN1", docente.nombre) } returns Response.success(204, Unit)

        repo.eliminarAnotacion(docente, anotacion, "detalle")

        coVerify { anotacionDao.eliminar(anotacion) }
    }

    "guardarAsistencia() arma un registro por estudiante y audita CREAR o MODIFICAR" {
        val registros = slot<List<RegistroAsistencia>>()
        coEvery { asistenciaDao.delCursoEnFecha("C3A", "2026-10-10") } returns emptyList()
        coEvery { asistenciaDao.guardar(capture(registros)) } returns Unit

        repo.guardarAsistencia(
            docente, "C3A", "2026-10-10",
            mapOf("E3A-1" to EstadoAsistencia.PRESENTE, "E3A-2" to EstadoAsistencia.AUSENTE), "detalle"
        )

        registros.captured.map { it.id } shouldContainExactly listOf("E3A-1_2026-10-10", "E3A-2_2026-10-10")
        coVerify { auditoriaDao.insertar(match { it.accion == "CREAR" }) }
    }

    "guardarSesion() inserta la sesión con la firma del docente" {
        repo.guardarSesion(docente, "C3A", "MAT", "2026-10-10", "Bloque 1", listOf("OA-MAT-1"), "Contenido de la clase.", "detalle")

        coVerify { sesionDao.insertar(match { it.registradoPor == docente.nombre && it.oaIds == listOf("OA-MAT-1") }) }
    }

    "poblarSiVacia() no inserta nada si la base ya tiene usuarios" {
        coEvery { usuarioDao.contar() } returns 5

        repo.poblarSiVacia()

        coVerify(exactly = 0) { usuarioDao.insertarTodos(any()) }
    }

    "poblarSiVacia() carga los datos ficticios la primera vez" {
        coEvery { usuarioDao.contar() } returns 0

        repo.poblarSiVacia()

        coVerify { usuarioDao.insertarTodos(match { it.size == 5 }) }
        coVerify { anotacionDao.insertarTodas(any()) }
    }

    "lecturas puntuales y exportación delegan en los DAO" {
        coEvery { usuarioDao.buscarPorUsuario("docente.mat@libromp.cl") } returns docente
        coEvery { asistenciaDao.delCursoEnFecha("C3A", "2026-10-01") } returns emptyList()
        val auditoria = slot<RegistroAuditoria>()
        coEvery { auditoriaDao.insertar(capture(auditoria)) } returns Unit

        repo.buscarUsuario("docente.mat@libromp.cl") shouldBe docente
        repo.asistenciaDe("C3A", "2026-10-01") shouldBe emptyList()
        repo.registrarExportacion(docente, "Reporte CSV")
        auditoria.captured.accion shouldBe "EXPORTAR"
        repo.cargasDe("U1")
    }
})
