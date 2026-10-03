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
| 2026-10-03 | Fondo más poblado | Grilla de formas de 7×4 (28) a 7×5 (35, +25 %; con una grilla no se llega exacto a +20 %: 8×4 = +14 %). Costo de rendimiento despreciable. Subtítulo "1 juego" en singular. |
| 2026-10-03 | Mecánica del cursor + Favoritos | Selección en `SystemsViewModel.focusedIndex` (regla: el foco vive en el ViewModel). `GridNavigation` (aritmética pura): arriba/abajo dentro de la página; izquierda/derecha pasan a la página anterior/siguiente desde la primera/última columna (**distinto del legado**, donde abajo pasaba de página); L/R = página entera. Teclas vía `ButtonMapping` (remapeable); B no se maneja en la grilla. Toque en un elemento = seleccionarlo; toque en las flechas = cambiar de página. Cursor animado (spring) hasta el elemento; al cambiar de página salta. Sonido de foco al moverse (`LocalSoundManager`, provisto en MainActivity). Footer conectado: elemento seleccionado + puntos de página reales (estado elevado a `GinnyApp`). Favoritos: `GridEntry.icon` / `Element(icon)` = ícono de la app chico (45 %) teñido con el acento aclarado (L 0.8). Verificado en dispositivo con la cruceta. |
| 2026-10-03 | Contenido: flechas + grilla (primera página) | `Content` = `PageArrow` izq. (ic_arrow blanco, alfa 0.5 si deshabilitada) · `ElementGrid` · `PageArrow` der. `ElementGrid` (`ui/components/game/`): lado = (alto − gaps) ÷ filas, columnas = las que entren, huecos `empty` para completar la página, gap 24 dp (lugar para el cursor), orden = el de la lista; informa `pageCount`. Datos provisionales: sistemas de `SystemsViewModel` (Favoritos con ic_star, subtítulo "N juegos"), filas desde Configuración. **Pendiente**: cambio de página, conectar `pageCount`/página actual con los puntos del footer, cursor y navegación con cruceta. |
| 2026-10-03 | Puntos de página (`ui/components/PageIndicator.kt`) | `Container` con un punto blanco por página; la actual es una píldora del acento, con ancho y color animados. En el footer, apilado sobre la leyenda a la derecha (16 dp + 4 + 28 dp = 48 dp). Leyenda achicada (ícono 16 dp, texto 12 sp; `Icon` ganó parámetro `size`). Páginas provisionales (4, actual 0): vendrán de la grilla. |
| 2026-10-03 | Aparición de `Element` con fade | Estados `Loading` (invisible) → `Loaded` (imagen) / `Failed` (título); al salir de Loading el elemento completo aparece con fade (`ElementFadeInMs` 300). `systemLogos` ahora es `Map?`: null = todavía buscando, para no mostrar primero el título y después el logo. Pendiente: la búsqueda de los 28 logos es secuencial vía SAF y tarda >1 s en el dispositivo (panel vacío mientras tanto); se podría paralelizar. |
| 2026-10-03 | `Cursor` (`ui/components/game/Cursor.kt`) | 4 esquinas en "L" con curva (Canvas + `arcTo`), extremos rectos, color plano (acento del tema) por ahora, con borde del mismo color aclarado (`CursorBorderWidth` 2dp, `CursorBorderLighten` 0.2; el trazo se dibuja dos veces: ancho y claro debajo, normal encima). Radio por defecto = `ElementCornerRadius`; `CursorStrokeWidth` 4dp, `CursorArmFraction` 0.25. Probado superpuesto al primer elemento en `Content.kt`. Pendiente: que se mueva al elemento seleccionado (animado) y las reglas de foco (degradado, resplandor, escala). |
| 2026-10-03 | `Element` sin selección | Vuelve a marco blanco redondeado (`ElementCornerRadius` 16dp, `ElementFrameWidth` 4dp, constantes públicas). Se quitó `selected`: **la selección la mostrará un cursor aparte que se mueve hacia el elemento elegido** (decisión del usuario). Las reglas de foco del spec (degradado de acento, resplandor, leve escala, transición animada) pasan a aplicarse al cursor. |
| 2026-10-03 | Logos de sistemas | `SystemsViewModel.systemLogos: StateFlow<Map<SystemId, Uri>>` (busca `system.png` de cada sistema en segundo plano; se recalcula si cambia la Carpeta de Usuario). `appContainer()` (`ui/util/`) para crear ViewModels desde un composable. `Content.kt` crea el ViewModel de forma **provisional** para probar; al hacer la pantalla de Sistemas, el ViewModel vive allí. Verificado en dispositivo. |
| 2026-10-01 | `Element` (tile de la grilla, `ui/components/game/`) + `Container(blur)` | Cuadrado de `size: Dp`. `empty` = hueco de relleno (Container pequeñito sin blur, centrado) para que la grilla conserve su forma. Si no: imagen (`image: Uri?`, igual que GameEntry.iconUri / systemLogoUri / SaveStateEntry.thumbnailUri; Coil `AsyncImage`, `Crop`) hasta el marco; sin imagen o si falla la carga, el título. `drawableUri(R.drawable.x)` (`ui/util/`) para recursos de la app. Fondo del tile opaco (con translúcido se veía la sombra del resplandor por dentro). Marco blanco grueso (4dp); `selected` → degradado de acento + resplandor + escala 1.06, animados (reglas del spec). Título/subtítulo no se dibujan en el tile (los muestra el footer). `Container(blur = false)` desactiva el desenfoque. |
| 2026-10-01 | Fondo geométrico animado + blur de `Container` | `GeometricBackground`: degradado 45° de 3 colores: complementario (tono + 180°) → tono + 90° (evita el centro gris) → color del tema, en OKLCH. Formas que orbitan (recorrido 10–22 dp, vuelta cada 7–14 s; con 3–8 dp/12–24 s no se percibía) y giran sobre sí mismas sin parar, "como en el espacio" (sentido y ángulo inicial propios, una vuelta cada 40–90 s; el usuario descartó un balanceo de vaivén) de trazo blanco translúcido (semilla fija, una por celda de una grilla 7×4) que flotan lentamente a ~30 fps; solo repinta, no recompone (`drawWithCache` + tiempo leído al dibujar). `AnimationEnabled` como constante (futuro interruptor en Configuración). `Container` desenfoca lo de atrás con la librería **Haze 0.7.3** (`LocalHazeState`); blur real en API 31+, solo tinte en 29-30. Verificado en dispositivo (API 33). |
| 2026-10-01 | Footer: `SelectionInfo` + leyenda de botones | Izquierda: `Container` con ic_gamepad + título/subtítulo (estáticos, una línea con "…"). Derecha: `ButtonLegend` Abrir/Regresar/Opciones con ic_buttons rotado (`FaceButton`: círculo vacío señala el botón, distribución Nintendo A der./B abajo/X arriba/Y izq.). **Pendiente vs reglas previas**: leyendas no táctiles todavía; "Regresar" debe ocultarse en Sistemas; respetar `ButtonMapping` remapeable. |
| 2026-09-27 | Hora + batería (`ui/components/DeviceStatus.kt`) | Dentro de `Container`, alineado a la derecha del header. Hora `hh:mm a` actualizada al cambiar cada minuto; `BatteryIcon` (capas) + porcentaje, ocultos si `BatteryObserver` marca la batería como no confiable. Pendiente del cluster derecho: acceso a Configuración. |
| 2026-09-27 | Paleta derivada de un color base | `paletteFrom(base)` en `GinnyThemes.kt`: cada tema define solo su color base (= accent); background/ink/muted salen de `base.adjust(lightness, chroma)` (OKLCH, `ui/theme/ColorModifiers.kt`). Reproduce los valores anteriores. Vuelve al enfoque "todo desde un color" del diseño legado. |
| 2026-09-27 | Tema actual + fuente | `GinnyTheme(settingsRepository)` (en `MainActivity`) lee `SettingsRepository.themeMode` y lo provee vía `LocalGinnyPalette`; los componentes leen `GinnyThemes.current`. Ciclo sigue cayendo en Menta. `GinnyFont` (M PLUS Rounded 1c, 4 pesos) en `ui/theme/Type.kt`. |
| 2026-09-27 | Header / Content / Footer (`ui/layout/`) | `Column` con margen 18dp (igual que el anterior) y gap 12dp entre las tres piezas (cumple la regla de piezas separadas). Header y Footer 48dp fijos; Content ocupa el resto (`weight(1f)`). Fondos blancos translúcidos provisionales, sin estilo glass/glow todavía. |
| 2026-09-27 | Íconos propios (`res/drawable/ic_*.xml`) | 16 íconos del usuario (+ `ic_buttons` e `ic_arrow` agregados después) (fuente: `../Iconos del sistema/*.svg`, lienzo 100×100, trazo 8) convertidos a VectorDrawable con `tools/svg2vd.js` — reemplazan los Material Icons del diseño anterior. Monocromos: el color lo pone `tint`. Batería (propuesta, falta confirmar al construirla): un solo contorno (`ic_battery_empty`) + relleno dibujado en código + rayo (`ic_battery_light`) como capa aparte en naranja. |
