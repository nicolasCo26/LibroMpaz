package com.librompaz.app.data.remote

// =====================================================================================
// PENDIENTE PARA LA EXPERIENCIA 3: CONSUMO DE API REST (Retrofit ya configurado en Gradle)
// =====================================================================================
// La Evaluación Parcial 2 pide solo persistencia local con Room (sin backend). Está pendiente
// con el CITT si cada equipo construye el backend Spring Boot o solo simula su consumo.
//
// Cuando se implemente, el cambio queda encapsulado en LibroMpazRepository: el ViewModel y las
// pantallas no se modifican. Estructura prevista para esta carpeta:
//   1) DTOs con los datos que se envían/reciben (por ejemplo AsistenciaDto, NotaDto).
//   2) Interfaz Retrofit, por ejemplo:
//          interface LibroApi {
//              @GET("cursos/{id}/estudiantes")
//              suspend fun estudiantes(@Path("id") cursoId: String): List<EstudianteDto>
//          }
//   3) Instancia de Retrofit (Retrofit.Builder + GsonConverterFactory).
//
// Restricciones del caso: sin integración con plataformas reales del MINEDUC ni con
// sistemas del sostenedor, y la app NO envía notificaciones, correos, SMS ni WhatsApp.
// =====================================================================================
