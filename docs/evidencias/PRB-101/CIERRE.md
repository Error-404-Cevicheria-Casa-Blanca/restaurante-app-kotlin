# CIERRE — PRB-101 [P1] App: login por PIN (RF-04)

- **Rama:** `feature/PRB-101-login-pin` (desde `develop@00c530b`)
- **Fecha:** 20/11/2026
- **Alcance:** login por `usuario` + PIN de 4 dígitos reutilizando `POST /api/v1/auth/pin`, con `terminal_codigo`, teclado PIN en móvil y `sw600dp`, estados/errores y tests.
- **No incluye:** validación visual en emulador/tablet ni llamada HTTP real (quedan para PRB-46).

## Evidencias

| Archivo | Contenido |
|---|---|
| `01-testDebugUnitTest-detekt-assemble.txt` | Salida cruda de `.\gradlew.bat clean testDebugUnitTest detekt assembleDebug --console=plain` con `EXIT=0`. |
| `02-test-totales.txt` | Totales leídos de `app/build/test-results/testDebugUnitTest`: 39 XML, 301 tests, 0 fallos, 0 errores, 0 omitidos. |
| `03-git-diff-stat.txt` | `git status` y `git diff --stat` de la rama antes del commit. |

## Criterios

| Criterio | archivo:línea | Evidencia | Estado |
|---|---|---|---|
| `PinRequestDto` envía `terminal_codigo` en snake_case | `app/src/main/java/com/lacasablanca/mesero/data/remote/dto/AuthDtos.kt:14-18` | `01` (compila) + `PinRequestDtoTest` (2 tests) | CUMPLE |
| `AuthRepository` expone `iniciarSesionPorPin` sin cambiar `validarPin` | `app/src/main/java/com/lacasablanca/mesero/data/repository/AuthRepository.kt:15` | `01` (compila) | CUMPLE |
| Repositorio real envía `usuario`, `pin` y `terminal_codigo`, valida rol y persiste la sesión | `app/src/main/java/com/lacasablanca/mesero/data/repository/RealAuthRepository.kt:57-80`; `:89-116` | `RealAuthRepositoryPinTest` (7 tests) | CUMPLE |
| 400 → `VALIDACION` | `RealAuthRepository.kt:69` | `RealAuthRepositoryPinTest` (`un 400 devuelve validacion`) | CUMPLE |
| 401 → `CREDENCIALES` | `RealAuthRepository.kt:70` | `RealAuthRepositoryPinTest` (`un 401 devuelve credenciales`) | CUMPLE |
| 403 → `ROL_NO_PERMITIDO` | `RealAuthRepository.kt:71` | `RealAuthRepositoryPinTest` (`un 403 devuelve rol no permitido`) | CUMPLE |
| 423 → `BLOQUEADO` | `RealAuthRepository.kt:72` | `RealAuthRepositoryPinTest` (`un 423 devuelve bloqueado`) | CUMPLE |
| `IOException` → `RED` | `RealAuthRepository.kt:77-78` | `RealAuthRepositoryPinTest` (`una IOException devuelve RED`) | CUMPLE |
| Fake valida `PIN_DEMO = "1234"` y persiste sesión fake | `app/src/main/java/com/lacasablanca/mesero/data/repository/FakeAuthRepository.kt:41-50`; `:69` | `FakeAuthRepositoryPinTest` (3 tests) | CUMPLE |
| ViewModel: modo PIN, dígitos, carga y error tipado | `app/src/main/java/com/lacasablanca/mesero/ui/login/LoginUiState.kt:12-29`; `LoginViewModel.kt:46-68` | `01` (compila) + `LoginPinViewModelTest` | CUMPLE |
| PIN de 4 dígitos dispara `iniciarSesionPorPin` y emite `IrAMesas` en éxito | `LoginViewModel.kt:95-117` | `LoginPinViewModelTest` (`el pin correcto inicia sesion y navega`) | CUMPLE |
| PIN inválido limpia el teclado y muestra error | `LoginViewModel.kt:139-147` | `LoginPinViewModelTest` (`un pin invalido muestra el error y limpia el teclado`) | CUMPLE |
| Cuenta bloqueada muestra `BLOQUEADO` | `LoginViewModel.kt:141` | `LoginPinViewModelTest` (`una cuenta bloqueada muestra el error de bloqueo`) | CUMPLE |
| Red muestra `RED` | `LoginViewModel.kt:143` | `LoginPinViewModelTest` (`un fallo de red muestra el error de conexion`) | CUMPLE |
| Usuario vacío no llama a la API | `LoginViewModel.kt:95-99` | `LoginPinViewModelTest` (`sin usuario no llama a la API`) | CUMPLE |
| Entrada "Ingresar con PIN" debajo de `btnIngresar` en móvil | `app/src/main/res/layout/activity_login.xml:215` | `01` (recursos compilan) | CUMPLE |
| Entrada "Ingresar con PIN" debajo de `btnIngresar` en `sw600dp` | `app/src/main/res/layout-sw600dp/activity_login.xml:236` | `01` (recursos compilan) | CUMPLE |
| Teclado PIN de 4 dígitos reutiliza estilos/recursos existentes | `activity_login.xml:224`; `layout-sw600dp/activity_login.xml:245` | `01` (recursos compilan) | CUMPLE |
| Login por credenciales se conserva y se alterna con el modo PIN | `LoginActivity.kt:205-217`; `LoginViewModel.kt:71-93` | `01` (compila) + tests existentes de `LoginViewModelTest` | CUMPLE |
| PIN demo se muestra solo con `USE_FAKE_DATA` | `LoginActivity.kt:129-137` | `01` (compila) + revisión de código | CUMPLE |
| Mensajes claros (PIN incorrecto, bloqueo, red, formato, genérico) | `LoginActivity.kt:299-309`; `strings.xml:29-35` | `01` (compila) + `LoginPinViewModelTest` | CUMPLE |
| No se fija orientación ni se toca el manifest | `git diff` sin cambios en `AndroidManifest.xml` | `03` | CUMPLE |
| Sin Hilt ni Room nuevos | `git diff` sin cambios en `app/build.gradle.kts` / `gradle/libs.versions.toml` | `03` | CUMPLE |
| Flujo real en emulador/tablet (aspecto y teclado en pantalla) | — | No se ejecutó emulador/tablet en este entorno | NO VERIFICADO |
| Llamada HTTP real a `POST /api/v1/auth/pin` | — | No se levantó el backend en esta sesión | NO VERIFICADO |

