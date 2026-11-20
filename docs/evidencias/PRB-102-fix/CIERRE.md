# CIERRE — PRB-102 fix: cerrar sesión al expirar el token en el perfil

Fecha: 20/11/2026
Rama: `feature/PRB-102-auth-me` (PR #36, base `develop`)
Repo: `repos/restaurante-app-kotlin`

## Criterios de aceptación

| Criterio | Archivo:línea | Evidencia | Estado |
|---|---|---|---|
| Un `SESION_EXPIRADA` borra la sesión persistida antes de navegar al login | `app/src/main/java/com/lacasablanca/mesero/ui/perfil/PerfilViewModel.kt:92-95` | `03-perfil-viewmodel-tests.txt` (test PASS) + `01-clean-testDebugUnitTest-detekt-assemble.txt` | CUMPLE |
| La limpieza no usa el método público `cerrarSesion()` (no emite `SesionCerrada` ni cambia `cerrandoSesion`) | `PerfilViewModel.kt:104-117` (helper privado) vs. `PerfilViewModel.kt:148-159` (público intacto) | `01-clean-testDebugUnitTest-detekt-assemble.txt` (compila) | CUMPLE |
| `RED` conserva la sesión y solo enciende el aviso; `DESCONOCIDO` no navega | `PerfilViewModel.kt:98-100` | `03-perfil-viewmodel-tests.txt` (`un fallo de red conserva la sesion local y avisa` PASS) | CUMPLE |
| No se introduce el campo `sincronizacion` (no existe en esta rama) | — | Búsqueda en `app/src`: sin campo en `PerfilViewModel` | CUMPLE |
| Test nuevo: `un 401 cierra la sesion y emite IrALogin`, verifica `cerrarSesion()` 1 vez y `IrALogin` | `app/src/test/java/com/lacasablanca/mesero/ui/perfil/PerfilViewModelTest.kt:193-207` | `03-perfil-viewmodel-tests.txt` (test PASS) | CUMPLE |
| Tests existentes conservados (sin borrar ni debilitar) | `PerfilViewModelTest.kt:178-191` (401 previo, solo se stubbea `cerrarSesion`) | `03-perfil-viewmodel-tests.txt` (7/7 PASS) | CUMPLE |
| `clean testDebugUnitTest detekt assembleDebug` termina en verde | `repos/restaurante-app-kotlin` | `01-clean-testDebugUnitTest-detekt-assemble.txt` (`BUILD SUCCESSFUL`, `EXIT=0`) | CUMPLE |
| Suite completa en verde | `app/build/test-results/testDebugUnitTest/*.xml` | `02-test-totales.txt` (`TOTAL suites=37 tests=291 failures=0 errors=0 skipped=0`) | CUMPLE |
| Validación de UI real (bucle perfil → login → mesas) | — | No hay emulador/tablet en este entorno | NO VERIFICADO |

## Totales de tests

- Suite completa: **291 tests**, 0 fallos, 0 errores, 0 omitidos (37 suites).
- `PerfilViewModelTest`: **7 tests**, 0 fallos. Incluye el nuevo caso `un 401 cierra la sesion y emite IrALogin`.

## No verificado

- Flujo visual en emulador/tablet: no se ejecutó UI real. Queda para PRB-46.

## Notas

- El orden es `authRepository.cerrarSesion()` → actualizar estado → emitir `IrALogin`, de modo que `LoginViewModel.comprobarSesionExistente()` ya no encuentra la sesión vieja.
- Si el borrado local lanza una excepción, se ignora y la pantalla vuelve al login igualmente (mismo criterio que el cierre de sesión manual).
