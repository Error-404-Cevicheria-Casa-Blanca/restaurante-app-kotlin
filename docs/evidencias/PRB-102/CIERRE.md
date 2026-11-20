# PRB-102 — App: perfil desde `/auth/me`

**Fecha:** 2026-11-20
**Repo:** `repos/restaurante-app-kotlin`
**Rama:** `feature/PRB-102-auth-me` (desde `develop` `00c530b`)
**Commit:** pendiente de revisión (`feat(PRB-102): refrescar perfil desde /auth/me`)
**Jira:** PRB-102 · Epic PRB-105 · depende de PRB-85 (en TEST)

## Checklist

| Criterio | Código | Evidencia | Estado |
|---|---|---|---|
| DTO `PerfilResponseDto` con `usuario` y `fecha_hora_servidor` | `AuthDtos.kt:30-34` | `01-testDebugUnitTest-detekt-assemble.txt` (EXIT=0), `02-test-totales.txt` | **CUMPLE** |
| `GET api/v1/auth/me` en `AuthApi` | `AuthApi.kt:19-20` | `01...`, `02...` | **CUMPLE** |
| `refrescarPerfil()` + `PerfilActualizado` en `AuthRepository` | `AuthRepository.kt:7-21` | `01...` | **CUMPLE** |
| Repositorio real conserva el token y mapea usuario/turno/terminal | `RealAuthRepository.kt:83-104` | `RealAuthRepositoryTest.kt:36`, `02-test-totales.txt` (3/3) | **CUMPLE** |
| 401 → `SESION_EXPIRADA`; `IOException` → `RED` | `RealAuthRepository.kt:105-108` | `RealAuthRepositoryTest.kt:74,87`, `02-test-totales.txt` | **CUMPLE** |
| Fake devuelve la sesión actual con fecha simulada | `FakeAuthRepository.kt` | `01...`, `02...` | **CUMPLE** |
| Estado con `actualizando` y aviso de red | `PerfilUiState.kt` | `01...`, `PerfilViewModelTest.kt:130,161` | **CUMPLE** |
| ViewModel pinta la sesión local y luego refresca; red conserva y avisa; 401 navega | `PerfilViewModel.kt:51-126` | `PerfilViewModelTest.kt:130-188`, `02-test-totales.txt` (6/6) | **CUMPLE** |
| Fecha visible del servidor con respaldo local | `PerfilViewModel.kt:73,119-126` | `PerfilViewModelTest.kt:130` (`21/11/2026`) | **CUMPLE** |
| `PerfilFragment` pinta el progreso y el aviso Marea | `PerfilFragment.kt:83-84`, `fragment_perfil.xml` y `layout-sw600dp/fragment_perfil.xml` | `01...` (compila) | **CUMPLE (código)** |
| Tests de serialización/mapeo, repositorio y ViewModel | `PerfilResponseMapeoTest.kt`, `RealAuthRepositoryTest.kt`, `PerfilViewModelTest.kt` | `02-test-totales.txt` (`TESTS=290`, 0 fallos) | **CUMPLE** |
| `clean testDebugUnitTest detekt assembleDebug` | — | `01-testDebugUnitTest-detekt-assemble.txt` (`BUILD SUCCESSFUL`, `EXIT=0`) | **CUMPLE** |
| Validación visual en emulador/tablet | — | — | **NO VERIFICADO** |
| Llamada real a `/auth/me` contra backend levantado | — | — | **NO VERIFICADO** |

## Evidencias crudas

- `01-testDebugUnitTest-detekt-assemble.txt`: salida completa de
  `.\gradlew.bat clean testDebugUnitTest detekt assembleDebug --console=plain`;
  incluye `:app:detekt`, `BUILD SUCCESSFUL` y `EXIT=0`.
- `02-test-totales.txt`: totales leídos de `app/build/test-results/testDebugUnitTest`
  (`RESULTADOS=37`, `TESTS=290`, `FAILURES=0`, `ERRORS=0`, `SKIPPED=0`).
- `03-git-diff-stat.txt`: salida cruda de `git status --short --branch`,
  `git diff --stat` y `git diff --numstat`.

## NO VERIFICADO

- **UI real:** el progreso indeterminado y el banner de red se agregaron a los
  layouts móvil y `sw600dp`, pero no se ejecutaron en emulador ni tablet. La
  validación visual queda para PRB-46.
- **Backend en ejecución:** no se hizo una petición HTTP real a
  `/api/v1/auth/me`; el contrato se tomó del código verificado
  (`AuthController.java:39`, `AuthService.java:136-201`) y se cubrió con tests
  de repositorio con API mockeada.
- **PRB-100:** no está mergeado en `develop`; no se usó ni se tocó su rama.

## Seguridad

- No se agregaron secretos ni se registró el token en logs.
- El token se conserva desde la sesión activa y se re-persiste con
  `SessionManager.iniciar`; no se hace re-login.
