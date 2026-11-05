# CIERRE — Polish visual para la presentación

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
| Splash visible en arranque en frío | Requiere emulador/tablet; el agente no ve la pantalla | **NO VERIFICADO** |
| Animación del BottomSheet | Requiere captura/video en emulador | **NO VERIFICADO** |
| Animación inicial de mesas/catálogo | Requiere captura/video en emulador | **NO VERIFICADO** |
| Estado vacío visible | Requiere abrir el carrito, agregar un plato y quitarlo | **NO VERIFICADO** |
| Sensación del háptico | Requiere dispositivo/emulador con hápticos activos | **NO VERIFICADO** |
| Layout tablet (`layout-sw600dp`) tras los cambios | El BottomSheet es compartido; no se capturó en tablet | **NO VERIFICADO** |

## ASSUMPTIONS

| ID | Supuesto | Propuesta por defecto |
|---|---|---|
| P1 | La tarea no tiene clave `PE-xx` ni issue Jira; se pidió la rama `feature/polish-presentacion`. | Se usó esa rama y el scope `polish-presentacion` en el commit. Si el equipo exige `PRB-xx`, se renombra la rama/commit antes del PR. |
| P2 | `minSdk = 35` permite usar la SplashScreen API nativa sin la librería `androidx.core:core-splashscreen`. | Se usaron los atributos `android:windowSplashScreen*` del tema. No se agregaron dependencias. |
| P3 | La barra flotante de checkout se oculta con el carrito vacío (decisión A23 de PRB-38). | El estado vacío se hace visible cuando el BottomSheet queda abierto y se quita el último plato. |
| P4 | El háptico debe ser sutil y respetar la configuración del usuario. | `HapticFeedbackConstants.CONFIRM`; no se declara `VIBRATE` porque `performHapticFeedback` no lo requiere. |

## Pendiente de verificación humana (capturas/videos)

1. `01-splash-arranque-frio.png` — cerrar la app y abrirla; debe verse el logo sobre teal.
2. `02-mesas-primera-carga.png` o video — el grid debe aparecer con fade + slide-up.
3. `03-bottom-sheet-apertura.png` o video — el contenido del sheet debe aparecer suave.
4. `04-carrito-vacio.png` — agregar un plato, abrir el carrito, quitarlo; debe verse el ícono y “Tu pedido está vacío. Agrega platos para comenzar”.
5. `05-catalogo-primera-carga.png` o video — el catálogo debe aparecer con fade + slide-up.
6. `06-haptico.mp4` — activar hápticos en el sistema y presionar “+” en el catálogo.

## Comentario para Jira (sin clave asociada)

> **Polish visual para la presentación**
> Se implementó el splash con el logo de La Casa Blanca usando la SplashScreen API nativa (minSdk 35), animación fade/slide al abrir el carrito y en la primera carga de mesas/catálogo, estado vacío del carrito con ícono y feedback háptico en los botones “+” del catálogo.
> Verificación: `assembleDebug` OK, 71 tests OK, detekt OK y lint OK.
> Commit: `769144f`.
> Pendiente: validación visual en emulador/tablet y captura de las animaciones (el agente no ve la pantalla).
