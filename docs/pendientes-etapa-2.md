# Pendientes — Etapa 2 de la reconstrucción de la UI

Lista de lo que queda pendiente al cerrar la **etapa 1** (2026-10-06, último commit `95e1e1f`).
En una nueva sesión se revisa **punto por punto** y se toma una decisión para cada uno: hacerlo
(y cómo), postergarlo o descartarlo. La decisión se anota en el campo **Decisión** de cada punto.
Lo que se implemente se registra, como siempre, en el "Registro de avances" de
[plan-reconstruccion-ui.md](plan-reconstruccion-ui.md).

**Estado de cada punto:** ⬜ sin decidir · ✅ hacer · ⏸️ postergar · ❌ descartar

---

## A. Imprescindibles para usar la app con mando

### 1. Pantalla de Estados guardados ❌ (descartada)
- **Qué falta:** si el juego tiene saves de RetroArch, "Jugar" debería llevar primero a una
  pantalla con los estados guardados (el primer elemento siempre es **"Juego nuevo"**). Hoy
  "Jugar" lanza el juego siempre directo.
- **Regla del spec:** Sistemas → Juegos → Estados guardados (solo si el juego tiene saves; si no,
  lanza directo).
- **Lo que ya existe:** `SaveStatesViewModel`, `SaveStateRepository`,
  `GamesViewModel.onGameSelected` (decide entre ir a saves o lanzar), `GameLauncher.launch(…,
  saveStateSlot)`. Referencia: `docs/ui-legacy/ui/screens/savestates/`.
- **A decidir:** ¿pantalla completa (otra `Screen` en la grilla, con miniaturas) o dentro del
  modal del juego? ¿Qué muestra cada estado (miniatura, fecha, número de slot)?
- **Decisión (2026-10-13):** se **elimina** la pantalla y la función. Es demasiada complejidad para el usuario
  que solo quiere encender la consola y jugar (además nunca se logró activar bien). "Jugar" lanza siempre directo;
  los estados los gestiona el propio emulador. Se quitó del código: `SaveStatesViewModel`, `SaveStateRepository`,
  `SaveStateEntry`, `GamesViewModel.onGameSelected`, el parámetro `saveStateSlot` (y el extra `STATE_SLOT` de
  RetroArch) y el ajuste "Estados guardados" de Configuración > Carpetas (`retroArchSavesUri`; si `ginny.cfg`
  ya traía esa clave, se conserva sin usarse). En el punto 5, «y Estados guardados» ya no aplica.

### 2. Navegar la Configuración con la cruceta ✅ (cerrado)
- **Qué falta:** dentro de las opciones (filas, desplegables, botones, deslizadores) solo se puede
  tocar. Con la cruceta solo se cambia de sección. En una consola sin pantalla táctil la
  Configuración no se puede usar.
- **A decidir:** cómo se pasa de la barra lateral a las opciones (¿derecha/izquierda?), cómo se ve
  la opción enfocada (¿mismo cursor de la grilla? ¿resaltado de la fila?), qué hace A en cada
  control (abrir desplegable, mover deslizador con izquierda/derecha, etc.), y que el panel se
  desplace solo hasta la opción enfocada.
- **Decisión (2026-10-13):** cerrado. La Configuración ya se navega con el mando y el usuario no ha detectado
  puntos muertos. Si aparece algún caso, entra como soporte, no como pendiente de diseño.

