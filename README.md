# Libro MPAZ — Libro digital de clases

Aplicación Android para la Escuela Escritora Marcela Paz (San Bernardo). Reemplaza el libro de clases en papel (asistencia, cobertura curricular, calificaciones y hoja de vida) por una app con vistas por perfil: **Docente, UTP, Inspectoría y Dirección**.

Kotlin · Jetpack Compose · Material 3 · MVVM · Room (SQLite).

## Equipo

| Nombre | Usuario GitHub | Qué desarrolló |
|---|---|---|
| Nicolás Navarrete Reyes | [nicolasCo26](https://github.com/nicolasCo26) | Persistencia con Room, ViewModel y reglas, asistencia, calificaciones, recurso nativo y panel de Dirección |
| Camila Leiva | [alemi-vy](https://github.com/alemi-vy) | Login y navegación por rol, pantallas del docente (clase y hoja de vida), paneles de UTP e Inspectoría, README |

**Repositorio:** https://github.com/nicolasCo26/LibroMpaz. Cada funcionalidad se desarrolló en su propia rama (`feat/...`) y se integró a `main` con un Pull Request.

## Cómo ejecutar

1. Clonar el repositorio o descomprimir el proyecto en una carpeta limpia.
2. Android Studio → **File > Open…** → seleccionar la carpeta que contiene `settings.gradle.kts`.
3. Esperar el Gradle Sync (la primera vez necesita internet).
4. Ejecutar ▶ **Run 'app'** en un emulador o dispositivo (API 24 o superior).

Si Android Studio sugiere actualizar Gradle o el Android Gradle Plugin, **no aceptar**. El proyecto compila con JDK 17 o 21 (*Settings > Build Tools > Gradle > Gradle JDK*), no con JDK 25. Versiones: Kotlin 2.0.0 · AGP 8.5.2 · Gradle 8.7 · Room 2.6.1 · KSP 2.0.0-1.0.24.

La primera vez que se abre, la app crea la base de datos `libro_mpaz.db` y la llena con datos ficticios (`data/MockData.kt`). Para volver a los datos iniciales: **Ajustes del teléfono > Apps > Libro MPAZ > Almacenamiento > Borrar datos**.

**Pruebas unitarias:** clic derecho en `app/src/test/java/com/librompaz/app` → **Run 'Tests in…'** (`ValidacionesTest` y `MetricasTest`).

## Usuarios de prueba (ficticios)

| Perfil | Correo | Clave | Qué ve |
|---|---|---|---|
| Docente | docente.mat@libromp.cl | docente123 | Matemática en 3° Básico A y 4° Básico A |
| Docente | docente.len@libromp.cl | docente123 | Lenguaje en 4° Básico A y Ciencias en 3° Básico A |
| UTP | utp@libromp.cl | utp123 | Cobertura curricular y revisión de notas |
| Inspectoría | inspectoria@libromp.cl | inspectoria123 | Asistencia de todos los cursos y alertas |
| Dirección | direccion@libromp.cl | direccion123 | Métricas por curso, auditoría y exportación |

## Requerimientos implementados

| Requerimiento | Cómo se cumple | Dónde |
|---|---|---|
| RF-01 Autenticar y mostrar solo las cargas del docente | Login contra la tabla `usuarios`; `cargasDe(docenteId)` filtra en SQL | `LoginScreen`, `MisCursosScreen`, `AcademicoDao` |
| RF-02 Control de acceso por rol (RBAC) | Cada ruta declara sus perfiles permitidos (`protegida(...)`) y el ViewModel revisa la carga antes de guardar | `AppNavigation`, `LibroMpazViewModel.tieneCarga()` |
| RF-03 Asistencia P / A / T / J | Registro diario por curso; se puede corregir un día ya registrado | `AsistenciaScreen` |
| RF-04 Asistencia en menos de un minuto | Todos parten "presente", botón "Todos presentes" y resumen del día | `AsistenciaScreen` |
| RF-05 Inspectoría gestiona la asistencia de todos los niveles | Porcentaje por curso y acceso a registrar o corregir cualquier curso | `PanelInspectoriaScreen` |
| RF-06 OA y contenidos por sesión | Fecha, bloque, OA (catálogo) y contenidos, con validaciones | `RegistroClaseScreen` |
| RF-07 Cobertura curricular para UTP | OA trabajados y pendientes por curso y asignatura, con % de avance | `CoberturaCurricularScreen` |
| RF-08 Calificaciones con instrumento y fecha | Crear, eliminar evaluaciones; ingresar y corregir notas 1,0 a 7,0 | `CalificacionesScreen` |
| RF-09 Desfases u omisiones de notas | Cargas sin evaluaciones y evaluaciones con notas faltantes | `RevisionCalificacionesScreen` |
| RF-10 Anotaciones con marca temporal y firma | Usuario + fecha y hora automáticas, después de confirmar | `HojaDeVidaScreen` |
| RF-11 Riesgo e inasistencias reiteradas | Alertas con 3+ ausencias o 2+ anotaciones negativas; hoja de vida filtrable | `AlertasInspectoriaScreen` |
| RF-12 Estado global para Dirección | Asistencia, clases, notas y anotaciones negativas por curso | `PanelDireccionScreen` |
| RN-01 Sin acceso de estudiantes ni apoderados | Solo existen los 4 perfiles internos | `Perfil`, `MockData.usuarios` |
| RN-02 Auditoría automática e inalterable | Cada escritura guarda su auditoría en la misma transacción; el DAO de auditoría no tiene update ni delete | `LibroMpazRepository`, `AuditoriaDao`, `AuditoriaScreen` |
| RNF-01 Interfaz simple y rápida | Pantallas de una sola tarea; Room local, sin red | — |
| RNF-02 Confirmación antes de sobrescribir o borrar | Diálogos al sobrescribir asistencia o notas, eliminar evaluación y registrar anotación | Pantallas del docente |
| RNF-03 Sin GPS, cámara, micrófono ni biometría | No se declara ningún permiso en el `AndroidManifest` | — |
| RNF-04 Datos 100 % ficticios | "Estudiante 3A-01", correos `@libromp.cl` | `MockData.kt` |

## Recurso nativo: almacenamiento con el selector de archivos del sistema

**Qué hace:** en el Panel de Dirección, **"Exportar reporte (CSV)"** abre el selector nativo de Android ("Guardar como"). El usuario elige dónde guardar y la app escribe un archivo CSV con las métricas por curso, que se puede abrir en Excel.

**Por qué es pertinente:** el colegio está obligado a mantener registros de asistencia y avance académico, y Dirección necesita respaldarlos o presentarlos. Hoy esos resúmenes se hacen a mano a partir de los cuadernos. El reporte:
- contiene **solo métricas consolidadas por curso**, sin nombres de estudiantes (resguarda la privacidad, RN-01);
- solo lo puede generar el perfil **Dirección**;
- queda registrado en la **auditoría** (acción `EXPORTAR`).

**Cómo respeta las restricciones del proyecto:** no usa GPS, cámara, micrófono ni biometría (RNF-03), y la app no envía nada por su cuenta: no hay notificaciones, correos ni mensajes. El archivo se guarda donde el usuario decide.

**Integración y permisos:** se usa `rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv"))`. Android devuelve un `Uri` y `ExportadorCsv` escribe en él con el `ContentResolver`, en `Dispatchers.IO`. **No requiere declarar permisos**: con Storage Access Framework el usuario elige el archivo y la app solo recibe acceso a ese archivo. Si el usuario cancela, se muestra "Exportación cancelada" y la app no falla.

Archivos: `recursosnativos/ExportadorCsv.kt`, `ui/screen/direccion/PanelDireccionScreen.kt` y `LibroMpazViewModel.exportarReporte()`.

## Arquitectura (MVVM)

```
Pantalla (@Composable) ──evento──> ViewModel ──> Repository ──> DAO ──> Room (SQLite)
        ^                            │ valida                          │
        └──── collectAsState() <── StateFlow <──────── Flow <──────────┘
```

| Capa | Archivos | Responsabilidad |
|---|---|---|
| Entity | `model/*.kt` (`@Entity`) | Las 12 tablas: usuarios, cursos, asignaturas, estudiantes, cargas_docentes, asistencias, objetivos, sesiones, evaluaciones, calificaciones, anotaciones y auditoria. Claves foráneas entre ellas. |
| DAO | `data/local/Daos.kt` | Consultas. Las lecturas observables devuelven `Flow`; las escrituras son `suspend`. |
| Database | `data/local/AppDatabase.kt`, `Converters.kt` | Une entidades y DAOs con una sola instancia (singleton). El converter guarda la lista de OA como texto. |
| Repository | `data/repository/LibroMpazRepository.kt` | Única puerta a los datos; cada escritura y su auditoría van en `withTransaction`. |
| ViewModel | `viewmodel/LibroMpazViewModel.kt` | Estado (`StateFlow`), permisos por rol y orquestación con `viewModelScope`. |
| Reglas | `viewmodel/Validaciones.kt`, `viewmodel/Metricas.kt` | Validaciones y cálculos como funciones puras, con pruebas en `app/src/test`. |
| UI | `ui/screen/**`, `ui/components/**` | Pantallas Compose + Material 3 que solo dibujan el estado y avisan los eventos. |
| Navegación | `navigation/AppNavigation.kt` | NavHost, rutas por perfil, transiciones y cierre de sesión que limpia la pila. |

**Ejemplo de cómo viaja un dato (guardar una nota):** `CalificacionesScreen` → `viewModel.guardarCalificaciones()` valida con `validarNota()` → `repository.guardarCalificaciones()` abre una transacción → `CalificacionDao.guardarNotas()` (REPLACE) + `AuditoriaDao.insertar()` → Room emite el `Flow` de calificaciones → `viewModel.calificaciones` (StateFlow) → la pantalla se redibuja.

## Formularios, validaciones y animaciones

- **Validaciones (ViewModel / `Validaciones.kt`):** correo con formato válido y clave obligatoria; fechas obligatorias y no futuras (además, el calendario no deja elegir días futuros); nota de 1,0 a 7,0 con un decimal, que acepta coma o punto; nombre del instrumento de 3 a 60 caracteres; sesión con bloque, al menos un OA y contenido de 10 caracteres o más; anotación con estudiante, tipo y descripción de 10 a 300 caracteres. El error aparece **bajo el campo** (`isError` + `supportingText`).
- **Mensajes de éxito** animados (`AvisoExito`, con `AnimatedVisibility`) y **confirmaciones** (`AlertDialog`) antes de acciones destructivas.
- **Animaciones:** transiciones entre pantallas (deslizar y desvanecer), color animado de los estados de asistencia (`animateColorAsState`), barras de avance animadas (`animateFloatAsState`), planilla de notas que aparece al elegir una evaluación (`AnimatedVisibility`), `animateContentSize` y `animateItem` en listas, y un indicador de carga mientras se prepara la base.
- **Componentes interactivos:** `DatePickerDialog`, listas desplegables (`ExposedDropdownMenuBox`), `FilterChip`, `Checkbox` y diálogos.

## Decisiones de diseño

1. Un docente puede tener más de una asignatura (en los datos, el docente de Lenguaje también hace Ciencias).
2. Los cuatro perfiles son excluyentes: cada usuario tiene un solo rol.
3. **Firma digital** = usuario autenticado + fecha y hora automáticas, tras una confirmación explícita. Las anotaciones no se editan ni se borran.
4. Se puede **corregir la asistencia** de días anteriores. La corrección pide confirmación y queda auditada como `MODIFICAR`.
5. El estado "justificado" no requiere adjuntar un respaldo.
6. Las notas se validan en el rango **1,0 a 7,0**.
7. Los **OA** vienen de un catálogo cerrado de ejemplo cargado en la base; el docente solo los elige.
8. Muestra reducida: **2 cursos con 8 estudiantes** cada uno.
9. Sin backend: los datos se guardan solo en el dispositivo. Retrofit queda configurado para una futura API REST (`data/remote/ApiPendiente.kt`).

## APK firmado

**Build > Generate Signed App Bundle / APK… > APK > release**. El keystore (`.jks`) y sus claves **no** se suben al repositorio.
