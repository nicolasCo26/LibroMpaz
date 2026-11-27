import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    // KSP: genera el código de Room (DAO, base de datos) al compilar.
    alias(libs.plugins.ksp)
    // Kover: mide qué porcentaje de la lógica cubren las pruebas unitarias.
    alias(libs.plugins.kover)
}

// Credenciales de firma del APK release: se leen de keystore.properties (raíz del proyecto).
val keystoreProps = Properties().apply {
    val archivo = rootProject.file("keystore.properties")
    if (archivo.exists()) archivo.inputStream().use { load(it) }
}

android {
    namespace = "com.librompaz.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.librompaz.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // URL base de cada servicio. 10.0.2.2 es el "localhost" del computador visto desde el emulador.
        // En un teléfono físico se reemplaza por la IP del computador en la misma red Wi-Fi.
        buildConfigField("String", "URL_MS_CALIFICACIONES", "\"http://10.0.2.2:8081/\"")
        buildConfigField("String", "URL_MS_ANOTACIONES", "\"http://10.0.2.2:8082/\"")
        buildConfigField("String", "URL_API_FERIADOS", "\"https://date.nager.at/\"")
    }

    signingConfigs {
        create("release") {
            val ruta = keystoreProps.getProperty("storeFile")
            if (ruta != null) {
                storeFile = rootProject.file(ruta)
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
                // V1 (JAR) solo sirve bajo Android 7; como minSdk = 24, Gradle firma con V2 (APK Signature Scheme v2).
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystoreProps.getProperty("storeFile") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        // Las clases de Android (por ejemplo Uri) devuelven valores por defecto en las pruebas JVM.
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // --- Navegación entre pantallas ---
    // navigation-compose 2.7.7 ya incluye el runtime de navegación. NO agregar
    // "navigation-runtime-android": esa coordenada no existe en la versión 2.7.7.
    implementation(libs.androidx.navigation.compose)

    // --- Íconos (flecha atrás, mostrar/ocultar clave, agregar, etc.) ---
    implementation(libs.androidx.material.icons.extended)

    // --- Persistencia local: Room + DataStore ---
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    // --- Consumo de API REST: microservicios propios y API externa (Retrofit + Gson) ---
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.gson)
    // Muestra cada petición y respuesta HTTP en Logcat (etiqueta "okhttp.OkHttpClient").
    implementation(libs.okhttp.logging)

    // --- Dependencias estándar del proyecto (template de Android Studio) ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // --- Pruebas unitarias (src/test): Kotest + MockK + corrutinas de prueba ---
    testImplementation(libs.kotest.runner.junit5)     // Kotest sobre JUnit 5
    testImplementation(libs.kotest.assertions.core)   // shouldBe, shouldContainExactly...
    testImplementation(libs.junit.jupiter)            // JUnit 5
    testImplementation(libs.mockk)                    // simular dependencias (DAO, API, repositorio)
    testImplementation(libs.kotlinx.coroutines.test)  // runTest, StandardTestDispatcher
    testImplementation(libs.kotlin.reflect)           // Kotest la necesita para encontrar las pruebas
    testImplementation(libs.junit)                    // pruebas JUnit 4 anteriores
    testRuntimeOnly(libs.junit.vintage.engine)        // ejecuta las pruebas JUnit 4 sobre JUnit 5

    // --- Pruebas de interfaz (src/androidTest): se ejecutan en el emulador ---
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

// Obligatorio para usar JUnit 5 (Kotest) en las pruebas unitarias.
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

// Cobertura: solo cuenta la LÓGICA (ViewModel, reglas y repositorios), no las pantallas.
// Reporte: ./gradlew koverHtmlReportDebug  ->  app/build/reports/kover/htmlDebug/index.html
// Verificación (falla si baja de 80 %): ./gradlew koverVerifyDebug
kover {
    reports {
        filters {
            includes {
                packages("com.librompaz.app.viewmodel", "com.librompaz.app.data.repository")
            }
        }
        verify {
            rule {
                minBound(80)
            }
        }
    }
}
