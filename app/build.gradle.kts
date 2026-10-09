plugins {
    alias(libs.plugins.android.application)
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
            buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:8080/\"")
            buildConfigField("boolean", "USE_FAKE_DATA", "true")
        }
        release {
            optimization {
                enable = false
            }
            buildConfigField("String", "BASE_URL", "\"https://CAMBIAR-ANTES-DE-RELEASE/\"")
            buildConfigField("boolean", "USE_FAKE_DATA", "false")
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

    debugImplementation(libs.okhttp.logging.interceptor)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