## Totales de tests

`02-test-totales.txt`: **301 tests, 0 fallos, 0 errores, 0 omitidos** (39 archivos XML).
Tests nuevos de PRB-101: 19 (2 DTO + 7 repositorio real + 3 fake + 7 ViewModel).

## NO VERIFICADO

- Aspecto visual y comportamiento del teclado PIN en móvil y `sw600dp` (sin emulador/tablet en esta sesión). Se cubre en PRB-46.
- Respuesta real del backend y persistencia de terminal/turno vía `POST /api/v1/auth/pin` (sin backend levantado). El contrato se verificó leyendo `AuthController.java:34-37`, `AuthService.autenticarPorPin` y `PinLoginRequest.java:16-17`.

## Supuestos abiertos

- A168: `terminal_codigo` se envía también en el login por PIN, igual que en `/auth/login`.
- A169: PIN demo `1234` solo con `USE_FAKE_DATA=true`.
- A170: UI real y HTTP real quedan NO VERIFICADO hasta PRB-46.

## Comentario para Jira (borrador)

PRB-101 implementado en `feature/PRB-101-login-pin` (base `develop@00c530b`). Incluye `terminal_codigo` en `PinRequestDto`, `AuthRepository.iniciarSesionPorPin` con manejo 400/401/403/423/IOException, fake con PIN `1234`, teclado PIN de 4 dígitos en login móvil y `sw600dp`, y 19 tests nuevos. Verificación: `clean testDebugUnitTest detekt assembleDebug` → `EXIT=0`; 301 tests, 0 fallos. NO VERIFICADO: UI real en emulador/tablet y llamada HTTP real (pendiente PRB-46). Evidencias en `docs/evidencias/PRB-101/`.
