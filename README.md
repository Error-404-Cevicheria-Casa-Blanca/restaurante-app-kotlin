# restaurante-app-kotlin

App del mesero de la Cevichería "La Casa Blanca" — toma de pedidos desde tablet Android.

## Stack

- Kotlin 2.x (plugin integrado con AGP 9.3.3)
- **Vistas XML** (Material 3, sin Compose)
- minSdk 35 · compileSdk 37 · targetSdk 37
- Retrofit 2.11.0 + Gson
- OkHttp 4.12.0 (logging-interceptor solo en debug)
- Coroutines 1.10.2
- Lifecycle 2.11.0 (ViewModel + Runtime KTX)
- RecyclerView 1.4.0
- Glide 4.16.0
- DataStore Preferences 1.2.1
- Gradle Kotlin DSL
- MockK 1.14.3 + kotlinx-coroutines-test (pruebas unitarias)

## Cómo ejecutar

### Requisitos

- Android Studio (Jellyfish o superior recomendado)
- JDK 17 o 21
- Android SDK con API 35+ instalado
- Emulador Android (probado en `Medium_Phone` API 35) o dispositivo físico

### Pasos

1. Clona el repositorio: