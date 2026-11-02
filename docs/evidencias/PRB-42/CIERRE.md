# CIERRE — PRB-42 Polling automático de mesas

**Fecha:** 2026-11-02
**Rama:** `feature/PRB-42-polling-mesas` (desde `develop` @ `f9fc92b`)
**Alcance:** refresco automático del mapa de mesas cada 4 segundos entre tablets.

## 1. Criterios → evidencia → estado

| Criterio del brief | Referencia de código | Evidencia | Estado |
|---|---|---|---|
| `startPolling()` / `stopPolling()` idempotentes con un único `Job` en `viewModelScope` | `MesasViewModel.kt:75-91` (guard `pollingJob?.isActive`, cancelación y `pollingJob = null`) | `05-referencias-codigo.txt`; test «llamar dos veces a startPolling no duplica el Job» en `04-casos-prueba-nuevos.txt` | CUMPLE |
| Bucle `while (isActive) { cargarMesas(silencioso); delay(4000) }` con el `delay` al final | `MesasViewModel.kt:79-81` | `05-referencias-codigo.txt`; test «el segundo ciclo llega a los 4 segundos» | CUMPLE |
| `SharedFlow<String>` `eventosError` para el Snackbar si falla la red, sin romper el bucle | `MesasViewModel.kt:50-51` y `242` (`tryEmit(ERROR_RED)` dentro de `manejarFalloCarga`) | `05-referencias-codigo.txt`; test «si el polling falla por red conserva la lista y avisa una vez» | CUMPLE |
| Una sola carga inicial: `init` resuelve la sesión pero no pide mesas (A28 resuelta) | `MesasViewModel.kt:210-226` (bloque `iniciar()` sin llamada a `cargarMesas`) | `05-referencias-codigo.txt` (bloque `iniciar()` y ocurrencias de `cargarMesas`); test «el polling hace la carga inmediata al arrancar» (`exactly = 0` antes de `startPolling`) | CUMPLE |
| Carga silenciosa con `ListAdapter` + `DiffUtil` / `submitList`, sin recrear el adapter | `MesaAdapter.kt:21` y `75`; `MesasActivity.kt:84,87,178` (el adapter se crea una sola vez en `onCreate` y solo se hace `submitList`) | `05-referencias-codigo.txt` | CUMPLE (estático) |
| `onStart()` → `startPolling()`; `onStop()` → `stopPolling()` | `MesasActivity.kt:109-116` | `05-referencias-codigo.txt` | CUMPLE (estático) |
| Recolectar `eventosError` con `repeatOnLifecycle(STARTED)` y mostrar Snackbar | `MesasActivity.kt:97-100` y `389` (`mostrarErrorPolling` → `mostrarSnackbar`) | `05-referencias-codigo.txt` | PARCIAL: el código está conectado; el Snackbar en pantalla **NO VERIFICADO** (sin emulador) |
| `MesasViewModelPollingTest` con `StandardTestDispatcher` y `advanceTimeBy(4000)` | `MesasViewModelPollingTest.kt` (5 casos) | `04-casos-prueba-nuevos.txt` | CUMPLE |
| La suite pasa de 66 a ≥ 71 tests sin fallos | 9 suites | `02-testDebugUnitTest.txt` (`EXIT=0`) y `03-test-results-totales.txt` (`tests=71 failures=0 errors=0 skipped=0`) | CUMPLE |
| Compilación del APK de depuración | — | `01-assembleDebug.txt` (`BUILD SUCCESSFUL`, `EXIT=0`) | CUMPLE |
| `USE_FAKE_DATA` sin tocar | `app/build.gradle.kts:24,31` | `06-rama-y-cambios.txt` | CUMPLE |

## 2. Casos de prueba añadidos (5)

- Carga inmediata al arrancar el bucle (sin avanzar el reloj) y `init` sin peticiones.
- Segunda carga exactamente a los 4000 ms.
- `stopPolling()` cancela y no vuelve a consultar tras 12 s.
- Fallo de red del polling: conserva la lista, no pinta error de pantalla y emite un único aviso (y no lo repite en el fallo siguiente).
- Doble `startPolling()` no duplica el `Job`.

> **Ajuste tras resolver A28:** al retirar la carga de `init`, el helper `crearViewModel()` de
> `MesasViewModelTest` dispara `cargar()` para que esos 15 tests sigan probando lo mismo que antes
> (no se eliminó ni relajó ninguna aserción). En `MesasViewModelPollingTest` las expectativas de
> invocaciones bajan en uno porque ya no existe la lectura previa del constructor. La suite sigue en
> **71 tests, 0 fallos** (`03-test-results-totales.txt`).

## 3. Comandos ejecutados (salida cruda en esta carpeta)

