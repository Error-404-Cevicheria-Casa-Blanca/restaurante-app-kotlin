# CIERRE — Polish visual para la presentación

> **Actualización 2026-11-05:** el recorte del splash y el icono de la app se corrigieron en el commit `a378afc` (ver `icono-splash/CIERRE.md`). Las capturas `09-splash-b.png` y `09-splash-c.png` corresponden al estado anterior, en el que el logo se recortaba.

- **Fecha:** 2026-11-04
- **Repositorio:** `repos/restaurante-app-kotlin`
- **Rama:** `feature/polish-presentacion` (creada desde `develop`, base `4957527`)
- **Commit de código:** `769144f feat(polish-presentacion): pulir splash, animaciones y feedback haptico`
- **Alcance:** solo UI/UX y recursos. No se tocaron ViewModels, Retrofit, repositorios ni lógica de BD.

## Cambios implementados

| # | Criterio de la tarea | Implementación | Referencia |
|---|---|---|---|
| 1 | Splash Screen con el logo usando la API moderna | Atributos nativos `windowSplashScreenBackground`, `windowSplashScreenAnimatedIcon` y `windowSplashScreenIconBackgroundColor` en el tema (API 31+, cubierta por `minSdk 35`). Se usa `logo_casa_blanca.png`. | `app/src/main/res/values/themes.xml:22`, `values-night/themes.xml:21`, `06-referencias-codigo.txt` |
| 2a | Fade-in/slide-up al abrir el BottomSheet del carrito | Extensión reutilizable `View.aparecerConDesplazamiento()` y llamada desde `CarritoBottomSheet.onViewCreated`. | `ui/common/Animaciones.kt:16`, `ui/comanda/CarritoBottomSheet.kt:99` |
| 2b | Animación sutil en la primera carga de mesas y catálogo | Bandera de una sola ejecución + `aparecerConDesplazamiento()` sobre el `RecyclerView` cuando llegan datos por primera vez. | `ui/mesas/MesasActivity.kt:184`, `ui/comanda/ComandaActivity.kt:174` |
| 3 | Estado vacío del carrito con ícono y texto | `grupoCarritoVacio` con ícono vectorial `ic_carrito_vacio`, círculo de fondo `bg_circulo_vacio` y el texto solicitado. | `res/layout/bottom_sheet_carrito.xml:99`, `res/values/strings.xml:152` |
| 4 | Feedback háptico al presionar “+” en el catálogo | `performHapticFeedback(HapticFeedbackConstants.CONFIRM)` en el botón “Agregar” y en el “+” del stepper. Respeta la configuración de hápticos del sistema. | `ui/comanda/CatalogoAdapter.kt:119`, `:133` |
| 5 | Compilar y tests | `assembleDebug` y `testDebugUnitTest --rerun-tasks` en verde. | `01-assembleDebug.txt`, `02-testDebugUnitTest.txt`, `03-test-results-totales.txt` |

## Verificación

| Criterio | Evidencia | Estado |
|---|---|---|
| `assembleDebug` | `01-assembleDebug.txt` — `BUILD SUCCESSFUL in 13s`, `EXIT=0` | **CUMPLE** |
| Tests unitarios | `02-testDebugUnitTest.txt` — `BUILD SUCCESSFUL`, `EXIT=0`; `03-test-results-totales.txt` — 71 tests, 0 fallos, 0 errores, 0 omitidos | **CUMPLE** |
| Análisis estático (detekt) | `04-detekt.txt` — `BUILD SUCCESSFUL`, `EXIT=0` | **CUMPLE** |
| Lint de recursos | `05-lintDebug.txt` — `BUILD SUCCESSFUL`, `EXIT=0` | **CUMPLE** |
| Smoke test en emulador (`Medium_Phone`) | `08-smoke-emulador.txt` — install `Success`, cold start `Status: ok`, `topResumedActivity=MesasActivity`, sin `FATAL EXCEPTION` | **CUMPLE** |
| Splash visible en arranque en frío | `09-splash-b.png` / `09-splash-c.png`; `09-splash-analisis.txt` — 94.5% de muestras en `#0B5563`, centro distinto del fondo | **CUMPLE** (fondo/logo renderizados; estética a revisión humana) |
| Estado vacío del carrito | `10-carrito-vacio.png`, `10-carrito-vacio.txt` y `10-carrito-vacio-ui.xml` — texto exacto y `content-desc=Carrito de compras vacío` | **CUMPLE** |
| Feedback háptico | `11-haptico-emulador.txt` — `vibrator_manager` registra `performHapticFeedback(constant=16)`, `usage: TOUCH`, `com.lacasablanca.mesero` | **CUMPLE** (funcional; sensación subjetiva pendiente) |
| Animación del BottomSheet | Código y compilación OK; no se capturó video | **NO VERIFICADO** |
| Animación inicial de mesas/catálogo | Código y compilación OK; no se capturó video | **NO VERIFICADO** |
| Layout tablet (`layout-sw600dp`) tras los cambios | El BottomSheet es compartido; no se capturó en tablet | **NO VERIFICADO** |
| Estética general / fuente grande | Requiere revisión humana | **NO VERIFICADO** |

## ASSUMPTIONS

| ID | Supuesto | Propuesta por defecto |
|---|---|---|
| P1 | La tarea no tiene clave `PE-xx` ni issue Jira; se pidió la rama `feature/polish-presentacion`. | Se usó esa rama y el scope `polish-presentacion` en el commit. Si el equipo exige `PRB-xx`, se renombra la rama/commit antes del PR. |
| P2 | `minSdk = 35` permite usar la SplashScreen API nativa sin la librería `androidx.core:core-splashscreen`. | Se usaron los atributos `android:windowSplashScreen*` del tema. No se agregaron dependencias. |
| P3 | La barra flotante de checkout se oculta con el carrito vacío (decisión A23 de PRB-38). | El estado vacío se hace visible cuando el BottomSheet queda abierto y se quita el último plato. |
| P4 | El háptico debe ser sutil y respetar la configuración del usuario. | `HapticFeedbackConstants.CONFIRM`; no se declara `VIBRATE` porque `performHapticFeedback` no lo requiere. |

## Pendiente de verificación humana

1. Revisar `09-splash-b.png` / `09-splash-c.png`: validar que el logo se vea bien sobre teal.
2. Grabar la primera carga de mesas y catálogo para confirmar el fade + slide-up.
3. Grabar la apertura del BottomSheet para confirmar la animación.
4. Revisar `10-carrito-vacio.png`: validar ícono, texto y espaciado.
5. Probar en tablet (`layout-sw600dp`) y con fuente grande del sistema.
6. Sentir el háptico en un dispositivo/emulador con hápticos activos (el sistema ya registra `CONFIRM`).

## Comentario para Jira (sin clave asociada)

> **Polish visual para la presentación**
> Se implementó el splash con el logo de La Casa Blanca usando la SplashScreen API nativa (minSdk 35), animación fade/slide al abrir el carrito y en la primera carga de mesas/catálogo, estado vacío del carrito con ícono y feedback háptico en los botones “+” del catálogo.
> Verificación: `assembleDebug` OK, 71 tests OK, detekt OK, lint OK. Smoke test en emulador `Medium_Phone` OK (cold start + navegación a Mesas + splash capturado). Capturas de splash y carrito vacío adjuntas; el háptico queda registrado por `vibrator_manager` como `CONFIRM`.
> Commit de código: `769144f`.
> Pendiente: validar la estética y las animaciones en video/tablet (el agente no ve la pantalla).
