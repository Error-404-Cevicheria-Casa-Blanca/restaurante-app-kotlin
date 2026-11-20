# Cierre de corrección — PRB-101: login por PIN con sincronización

- **Rama:** `feature/PRB-101-login-pin` (PR #37, base `develop`)
- **Motivo:** resolver el conflicto con `develop` tras el merge de PR #35 (offline/outbox), PR #36 (`/auth/me`) y PR #38 (CI Android), y completar la integración de sincronización en el login por PIN.
- **Commit de cierre:** `fix(PRB-101): programar sincronizacion tras login por PIN`

## 1. Resolución del merge con develop

| Criterio | Archivo:línea | Evidencia | Estado |
|---|---|---|---|
| Se conserva `iniciarSesionPorPin` (PR #37) | `FakeAuthRepository.kt:42-53` | `03-git-diff-stat.txt` | VERIFICADO |
| Se conserva `refrescarPerfil` (PR #36) | `FakeAuthRepository.kt:55-64` | `03-git-diff-stat.txt` | VERIFICADO |
| `LoginActivity` conserva `sincronizacion = contenedor` | `LoginActivity.kt:42-49` | `03-git-diff-stat.txt` | VERIFICADO |
| `LoginViewModel` conserva el parámetro `sincronizacion` | `LoginViewModel.kt:19-23` | `03-git-diff-stat.txt` | VERIFICADO |
| Tests de perfil usan argumento nombrado tras el merge | `PerfilViewModelTest.kt:149,167,185,200` | `01-...txt` (compilación) | VERIFICADO |

El único conflicto real fue `FakeAuthRepository.kt`; el resto se auto-fusionó. El merge también obligó a corregir cuatro llamadas posicionales de `PerfilViewModel(...) { fechaFija }` para que el lambda apunte a `fechaActual` y no a `sincronizacion`.

## 2. Sincronización tras el éxito del PIN

| Criterio | Archivo:línea | Evidencia | Estado |
|---|---|---|---|
| El éxito del PIN llama `programar()` antes de `IrAMesas` | `LoginViewModel.kt:107-112` | `01-...txt`, `02-...txt` | VERIFICADO |
| El fallo del PIN no llama `programar()` | `LoginPinViewModelTest.kt:114-130` | `02-...txt` | VERIFICADO |
| El login por credenciales mantiene su `programar()` | `LoginViewModel.kt:84-89` | `03-git-diff-stat.txt` | VERIFICADO |
| Se agregan 2 tests con `Sincronizacion` mockeada | `LoginPinViewModelTest.kt:93-130` | `02-...txt` | VERIFICADO |

## 3. Flujo PIN revisado

| Criterio | Archivo:línea | Evidencia | Estado |
|---|---|---|---|
| `PinRequestDto` envía `terminal_codigo` | `AuthDtos.kt:14-19` | revisión de código | VERIFICADO |
| 400/401/403/423/IOException mapeados | `RealAuthRepository.kt:69-78` | revisión de código | VERIFICADO |
| Sesión persistida con el token devuelto | `RealAuthRepository.kt:66,89` | revisión de código | VERIFICADO |
| PIN de 4 dígitos se limpia en error con mensaje inline | `LoginViewModel.kt:113-118` | `02-...txt` | VERIFICADO |
| Demo `1234` solo con `USE_FAKE_DATA=true` | `LoginActivity.kt:134-142`, `FakeAuthRepository.kt:83` | revisión de código | VERIFICADO |
| Usuario vacío no llama a la API | `LoginViewModel.kt:99-103` | `02-...txt` | VERIFICADO |
| Ambos layouts declaran `btnIngresarPin` | `layout/activity_login.xml:215`, `layout-sw600dp/activity_login.xml:236` | revisión de código | VERIFICADO |
| Sin bloqueo de orientación | `AndroidManifest.xml:21-23` | revisión de código | VERIFICADO |
| `validarPin` (PRB-81) sin cambios de semántica | `FakeAuthRepository.kt:67-77`, `RealAuthRepository.kt:160+` | `03-git-diff-stat.txt` | VERIFICADO |
| Sin Hilt ni Room nuevos | — | `03-git-diff-stat.txt` | VERIFICADO |

## 4. Verificación ejecutada

Comando:

```powershell
.\gradlew.bat clean testDebugUnitTest detekt assembleDebug --console=plain
```

Resultado: `BUILD SUCCESSFUL`, `EXIT=0`.

- Total de pruebas: **323**, fallos **0**, errores **0**, omitidas **0**.
- Suites PRB-101: `PinRequestDtoTest` (2), `FakeAuthRepositoryPinTest` (3), `RealAuthRepositoryPinTest` (7) y `LoginPinViewModelTest` (9) = **21 pruebas, 0 fallos**.
- Evidencia cruda del build: `01-clean-testDebugUnitTest-detekt-assemble.txt`.
- Conteo crudo desde los XML de JUnit: `02-test-totales.txt`.

## 5. NO VERIFICADO

- UI real en emulador o tablet (no se ejecutó la app en dispositivo en esta sesión).
- Login por PIN contra el backend HTTP real (no se levantó el backend en esta sesión).
- Resultado del CI de GitHub y merge de PR #37 (se verifica después del push).