| Archivo | Comando |
|---|---|
| `00-compileDebugKotlin.txt` | `.\gradlew.bat compileDebugKotlin --rerun-tasks` |
| `01-assembleDebug.txt` | `.\gradlew.bat assembleDebug` |
| `02-testDebugUnitTest.txt` | `.\gradlew.bat testDebugUnitTest --rerun-tasks` |
| `03-test-results-totales.txt` | lectura de `app/build/test-results/testDebugUnitTest/*.xml` |
| `04-casos-prueba-nuevos.txt` | lectura del XML de `MesasViewModelPollingTest` |
| `05-referencias-codigo.txt` | búsqueda de las referencias citadas en este documento |
| `06-rama-y-cambios.txt` | `git branch`, `git status`, `git diff --stat` y `USE_FAKE_DATA` |
| `07-commit.txt` | `git log`, `git status`, `git log develop..HEAD` y `git show --stat` de la rama (`24e4d99` y `4525192`) |
| `08-push-y-pr.txt` | `git ls-remote --heads origin`, `git rev-parse HEAD`, `git status -sb` y `gh pr view 7` |

## 4. NO VERIFICADO

- **Snackbar en pantalla y refresco real entre dos tablets/emuladores:** no se abrió la app; este agente no ve la pantalla. Queda para la verificación visual humana y para PRB-46.
- **Comportamiento con `USE_FAKE_DATA = false` contra el backend real:** `debug` sigue con datos falsos; se traslada a PRB-46.
- **Consumo de red/batería con la pantalla en segundo plano en dispositivo físico:** el código detiene el bucle en `onStop`, pero no se midió en hardware.

## 5. ASSUMPTIONS

- **A28 — RESUELTA (02/11/2026):** eliminada la carga de mesas de `init`; la única carga inicial es el primer ciclo de `startPolling()`. Ya no hay GET duplicado al abrir el mapa. Ver §7.
- **A29 (abierta):** el ViewModel no tiene `Context`; `eventosError` emite el código `"RED"` y `MesasActivity` lo traduce con `strings.xml`.
- **A30 (abierta):** se muestra un solo Snackbar por racha de fallos (no cada 4 s) y un fallo silencioso no sustituye la lista ya pintada; si aún no hay mesas, se conserva el panel de error con «Reintentar».

## 6. Comentario para Jira (sin enviar)

> PRB-42 completada. `MesasViewModel` refresca el mapa cada 4 segundos con un único `Job` en `viewModelScope` (arranque idempotente, cancelación en `onStop`) y sin petición duplicada al abrir: el constructor solo resuelve la sesión y el primer ciclo del bucle hace la carga inicial. La carga es silenciosa y usa `submitList` sobre el mismo `ListAdapter`, por lo que se conservan scroll y filtros; si falla la red se emite un `SharedFlow<String>` que la pantalla muestra como Snackbar sin romper el bucle. Añadidos 5 tests con `StandardTestDispatcher` y `advanceTimeBy(4000)`: la suite pasa de 66 a 71 tests, 0 fallos. Evidencia en `docs/evidencias/PRB-42/`. Pendiente de verificación visual entre dos dispositivos en PRB-46.

## 7. Corrección de A28 — una sola carga inicial (02/11/2026)

**Problema:** `init` ejecutaba `iniciar()` → `cargarMesas()`, y `MesasActivity.onStart()` llamaba a `startPolling()`, cuyo primer ciclo volvía a consultar. Al abrir el mapa podían salir dos `GET /mesas`.

**Cambio:** `iniciar()` ahora solo lee la sesión guardada, publica el nombre del usuario y completa `sesionLista`; **no** llama a `cargarMesas()`. El primer ciclo de `startPolling()` es la única carga inicial. `cargar()` sigue disponible para el botón «Actualizar», el reintento y `recargarAlVolver()`.

**Impacto en pruebas (sin debilitarlas):**

- `MesasViewModelTest` (15 tests): el helper `crearViewModel()` dispara `cargar()` tras construir el ViewModel, de modo que cada test conserva su escenario y sus aserciones.
- `MesasViewModelPollingTest` (5 tests): se añadió la comprobación de que el constructor **no** consulta (`coVerify(exactly = 0)`), la carga inmediata se mide con una sola invocación del bucle y el fallo de red se prueba avanzando 4 s.
- Resultado: `.\gradlew.bat testDebugUnitTest --rerun-tasks` → `BUILD SUCCESSFUL`, `EXIT=0` (`02-testDebugUnitTest.txt`); totales `tests=71 failures=0 errors=0 skipped=0` (`03-test-results-totales.txt`).
- Compilación: `assembleDebug` → `BUILD SUCCESSFUL`, `EXIT=0` (`01-assembleDebug.txt`).
- Evidencia del código final: `05-referencias-codigo.txt` (bloque `iniciar()` sin `cargarMesas` y solo tres ocurrencias de `cargarMesas`: definición, `cargar()` y el bucle).
- **Commit de la corrección:** `4525192 refactor(PRB-42): eliminar la carga duplicada del mapa al abrir` (`07-commit.txt`), sobre `24e4d99`. Sin push.
