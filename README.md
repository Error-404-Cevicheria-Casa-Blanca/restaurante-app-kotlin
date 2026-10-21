# restaurante-app-kotlin

App del mesero de la Cevichería "La Casa Blanca" — toma de pedidos desde tablet Android.

## Stack

- Kotlin 2.x (plugin integrado con AGP 9.3.3)
- **Vistas XML** (Material 3, sin Compose)
- minSdk 35 · compileSdk 37 · targetSdk 37
- Retrofit 3.0.0 + Gson
- OkHttp 4.12.0 (logging-interceptor en debug y en el source set de tests)
- Coroutines 1.11.0
- Lifecycle 2.11.0 (ViewModel + Runtime KTX)
- RecyclerView 1.4.0
- Glide 4.16.0
- DataStore Preferences 1.2.1
- Gradle Kotlin DSL
- MockK 1.14.11 + kotlinx-coroutines-test (pruebas unitarias)

## Cómo ejecutar

### Requisitos

- Android Studio con soporte para **AGP 9.3.3**.
- **JDK 25 para el daemon de Gradle.** No hace falta instalarlo a mano: `gradle/gradle-daemon-jvm.properties`
  fija `toolchainVersion=25` y Gradle **lo descarga la primera vez**, por lo que se **necesita internet** en
  ese primer arranque. (No se requiere instalar "JDK 17 o 21".)
- **Android SDK Platform 37 instalada**: el proyecto usa `compileSdk 37` / `targetSdk 37`.
- Emulador o tablet con **Android 15 o superior (API 35)**, que es el `minSdk 35`. Probado en `Medium_Phone`.

### Pasos

1. Clona el repositorio:

   ```bash
   git clone <url-del-repo>
   cd restaurante-app-kotlin
   ```

2. Abre la carpeta en Android Studio y deja que haga *Sync* (la primera vez descarga el JDK 25 del toolchain).
3. Compila y ejecuta:

   ```bash
   ./gradlew.bat assembleDebug
   ```

4. Ejecuta las pruebas unitarias:

   ```bash
   ./gradlew.bat testDebugUnitTest
   ```

La `BASE_URL` y `USE_FAKE_DATA` se configuran por `buildConfigField` en `app/build.gradle.kts`
(emulador `http://10.0.2.2:8080/`; tablets reales `http://<IP-LAN-de-la-PC>:8080/`). El tráfico HTTP en claro
solo está permitido en `debug`.
