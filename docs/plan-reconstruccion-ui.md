# Plan de reconstrucción de la UI (guiada paso a paso)

Iniciado: 2026-09-27.

## Por qué

La interfaz construida hasta el rediseño del 2026-09-25 acumuló demasiados errores y detalles
visuales que no terminaron de convencer. En vez de seguir corrigiendo sobre esa base, se cambia el
proceso de desarrollo: **la UI se reconstruye desde cero, elemento por elemento, guiada por el
usuario**. La capa de datos, lanzadores, audio y ViewModels **no** se reescriben; solo la parte
visual (Compose).

## Cómo se trabaja

1. El usuario pide **un solo elemento** por vez (ej. "el fondo aurora", "la barra superior", "un tile").
2. Claude lo implementa en el código nuevo, **mínimo y aislado**, sin adelantarse a elementos que
   todavía no se pidieron.
3. El usuario lo revisa en el dispositivo y puede ajustar el código directamente o pedir cambios.
4. Recién cuando el elemento queda como le gusta, se pasa al siguiente.

En cada paso, Claude debe:

- **Consultar el backup** ([ui-legacy/](ui-legacy/)) para ver cómo se había resuelto antes ese
  mismo elemento, qué opciones tenía y qué reglas respetaba.
- **Avisar proactivamente** si la nueva implementación omite una opción/función que existía antes,
  o rompe una regla ya establecida (ver checklist abajo). Avisar no es imponer: el usuario puede
  decidir descartar la regla a propósito; en ese caso se anota en el registro de avances.
- No copiar el código legado a ciegas: sirve de referencia, no de plantilla. Lo que no gustaba era
  justamente cómo quedó.

## Estado actual del código

- **Punto de entrada de la UI nueva**: [`app/src/main/java/com/ginny/frontend/ui/GinnyApp.kt`](../app/src/main/java/com/ginny/frontend/ui/GinnyApp.kt)
  — hoy es solo un `Box` a pantalla completa con fondo `#E3F5ED` (el `bg0` del tema Menta:
  `oklch(0.955, 0.022, 168)`).
- **`MainActivity.kt`**: conserva modo inmersivo, pantalla siempre encendida y `SoundManager`
  (la música de fondo sigue sonando). Se quitaron temporalmente el tema, la navegación y el manejo
  global de B/Menu en `dispatchKeyEvent` (se reincorporan cuando haya pantallas).
- **Se conservaron en `ui/`** (lógica, no visual): los 4 ViewModels
  (`SystemsViewModel`, `GamesViewModel`, `SaveStatesViewModel`, `SettingsViewModel`),
  `GamesScope`, `SystemTileItem` (modelo sellado que usa `SystemsViewModel`) y `ThemeMode`
  (lo usa `SettingsRepository`).
- **Se eliminaron**: `ui/components/`, `ui/common/`, `ui/navigation/`, todos los `*Screen.kt` y
  el resto de `ui/theme/` (paleta OKLCH, tipografía, Ciclo, sigla/matiz por sistema).
- Las fuentes M PLUS Rounded 1c siguen en `res/font/` (sin uso por ahora).

## El backup: `docs/ui-legacy/`

Copia exacta del estado de la UI al 2026-09-27 (incluye cambios que no estaban commiteados en ese
momento). **No se compila** (está fuera de `app/src`) y **no se modifica**: es de solo consulta.

| Ruta en el backup | Qué había |
|---|---|
| `MainActivity.kt` | Manejo global de B (cierra Configuración → si no, `popBackStack`) y Menú (abre Configuración) vía `dispatchKeyEvent` + `ButtonMapping` |
| `ui/navigation/GinnyNavHost.kt` | Rutas `systems → games/{systemId}`, `games/favorites`, `saves`; Configuración como overlay con blur del fondo |
| `ui/theme/` | `OklchColor`, `GinnyPalette` (toda la paleta desde un solo hue), `ThemeMode`, `CycleHue` (modo Ciclo), `Type` (M PLUS Rounded 1c), `SystemHue`, `SystemShortCode` |
| `ui/components/` | `PagedFocusGrid` + `FocusGridMath` (grid paginado D-Pad), `Tile`, `TopBar`, `BottomBar`, `GlassSurface`, `AuroraBackground`, `ButtonBadge`, `SegmentedPillPicker`, `GinnySlider`, `PillDropdown`, `PageJumpButton`, `SystemLogo`, `GridPanel` |
| `ui/common/` | `GinnyModalSurface`, `GameOptionsDialog`, `EmulatorConfigDialog`, `ScanProgressDialog`, `GameCountLabel` |
| `ui/screens/*/` | `SystemsScreen`, `GamesScreen`, `SaveStatesScreen`, `SettingsScreen` (+ copias de los ViewModels) |
| `res/` | `themes.xml`, `colors.xml` de ese momento |