### 3. Mapeo de botones ⏸️ (pospuesto)
- **Qué falta:** asignar las teclas físicas (A, B, Y, Menú, L, R, cruceta). Es la única opción de
  Configuración del diseño anterior que todavía no tiene lugar en la UI nueva (regla: "todo debe
  seguir existiendo").
- **Lo que ya existe:** `SettingsViewModel.startListeningForButton / onKeyCaptured /
  cancelListening / resetButtonMapping`, `LogicalButton`, `ButtonMapping`. Referencia: la pestaña
  de botones en `docs/ui-legacy/ui/screens/settings/SettingsScreen.kt`.
- **A decidir:** ¿grupo dentro de Interfaz > Botones (junto a "Distribución") o sección propia?
  ¿Cómo se ve el modo "presiona un botón…"?
- **Decisión (2026-10-13):** no se hace pantalla de mapeo por ahora. Android ya entrega códigos estándar (A=96, B=97, X=99, Y=100…)
  y las consolas (AYN, Retroid, Anbernic) los respetan; su opción AB/XY ↔ BA/YX solo cambia qué botón físico emite cada
  código, y Ginny ya lo cubre con Interfaz > Botones > Distribución. Si aparece una casuística real (un mando que no
  siga el estándar), se implementa entonces: la lógica ya existe en `SettingsViewModel` y `ginny.cfg` conserva `buttonMapping`.

### 4. Primer arranque y estados vacíos 🟨 (bienvenida hecha; estados vacíos pendientes)
- **Qué falta:** sin carpetas configuradas o sin escanear, la grilla queda vacía sin explicación.
  Tampoco hay mensaje cuando un sistema o Favoritos quedan sin juegos.
- **Lo que ya existe:** `SettingsRepository.onboardingDone`, `isScanning` en los ViewModels
  (distingue "cargando" de "vacío").
- **A decidir:** ¿asistente de primer arranque (elegir carpetas → escanear) o solo un mensaje en
  la zona de contenido con un botón que abra Configuración > Carpetas? Texto de cada caso.
- **Decisión (2026-10-13):** pantalla de **bienvenida** (hecha, sin compilar): mismo fondo y un único `Container` en
  lugar de cabecera/contenido/pie, con texto breve, «Especifique las siguientes carpetas:», botones **Usuario**, **ROMs** y
  **Continuar** (este se habilita al elegir ambas), y una nota de que se pueden cambiar luego en Configuración. Continuar
  limpia el contenido y escanea la biblioteca mostrando los sistemas a medida que aparecen; al terminar se muestra la
  interfaz. Aparece mientras falten carpetas o el escaneo inicial no haya terminado. Detalle en el registro de avances de
  `plan-reconstruccion-ui.md`. **Siguen pendientes** los mensajes para un sistema o Favoritos sin juegos.

---

## B. Reglas establecidas que la UI nueva todavía no cumple

### 5. Y en Juegos abre las opciones del juego ❌ (obsoleto)
- **Qué falta:** en la pantalla de Juegos, **Y** debería abrir el modal del juego (opciones); hoy
  abre Configuración. En Sistemas, Y sigue abriendo Configuración.
- **A decidir:** ¿Y abre el mismo modal que A, o A pasa a lanzar directo y Y abre el modal? Hoy A
  abre el modal y "Jugar" lanza.
- **Decisión (2026-10-14):** obsoleto. Ya no hay «opciones del juego»: el modal que se abre con A lo sustituye. Y abre Configuración en todas las pantallas.

### 6. "Reescanear este sistema" ✅ (hecho, en Configuración)
- **Qué falta:** el modal anterior lo tenía; el nuevo no. `GamesViewModel.rescanSystem` ya existe.
- **A decidir:** ¿se agrega al modal (dónde, táctil o con botón)? ¿O alcanza con que cambiar la
  carpeta en Sistemas ya reescanea?
- **Decisión (2026-10-14):** no va en el modal del juego sino en **Configuración > Carpetas**, debajo de «Escanear biblioteca»: fila «Escanear el sistema <nombre>» con botón «Escanear». El sistema es el de la pantalla de Juegos abierta o, en Sistemas, el seleccionado en la grilla (con Favoritos o sin selección la fila queda apagada y explica qué hacer). Usa `SettingsViewModel.scanSystem` → `rescanSystem` (la misma lógica de siempre) y avisa con un toast. Sin compilar.

### 7. Emulador no instalado ✅ (se queda como está)
- **Qué falta:** hoy solo aparece un aviso (Toast). La regla pide mostrar el selector de emulador
  con un mensaje de error (y, si existe, enlace a la tienda: `LaunchResult.EmulatorNotInstalled`
  trae `storeUrl`).
- **A decidir:** ¿mensaje dentro del mismo modal del juego, resaltando el desplegable de
  Aplicación? ¿Botón "Ir a la tienda"?
- **Decisión (2026-10-14):** se queda como está: basta con el toast. El usuario elige otro emulador de la lista (en el modal del juego o en Configuración > Sistemas) o lo instala por su cuenta. Sin selector de error ni enlace a la tienda.

### 8. Acceso táctil a Configuración ✅ (cerrado)
- **Qué falta:** la regla pide un acceso a Configuración en la barra superior (junto a hora y
  batería). Hoy solo se abre con Menú / Y.
- **A decidir:** ¿ícono de engranaje (`ic_gear` ya existe) dentro del Container de hora/batería o
  aparte?
- **Decisión (2026-10-14):** cerrado. Configuración se abre con Y (y con Menú) y tocando la leyenda del pie, sin ícono nuevo en el header. Corrección: esa leyenda decía «Opciones»; ahora dice **«Configuración»** (`GinnyFooter`).

### 9. Batería oculta si sus datos no son confiables ✅ (verificado y corregido)
- **Qué falta:** verificar en la UI nueva que la batería se oculta cuando `BatteryObserver` indica
  datos no confiables (regla del spec).
- **A decidir:** solo verificar / corregir.
- **Decisión (2026-10-14):** se verificó por lectura del código que la UI oculta el ícono y el porcentaje cuando `BatteryObserver` no da un valor. Se corrigió un hueco: ahora también se oculta si el sistema dice que **no hay batería** (`EXTRA_PRESENT` = false), caso típico de un mini PC o TV box con Android que reporta «100 % enchufado». No se tocó el resto (la regla de los 10 minutos solo se evalúa al llegar avisos, y «no confiable» es permanente hasta reiniciar): el spec no lo pide. Probar con `adb shell dumpsys battery set present 0` (y `reset` al terminar). Sin compilar ni probar en dispositivo.

---

## C. Mejoras anotadas durante la etapa 1

### 10. Desplegable propio ✅ (hecho, a probar)
- **Problema:** el `DropdownMenu` de Material abre una ventana emergente que hace aparecer la
  barra de navegación de Android.
- **A decidir:** ¿hacer un desplegable propio dentro de la misma ventana (también facilitaría
  manejarlo con la cruceta, punto 2)?
- **Decisión (2026-10-14):** hacerlo. `ui/components/GinnyMenu.kt`: menú propio que se dibuja en la misma ventana (capa `GinnyMenuHost`, último hijo de `GinnyApp`, encima de todo incluidos los modales). Sustituye al `DropdownMenu` de Material en `GinnyDropdown` (9 usos) y en el menú «Más» del modal del juego. B lo cierra por su `BackHandler`; `GinnyMenus.isOpen` evita que Menú / Y abran Configuración encima. Sin compilar.

### 11. Búsqueda de logos más rápida ✅ (opción A hecha y medida)
- **Problema:** `SystemsViewModel.systemLogos` busca los logos uno por uno vía SAF y tarda más de
  1 s al abrir la app (la grilla espera a tenerlos).
- **A decidir:** ¿buscar en paralelo, guardar en caché (Room/DataStore) o indexar la carpeta
  Assets una sola vez?
- **Decisión (2026-10-14):** opción A: `AssetRepository.systemLogoUris` lista la raíz y `Assets` **una sola vez** y busca `system.png` de cada sistema **en paralelo** (antes: ~84 consultas SAF en serie, con los dos primeros listados repetidos 28 veces). Lo usan `SystemsViewModel` y `ScrapViewModel`. Medido por el usuario en su consola: ≈ la tercera parte del tiempo anterior (conforme; el log temporal de medición se quitó). Siguientes pasos posibles, no necesarios: caché entre arranques (B) o ruta directa (C).

### 12. Transición entre pantallas ✅
- **Qué falta:** el cambio Sistemas ↔ Juegos es instantáneo.
- **A decidir:** ¿fundido, deslizamiento, zoom desde el elemento abierto? Cuidar el rendimiento
  (regla: hardware de gama baja).
- **Decisión (2026-10-14):** fundido de 180 ms (solo opacidad, barato en gama baja) entre Sistemas y Juegos, con un crecimiento 0.97 → 1 al entrar a Juegos; `AnimatedContent` en `GinnyApp` con `contentKey` por sistema. La pantalla que sale no responde a la cruceta. Conforme el usuario.

### 13. Sección Sistemas más fácil de recorrer ⬜
- **Problema:** son ~30 grupos y hay que desplazarse mucho.
- **A decidir:** ¿índice/salto entre sistemas, mostrar solo los que tienen juegos (con opción de
  ver todos), grupos plegables, o elegir el sistema en una lista y ver sus opciones al lado?
- **Decisión:**

---

## D. Limpieza al terminar la reconstrucción

### 14. Retirar el respaldo y actualizar la documentación ⬜
- **Qué falta:** borrar `docs/ui-legacy/` cuando ya no haga falta como referencia, y actualizar
  `docs/architecture.md` y `../docs/ginny-frontend.md` con la estructura nueva (`Screen`,
  `Content` genérico, `SystemsScreen` / `GamesScreen`, `SettingsModal`, `GameModal`, temas).
  También quitar de `CLAUDE.md` la nota de "UI en reconstrucción" cuando corresponda.
- **A decidir:** cuándo (¿al terminar la etapa 2?).
- **Decisión:**

---

## Orden sugerido (histórico; 1 y 2 cerrados el 2026-10-13)

Empezar por **1** (estados guardados) y **2** (cruceta en Configuración): sin ellos el flujo con
mando no está completo. Después **3** (mapeo de botones) y **4** (primer arranque). El **10**
conviene decidirlo junto con el **2**, porque un desplegable propio simplifica la navegación con
cruceta.
