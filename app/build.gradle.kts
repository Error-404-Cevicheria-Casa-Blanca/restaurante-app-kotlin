plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.detekt)
}

/**
 * Configuracion inyectable por linea de comandos (PRB-2):
 *
 *   .\gradlew.bat assembleDebug -PbaseUrl=http://192.168.1.50:8080/ -PuseFakeData=false
 *
 * Sin propiedades se conservan los valores historicos: emulador 10.0.2.2
 * y datos fake activos en debug.
 */
val baseUrlDebug: String = providers.gradleProperty("baseUrl")
    .orElse("http://10.0.2.2:8080/")
    .get()
val baseUrlRelease: String = providers.gradleProperty("baseUrl")
    .orElse("https://CAMBIAR-ANTES-DE-RELEASE/")
    .get()
val useFakeDataDebug: String = providers.gradleProperty("useFakeData")
    .orElse("true")
    .get()
val useFakeDataRelease: String = providers.gradleProperty("useFakeData")
    .orElse("false")
    .get()

/** Escapa el texto para que BuildConfig reciba un String Java valido. */
fun buildConfigString(valor: String): String =
    "\"${valor.replace("\\", "\\\\").replace("\"", "\\\"")}\""

/** Valida y normaliza la propiedad booleana de Gradle. */
fun buildConfigBoolean(valor: String): String = when (valor.lowercase()) {
    "true", "false" -> valor.lowercase()
    else -> throw GradleException(
        "useFakeData debe ser true o false; se recibio '$valor'."
    )
}

listOf("baseUrl debug" to baseUrlDebug, "baseUrl release" to baseUrlRelease).forEach { (nombre, url) ->
    if (!url.endsWith("/")) {
        throw GradleException("$nombre debe terminar en '/': '$url'.")
    }
}

android {
    namespace = "com.lacasablanca.mesero"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.lacasablanca.mesero"
        minSdk = 35
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            buildConfigField("String", "BASE_URL", buildConfigString(baseUrlDebug))
            buildConfigField("boolean", "USE_FAKE_DATA", buildConfigBoolean(useFakeDataDebug))
        }
        release {
            optimization {
                enable = false
            }
            buildConfigField("String", "BASE_URL", buildConfigString(baseUrlRelease))
            buildConfigField("boolean", "USE_FAKE_DATA", buildConfigBoolean(useFakeDataRelease))
        }
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.androidx.recyclerview)
    implementation(libs.glide)
    implementation(libs.androidx.datastore.preferences)

    // ApiClient compila el interceptor en todas las variantes; su nivel queda
    // en NONE cuando BuildConfig.DEBUG es false, asi que release no registra nada.
    implementation(libs.okhttp.logging.interceptor)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

detekt {
    toolVersion = libs.versions.detekt.get()
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    allRules = false
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "11"
    reports {
        html.required.set(true)
        xml.required.set(true)
        txt.required.set(false)
        sarif.required.set(false)
    }
}

