# PRB-100 — Offline seguro y outbox visible

- **Fecha:** 2026-11-20
- **Repo:** `repos/restaurante-app-kotlin`
- **Rama:** `feature/PRB-100-offline-outbox` (desde `develop` @ `00c530b`)
- **Estado:** implementado y verificado con tests, detekt y `assembleDebug`. **Sin commit y sin push** (a la espera de revisión).

## Qué se hizo

| # | Cambio | Archivo |
|---|---|---|
| 1 | Contrato del outbox expone `contar()` y la cola completa | `data/local/offline/OutboxPedidoStore.kt`, `RoomOutboxPedidoStore.kt` |
| 2 | Modelo de pendiente y resumen con detección de conflicto (incluye filas viejas con `HTTP 409`) | `domain/model/PedidoPendiente.kt` |
| 3 | Repositorio de outbox para resumen, sincronización y descarte de conflictos | `data/repository/OutboxRepository.kt` |
| 4 | El worker no sincroniza sin sesión y se cancela al cerrar sesión | `SyncWorker.kt`, `SyncScheduler.kt`, `MesasViewModel.kt`, `PerfilViewModel.kt` |
| 5 | El login vuelve a programar la sincronización | `LoginViewModel.kt` |
| 6 | Sin red no se abre una mesa nueva | `MesasViewModel.kt` |
| 7 | Badge “N pedidos pendientes de sincronizar”, contador de conflictos y reintento manual | `MesasViewModel.kt`, `SalonFragment.kt`, layouts de `fragment_mesas` |
| 8 | 401 detiene la cola y la conserva; 409 queda como conflicto reintentable/descartable | `PedidoSync.kt` |
| 9 | Tests nuevos de offline, outbox, 401 y descarte de conflictos | `app/src/test/**` |

## Checklist contra el alcance de Jira

| Criterio | Implementación | Evidencia | Estado |
|---|---|---|---|
| Banner sin conexión | Ya existía (`SalonFragment.kt`, `layout/fragment_mesas.xml`); se conserva | `03-git-diff-stat.txt` | **CUMPLE** (lógica); visual **NO VERIFICADO** |
| Sin red no se abre una mesa nueva | `MesasViewModel.kt:181-186` bloquea antes del PATCH y emite `AvisoMesas.SIN_CONEXION` | `MesasViewModelOfflineTest > sin red no se abre una mesa nueva`; `02-test-totales.txt` | **CUMPLE** |
| Badge “Pendiente de sincronizar” | `MesasViewModel.kt:543` calcula el resumen; `SalonFragment.kt:328` pinta el badge; `layout/fragment_mesas.xml:54` | `MesasViewModelOfflineTest > el estado expone los pendientes y conflictos del outbox` | **CUMPLE** (lógica); visual **NO VERIFICADO** |
| Reintento manual | `MesasViewModel.kt:435`; botón en `SalonFragment.kt:188` | `MesasViewModelOfflineTest > reintentar pendientes invoca al outbox y avisa el resultado` | **CUMPLE** |
| Pausa del worker al cerrar sesión | `SyncScheduler.kt:56`, `MesasViewModel.kt:469/537`, `PerfilViewModel.kt:90`, `SyncWorker.kt:20` | `MesasViewModelOfflineTest > cerrar sesion cancela el worker de sincronizacion` | **CUMPLE** |
| Manejo de 409 | `PedidoSync.kt:75` marca `CONFLICTO`; `PedidoPendiente.kt:16` lo detecta; se ofrece descartar en `OutboxRepository.kt:24` | `PedidoSyncTest > un 409 se marca como conflicto y se conserva para revision` | **CUMPLE** (lógica) |
| Manejo de 401 | `PedidoSync.kt:71` marca `SESION_EXPIRADA` y detiene la cola conservándola | `PedidoSyncTest > un 401 marca sesion expirada y detiene la cola` | **CUMPLE** (lógica) |
| Pedidos de mesas abiertas visibles en cola y sincronizables | Outbox Room + badge + reintento | `OutboxRepositoryTest` (3), `RoomOutboxPedidoStoreTest` (5), `PedidoSyncTest` (7) | **CUMPLE** (lógica) |
| Evidencias crudas guardadas | Este directorio | `01-...txt`, `02-...txt`, `03-...txt` | **CUMPLE** |

## Verificación ejecutada

```powershell
.\gradlew.bat clean testDebugUnitTest detekt assembleDebug --console=plain
```

- **Resultado:** `BUILD SUCCESSFUL`, `EXIT=0` (`01-testDebugUnitTest-detekt-assemble.txt`).
- **Tests:** 37 suites, **293 tests**, 0 fallos, 0 errores, 0 omitidos (`02-test-totales.txt`).
  - `MesasViewModelOfflineTest`: 6/6.
  - `OutboxRepositoryTest`: 3/3.
  - `PedidoSyncTest`: 7/7.
  - `RoomOutboxPedidoStoreTest`: 5/5.
- **Detekt:** sin hallazgos en la misma corrida.
- **Assemble debug:** `BUILD SUCCESSFUL`; APK en `app/build/outputs/apk/debug/app-debug.apk`.

## NO VERIFICADO

- **UI en tablet/emulador:** no se levantó dispositivo en esta sesión; el badge, los botones y el diálogo de descarte no tienen captura. Corresponde a PRB-46.
- **Backend real:** no había backend ni túnel activos; no se provocó un 401/409 real. El comportamiento está cubierto por tests con dobles.
- **Red real:** `NetworkMonitor` ahora considera “hay red” con `NET_CAPABILITY_INTERNET` (sin exigir `VALIDATED`), porque el backend es LAN. No se probó en la red del restaurante.

## ASSUMPTION

- **A-PRB100-1:** “sin red” significa sin red por defecto con capacidad `INTERNET`. Si el dispositivo está en una WiFi sin salida a internet pero con el servidor local accesible, la app intenta la operación y el error de red se muestra como antes. Registrado aquí para revisión.

## Comandos y notas

- No se hizo `git push`.
- No se versionaron secretos.
- La rama queda lista para revisión; los cambios están sin commitear.
