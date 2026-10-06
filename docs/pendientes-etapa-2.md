# Pendientes — Etapa 2 de la reconstrucción de la UI

Lista de lo que queda pendiente al cerrar la **etapa 1** (2026-10-06, último commit `95e1e1f`).
En una nueva sesión se revisa **punto por punto** y se toma una decisión para cada uno: hacerlo
(y cómo), postergarlo o descartarlo. La decisión se anota en el campo **Decisión** de cada punto.
Lo que se implemente se registra, como siempre, en el "Registro de avances" de
[plan-reconstruccion-ui.md](plan-reconstruccion-ui.md).

**Estado de cada punto:** ⬜ sin decidir · ✅ hacer · ⏸️ postergar · ❌ descartar

---

## A. Imprescindibles para usar la app con mando

### 1. Pantalla de Estados guardados ⬜
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
- **Decisión:**

### 2. Navegar la Configuración con la cruceta ⬜
- **Qué falta:** dentro de las opciones (filas, desplegables, botones, deslizadores) solo se puede
  tocar. Con la cruceta solo se cambia de sección. En una consola sin pantalla táctil la
  Configuración no se puede usar.
- **A decidir:** cómo se pasa de la barra lateral a las opciones (¿derecha/izquierda?), cómo se ve
  la opción enfocada (¿mismo cursor de la grilla? ¿resaltado de la fila?), qué hace A en cada
  control (abrir desplegable, mover deslizador con izquierda/derecha, etc.), y que el panel se
  desplace solo hasta la opción enfocada.
- **Decisión:**

### 3. Mapeo de botones ⬜
- **Qué falta:** asignar las teclas físicas (A, B, Y, Menú, L, R, cruceta). Es la única opción de
  Configuración del diseño anterior que todavía no tiene lugar en la UI nueva (regla: "todo debe
  seguir existiendo").
- **Lo que ya existe:** `SettingsViewModel.startListeningForButton / onKeyCaptured /
  cancelListening / resetButtonMapping`, `LogicalButton`, `ButtonMapping`. Referencia: la pestaña
  de botones en `docs/ui-legacy/ui/screens/settings/SettingsScreen.kt`.
- **A decidir:** ¿grupo dentro de Interfaz > Botones (junto a "Distribución") o sección propia?
  ¿Cómo se ve el modo "presiona un botón…"?
- **Decisión:**

### 4. Primer arranque y estados vacíos ⬜
- **Qué falta:** sin carpetas configuradas o sin escanear, la grilla queda vacía sin explicación.
  Tampoco hay mensaje cuando un sistema o Favoritos quedan sin juegos.
- **Lo que ya existe:** `SettingsRepository.onboardingDone`, `isScanning` en los ViewModels
  (distingue "cargando" de "vacío").
- **A decidir:** ¿asistente de primer arranque (elegir carpetas → escanear) o solo un mensaje en
  la zona de contenido con un botón que abra Configuración > Carpetas? Texto de cada caso.
- **Decisión:**

---

## B. Reglas establecidas que la UI nueva todavía no cumple

### 5. Y en Juegos abre las opciones del juego ⬜
- **Qué falta:** en la pantalla de Juegos, **Y** debería abrir el modal del juego (opciones); hoy
  abre Configuración. En Sistemas y Estados guardados, Y sigue abriendo Configuración.
- **A decidir:** ¿Y abre el mismo modal que A, o A pasa a lanzar directo y Y abre el modal? Hoy A
  abre el modal y "Jugar" lanza.
- **Decisión:**

### 6. "Reescanear este sistema" en el modal del juego ⬜
- **Qué falta:** el modal anterior lo tenía; el nuevo no. `GamesViewModel.rescanSystem` ya existe.
- **A decidir:** ¿se agrega al modal (dónde, táctil o con botón)? ¿O alcanza con que cambiar la
  carpeta en Sistemas ya reescanea?
- **Decisión:**

### 7. Emulador no instalado ⬜
- **Qué falta:** hoy solo aparece un aviso (Toast). La regla pide mostrar el selector de emulador
  con un mensaje de error (y, si existe, enlace a la tienda: `LaunchResult.EmulatorNotInstalled`
  trae `storeUrl`).
- **A decidir:** ¿mensaje dentro del mismo modal del juego, resaltando el desplegable de
  Aplicación? ¿Botón "Ir a la tienda"?
- **Decisión:**

### 8. Acceso táctil a Configuración desde el header ⬜
- **Qué falta:** la regla pide un acceso a Configuración en la barra superior (junto a hora y
  batería). Hoy solo se abre con Menú / Y.
- **A decidir:** ¿ícono de engranaje (`ic_gear` ya existe) dentro del Container de hora/batería o
  aparte?
- **Decisión:**

### 9. Batería oculta si sus datos no son confiables ⬜
- **Qué falta:** verificar en la UI nueva que la batería se oculta cuando `BatteryObserver` indica
  datos no confiables (regla del spec).
- **A decidir:** solo verificar / corregir.
- **Decisión:**

---

## C. Mejoras anotadas durante la etapa 1

### 10. Desplegable propio ⬜
- **Problema:** el `DropdownMenu` de Material abre una ventana emergente que hace aparecer la
  barra de navegación de Android.
- **A decidir:** ¿hacer un desplegable propio dentro de la misma ventana (también facilitaría
  manejarlo con la cruceta, punto 2)?
- **Decisión:**

### 11. Búsqueda de logos más rápida ⬜
- **Problema:** `SystemsViewModel.systemLogos` busca los logos uno por uno vía SAF y tarda más de
  1 s al abrir la app (la grilla espera a tenerlos).
- **A decidir:** ¿buscar en paralelo, guardar en caché (Room/DataStore) o indexar la carpeta
  Assets una sola vez?
- **Decisión:**

### 12. Transición entre pantallas ⬜
- **Qué falta:** el cambio Sistemas ↔ Juegos es instantáneo.
- **A decidir:** ¿fundido, deslizamiento, zoom desde el elemento abierto? Cuidar el rendimiento
  (regla: hardware de gama baja).
- **Decisión:**

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

## Orden sugerido

Empezar por **1** (estados guardados) y **2** (cruceta en Configuración): sin ellos el flujo con
mando no está completo. Después **3** (mapeo de botones) y **4** (primer arranque). El **10**
conviene decidirlo junto con el **2**, porque un desplegable propio simplifica la navegación con
cruceta.
