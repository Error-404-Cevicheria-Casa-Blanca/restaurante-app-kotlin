# CIERRE — Icono de app y recorte del Splash Screen

- **Fecha:** 2026-11-05
- **Repositorio Android:** `repos/restaurante-app-kotlin`
- **Rama:** `feature/polish-presentacion`
- **Commit de código:** `a378afc fix(polish-presentacion): corregir icono de app y recorte del splash`
- **Alcance:** solo recursos de icono adaptativo, drawable del logo y tema del splash. No se tocaron Activities, ViewModels, repositorios ni dependencias.

## Problema corregido

1. El launcher mostraba el icono genérico de Android (robot).
2. El splash mostraba el PNG `logo_casa_blanca.png` a sangre. Como el sistema recorta el tercio exterior del área del icono, se cortaban la parte superior de “CEVICHERÍA” y la inferior de “RESTAURANTE” (ver capturas anteriores `09-splash-b.png` y `09-splash-c.png`).

## Cambios implementados

| # | Criterio | Implementación | Archivos |
|---|---|---|---|
| 1 | Icono adaptativo con el logo de la empresa | Se reemplazaron las capas del robot por `@color/primary` (#0B5563) de fondo y `@drawable/logo_casa_blanca_icon` de foreground, en `mipmap-anydpi-v26`. Se quitó la capa `monochrome` del robot. | `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`, `ic_launcher_round.xml` |
| 2 | Logo sin recortes en el icono y en el splash | `logo_casa_blanca_icon.xml` es un `<inset>` del 19,45 % por lado sobre el PNG original. Deja visible el 61,1 % central: 66dp dentro de la capa adaptativa de 108dp y 146,7dp dentro del círculo visible de 160dp del splash. El PNG original se conserva para el login y la barra de mesas. | `app/src/main/res/drawable/logo_casa_blanca_icon.xml` |
| 3 | Splash sobre el mismo Teal | `windowSplashScreenBackground` y `windowSplashScreenIconBackgroundColor` usan `@color/primary` (#0B5563); `windowSplashScreenAnimatedIcon` usa el drawable con inset. Se corrigió también la variante `values-night`. | `app/src/main/res/values/themes.xml`, `values-night/themes.xml` |
| 4 | PNG con transparencia | Se verificó el canal alpha del PNG: `Format32bppArgb`, esquina `(0,0)` con alfa 0. El círculo visible es full-bleed, de ahí la necesidad del inset. | `11-logo-propiedades.txt` |

## Verificación

| Criterio | Evidencia | Estado |
|---|---|---|
| `assembleDebug` | `01-assembleDebug.txt` — `BUILD SUCCESSFUL in 13s`, `EXIT=0` | **CUMPLE** |
| `testDebugUnitTest --rerun-tasks` | `02-testDebugUnitTest.txt` — `BUILD SUCCESSFUL in 42s`, `EXIT=0` | **CUMPLE** |
| Totales de tests | `03-test-results-totales.txt` — 9 suites, `TOTAL: tests=71 failures=0 errors=0 skipped=0` | **CUMPLE** |
| PNG del logo con alpha | `11-logo-propiedades.txt` — `pixelFormat=Format32bppArgb`, `esquina(0,0)=ARGB(0,0,0,0)` | **CUMPLE** |
| Icono adaptativo empaquetado | `08-aapt2-badging.txt` — `application-icon-*` y `application:` apuntan a `res/mipmap-anydpi-v26/ic_launcher.xml` | **CUMPLE** |
| Splash sin recorte en emulador | `05-splash-fix-1.png` — logo completo, centrado sobre teal; `07-splash-analisis.txt` — 93,30 % de muestras en `#0B5563`, bbox azul con márgenes superior/inferior de 968/975 px | **CUMPLE** (captura; estética final a revisión humana) |
| Icono en el launcher | `10-app-drawer.png` — icono de la app con el logo; `06-app-info-icono.png` y `06-app-info-icono-zoom.png` | **CUMPLE** (launcher circular del emulador; otras máscaras OEM no probadas) |
| Instalación en emulador | `04-install-emulador.txt` — `Success`, `EXIT=0` | **CUMPLE** |
| Lint de recursos | `09-lintDebug.txt` — `BUILD SUCCESSFUL`, `EXIT=0` | **CUMPLE** (extra) |
| Máscaras cuadradas/redondeadas de otros launchers y tablet | No se probaron; la zona segura de 66dp evita el recorte, pero falta revisión visual en esos launchers | **NO VERIFICADO** |
| Iconos legacy `mipmap-*/*.webp` | Siguen siendo los del robot, pero quedan fuera de la selección en minSdk 35 (`aapt2` selecciona `mipmap-anydpi-v26`). No se regeneraron en esta tarea | **FUERA DE ALCANCE / OBSERVACIÓN** |

## ASSUMPTIONS

| ID | Supuesto | Propuesta por defecto |
|---|---|---|
| P1 | La tarea no tiene clave `PE-xx` ni issue Jira; se pidió corregir sobre la rama existente `feature/polish-presentacion`. | Se usó esa rama y el scope `polish-presentacion` en el commit. Si el equipo exige `PRB-xx`, renombrar la rama/commit antes del PR. |
| P2 | El Teal indicado (#0B5563) es `@color/primary`. `@color/stitch_primary` vale `#003D48` y no se usó. | `@color/primary` para fondo del icono, icono del splash y ventana del splash. |
| P3 | El PNG original tiene alpha en las esquinas, pero el círculo azul ocupa todo el lienzo. Usarlo a sangre en el splash/launcher provoca el recorte reportado. | Se creó `logo_casa_blanca_icon.xml` con inset fraccional y se mantuvo `logo_casa_blanca.png` para el resto de pantallas. |
| P4 | “Logo de 108dp” se interpreta como la capa adaptativa de 108dp; el contenido visible del logo se limita a 66dp (zona segura oficial) para no cortar texto. | 61,1 % del lienzo; si se necesita un logo más grande, habría que recortar el arte o aceptar recorte en máscaras. |
| P5 | Los iconos legacy WebP no se usan con `minSdk = 35`. | No se regeneraron para no ampliar el alcance ni añadir binarios; se puede hacer una limpieza posterior. |

## Pendiente de verificación humana

1. Revisar `05-splash-fix-1.png` en el emulador/dispositivo: el logo debe verse completo, centrado y sobre teal, sin “CEVICHERÍA” ni “RESTAURANTE” cortados.
2. Revisar el icono en el launcher del dispositivo objetivo (tablet 8"–10") y con la máscara del fabricante (círculo, cuadrado redondeado, etc.).
3. Confirmar que el color del fondo del icono coincide con el teal de marca en el dispositivo.
4. Si el equipo quiere eliminar por completo el robot de los recursos legacy, regenerar los WebP de `mipmap-*` en una tarea aparte.

## Comentario para Jira (sin clave asociada)

> **Corrección de icono de app y splash**
> Se reemplazó el icono adaptativo del robot por el logo de La Casa Blanca sobre fondo teal `#0B5563` (`mipmap-anydpi-v26`). Para el splash y el icono se usa `logo_casa_blanca_icon`, una variante con inset del 19,45 % que deja el logo en la zona segura y evita el recorte de “CEVICHERÍA” y “RESTAURANTE”; el fondo del icono del splash es el mismo teal.
> Verificación: `assembleDebug` OK, `testDebugUnitTest --rerun-tasks` OK (71 tests, 0 fallos), `lintDebug` OK. Instalación y capturas en emulador `Medium_Phone`: splash completo y logo visible en el launcher. Capturas y análisis en `docs/evidencias/polish-presentacion/icono-splash/`.
> Commit: `a378afc`.
> Pendiente: validar el icono en la tablet y en la máscara del launcher del fabricante.