La maqueta de diseño que originó esa versión sigue en `docs/diseño/Ginny Frontend.dc.html`, y la
anterior en `docs/mockup-visual.html`.

## Checklist de reglas ya establecidas

Resumen para detectar omisiones. La fuente de verdad sigue siendo
[prompt-frontend-emuladores-3ds.md](prompt-frontend-emuladores-3ds.md) y
[architecture.md](architecture.md).

### Estructura y navegación
- Pantallas: Sistemas → Juegos de un sistema → Estados guardados (solo si el juego tiene saves;
  si no, lanza directo). Sin salto directo entre sistemas.
- Tile sintético **Favoritos** en Sistemas, solo si hay al menos un favorito (`SystemTileItem`).
- Estados guardados: el primer ítem siempre es **"Juego nuevo"**.
- **B** retrocede un nivel; con Configuración abierta, B la cierra primero. Se maneja en
  `MainActivity.dispatchKeyEvent` porque debe funcionar sin importar quién tenga el foco.
- **Menú** abre Configuración. **Y** abre Configuración en Sistemas/Estados guardados y el modal
  de Opciones de juego en Juegos.
- Todas las teclas se leen a través de `ButtonMapping` (remapeable), nunca `KEYCODE_*` hardcodeado.

### Grid central
- Elementos **cuadrados**, gap uniforme; filas visibles configurables **1–4**; tamaño =
  `(alto - gap*(filas-1)) / filas`, columnas = las que entren.
- Orden **alfabético**, sin reordenamiento manual.
- Nunca una fila cortada. La versión legada usaba páginas discretas horizontales (L/R saltan
  página, puntos de página en la barra inferior, botones táctiles en los bordes).
- D-Pad navega **solo** el grid (no barras, no el botón B). Izquierda/Derecha sin wrap.
- El foco vive en el ViewModel (`focusedIndex`), no en el componente.
- Tile no enfocado: borde blanco grueso, carátula hasta el borde. Enfocado: borde con degradado de
  acento, glow suave y leve escala; transiciones animadas.
- Sonido de foco al mover; sonido de activación al abrir/lanzar (A).

### Barras
- Superior: izquierda título contextual ("Ginny Frontend" en Sistemas, nombre del sistema en
  Juegos/Saves); derecha hora (cada minuto) + batería + acceso a Configuración.
- Batería **se oculta** si los datos no son confiables (`BatteryObserver`).
- Inferior: nombre del elemento enfocado a la izquierda; a la derecha badges de botones (A abrir;
  B Retroceder solo fuera de Sistemas, táctil pero nunca enfocable por D-Pad, badge rojo).
- Barra superior, panel del grid y barra inferior: piezas separadas con gap entre ellas.

### Configuración (todo debe seguir existiendo)
- Filas visibles, carpeta de Usuario, carpeta de ROMs (permiso de **lectura y escritura**),
  carpeta de saves de RetroArch.
- Emulador y core por sistema; override de package de PS2.
- Volumen de música y de efectos (independientes); selección de pista de fondo.
- Mapeo de botones; tema (Menta/Cielo/Durazno/Lila/Ciclo — Ciclo solo anima si está elegido).
- Escanear biblioteca (con diálogo de progreso).

### Opciones de juego (modal, botón Y en Juegos)
- Seleccionar emulador/core, marcar favorito, reescanear este sistema, eliminar juego (confirmación
  de doble toque; borra ROM/carpeta completa, ícono y caché).
- Emulador no instalado al lanzar → mismo selector de emulador con copy de error.

### Estética
- Identidad propia inspirada en 3DS/Wii U, sin assets/tipografías de Nintendo.
- Fondo claro con toque aurora sutil; esquinas redondeadas consistentes; glass sobrio;
  tipografía redondeada; badges circulares para botones físicos.
- Toda la paleta derivaba de un solo hue (OKLCH) para soportar temas y modo Ciclo.
- Rendimiento: hardware de gama baja — evitar animaciones permanentes que recompongan cada frame.

## Registro de avances

Anotar aquí cada elemento terminado y cualquier regla que se decida cambiar o descartar.

| Fecha | Elemento | Notas / reglas modificadas |
|---|---|---|
| 2026-09-27 | Lienzo vacío | Fondo liso `#E3F5ED` (bg0 Menta). UI anterior archivada en `docs/ui-legacy/`. |
| 2026-09-27 | Temas (`ui/theme/GinnyThemes.kt`) | Paletas escritas a mano (4 papeles: background/accent/ink/muted) en vez de generadas por hue. Valores iguales a los del diseño anterior. Ciclo aún no implementado (cae en Menta). `GinnyApp` usa `GinnyThemes.Menta` fijo: todavía no lee el tema de Configuración. |
