package com.librompaz.app.data.remote

import com.librompaz.app.BuildConfig
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Configuración única de Retrofit (singleton). Hay una instancia por servicio porque cada uno
 * tiene su propia URL base:
 *  - ms-calificaciones y ms-anotaciones: microservicios PROPIOS (Spring Boot, puertos 8081 y 8082).
 *  - Nager.Date: API EXTERNA pública de feriados.
 * Las URL están en app/build.gradle.kts (BuildConfig).
 */
object RetrofitClient {

    /** Un solo cliente HTTP para todos: tiempos de espera cortos y registro de cada petición en Logcat. */
    private val cliente: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
            .build()
    }

    private fun crear(urlBase: String): Retrofit =
        Retrofit.Builder()
            .baseUrl(urlBase)
            .client(cliente)
            .addConverterFactory(GsonConverterFactory.create())   // JSON <-> clases Kotlin
            .build()

    val calificacionesApi: CalificacionesApi by lazy {
        crear(BuildConfig.URL_MS_CALIFICACIONES).create(CalificacionesApi::class.java)
    }

    val anotacionesApi: AnotacionesApi by lazy {
        crear(BuildConfig.URL_MS_ANOTACIONES).create(AnotacionesApi::class.java)
    }

    val feriadosApi: FeriadosApi by lazy {
        crear(BuildConfig.URL_API_FERIADOS).create(FeriadosApi::class.java)
    }
}
