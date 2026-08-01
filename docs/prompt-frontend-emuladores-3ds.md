# Prompt para Claude Code: Frontend de emuladores estilo Nintendo 3DS (Android)

## Contexto del proyecto

Quiero desarrollar una aplicación Android (APK) que funcione como **frontend/launcher para emuladores**, con una interfaz inspirada visualmente en el HOME Menu de la Nintendo 3DS, pero con scroll **vertical** en lugar de horizontal. La app está pensada para usarse en una **consola Android** (dispositivo tipo handheld/retroconsola), por lo que la navegación con **cruceta (D-Pad) y botones físicos** es un requisito central, no opcional.

## Stack técnico sugerido

Evalúa y decide el stack más adecuado (idealmente **Kotlin nativo con Jetpack Compose**, por rendimiento de animaciones, scroll-snap y consumo de batería; alternativamente Flutter si se justifica). Documenta la decisión antes de empezar a codear.

Requisitos técnicos base:
- Android mínimo: API 29 (Android 10).
- Orientación: landscape fijo.
- Soporte de entrada: D-Pad/joystick físico (gamepad API de Android — `KeyEvent.KEYCODE_DPAD_*`, `MotionEvent` de joystick analógico como alternativa), táctil, y botones físicos mapeados a A/B/Start/Select según el dispositivo.
- Sin dependencia de Google Play Services (muchas retroconsolas no lo incluyen).

## Estructura de la interfaz

### 1. Barra superior (status bar personalizada)
- Reemplaza la barra de estado del sistema operativo (ocultar la nativa, usar modo inmersivo/fullscreen).
- **Lado izquierdo — título contextual** (reemplaza el reloj que estaba aquí en versiones anteriores de este documento):
  - Si el usuario está en la **pantalla de sistemas**: muestra el nombre de la app, **"Ginny Frontend"**.
  - Si el usuario está en la **pantalla de juegos** o en la **pantalla de estados guardados** de un sistema: muestra el **nombre del sistema actual** (ej. "Super Nintendo").
- **Lado derecho — cluster alineado**: hora actual (actualizada cada minuto) + indicador de batería (ícono + porcentaje, con estados de carga) + botón de opciones, en ese orden, todos alineados a la derecha.
- **Botón de opciones (X)**: se movió aquí desde la barra inferior. Es un botón circular (badge) ubicado al final del cluster derecho, después del indicador de batería. Abre el panel de opciones/Configuración (ver sección correspondiente).
- **Detección de batería no confiable**: si `BatteryManager` no reporta datos fiables (ej. valores fijos como -1, 50% constante que nunca cambia durante un período razonable de uso, o ausencia de evento `ACTION_BATTERY_CHANGED`), el indicador de batería debe **ocultarse completamente** en vez de mostrar un dato posiblemente incorrecto. Definir una heurística simple (ej. si el valor no cambia en X minutos de uso activo con pantalla encendida, o si el nivel reportado es un valor inválido/fuera de rango) para decidir cuándo ocultarlo.
- Estilo visual acorde a la dirección estética definida más abajo (ver sección "Dirección estética").

### 2. Barra inferior
- Nombre del elemento actualmente seleccionado, alineado a la izquierda.
- A la derecha, botones pulsables (táctiles y accesibles por botón físico), cada uno con el ícono del botón físico correspondiente junto a su etiqueta/acción:
  - **A** → Abrir/lanzar el elemento seleccionado.
  - **B Retroceder** → Solo visible cuando el usuario está dentro del listado de juegos de un sistema o dentro de la pantalla de estados guardados (no en la pantalla de sistemas). Tiene **cuerpo sólido, con el mismo tratamiento visual que el botón A** (misma forma de pill/badge). El círculo/badge de la letra B tiene **fondo rojo sólido (degradado rojo suave) y la letra B en blanco** — exactamente el mismo tratamiento que el badge A (fondo de color sólido + letra blanca), solo que en rojo en vez de celeste→azul. Es **pulsable por toque, pero no forma parte de la navegación por cruceta/foco** — no se le puede dar foco con el D-Pad, solo se activa tocándolo en pantalla o presionando el botón físico B. Al activarse, regresa un nivel (ver "Navegación entre pantallas").
  - El botón de opciones (X) **ya no está aquí** — se movió a la barra superior (ver sección 1).
  - Los íconos deben representar visualmente el botón físico (círculo/badge con la letra, estilo similar al de los indicadores de botones de Nintendo), no solo un ícono genérico.

### 3. Contenedor central (grid de elementos)

Este es el componente más complejo. Debe respetar:

**Cuerpo visual del contenedor:**
- El contenedor de la grilla debe tener el **mismo tratamiento visual (cuerpo/superficie) que las barras superior e inferior** — mismo estilo de "panel" (fondo translúcido tipo glass suave, bordes redondeados grandes, mismo lenguaje visual), no un fondo transparente/inexistente.
- Debe existir un **espacio (gap) visible entre este panel y la barra superior, y entre este panel y la barra inferior** — los tres elementos (barra superior, panel de grilla, barra inferior) se perciben como piezas separadas apiladas verticalmente, no como un bloque continuo.

**Configuración de filas:**
- Ajuste en configuración: cantidad de filas visibles, valor entre 1 y 4.
- Los elementos son **cuadrados**, con espaciado (padding/gap) uniforme entre ellos.
- El alto de cada elemento se calcula dinámicamente según la cantidad de filas configurada (para que siempre quepan exactamente N filas en el alto disponible del contenedor).
- El ancho se ajusta proporcionalmente para mantener el elemento cuadrado, definiendo cuántas columnas caben por fila según el ancho de pantalla disponible.

**Orden:**
- Los elementos se listan siempre en **orden alfabético**.
- No son reubicables por el usuario (sin drag & drop, sin reordenamiento manual).

**Scroll vertical con snap centrado:**
- El scroll es vertical.
- Al soltar el scroll (fin de gesto de swipe o inercia), el contenedor debe **autoajustarse (snap)** de forma que siempre se muestren exactamente N filas completas centradas — nunca una fila cortada/parcial.
- Ejemplo: si la config indica 2 filas y el usuario suelta el scroll mostrando la fila inferior a medias, el contenedor debe animar el scroll hasta la posición más cercana donde ambas filas queden completamente visibles y centradas verticalmente en el contenedor.
- Este snap debe funcionar tanto con gesto táctil (swipe/fling) como con navegación por cruceta (al moverse verticalmente entre filas, el foco puede quedar en un elemento cuya fila no esté 100% visible; en ese caso también debe recalcularse el scroll para centrar).

**Navegación con cruceta:**
- El D-Pad debe navegar exclusivamente entre los elementos del contenedor central (no debe interactuar con la barra superior, y no tiene acceso al botón "B Retroceder" de la barra inferior — ese botón es solo táctil/por botón físico dedicado, nunca por foco de cruceta).
- Arriba/Abajo: mueve el foco a la fila anterior/siguiente, misma columna (o la más cercana si la fila tiene menos elementos).
- Izquierda/Derecha: mueve el foco al elemento anterior/siguiente dentro de la misma fila (con wrap opcional a fila anterior/siguiente — decidir comportamiento).
- El elemento con foco debe quedar siempre visible dentro del área centrada tras cada movimiento.

**Navegación rápida con L/R:**
- Los botones físicos **L y R** deben saltar hacia atrás/adelante una cantidad de filas igual a la cantidad de filas configurada para la grilla (ej. si la config indica 2 filas, L/R saltan 2 filas hacia atrás/adelante respectivamente).
- El foco se reposiciona en la misma columna donde estaba antes del salto (o la más cercana disponible), y el contenedor debe recalcular el snap centrado igual que con la navegación normal.

**Navegación entre pantallas (sistemas ↔ juegos):**
- Al abrir un sistema (botón A), se navega a la pantalla de juegos de ese sistema (reemplaza la pantalla completa).
- El botón físico **B** regresa un nivel: desde la pantalla de juegos de un sistema, vuelve a la pantalla de sistemas. Equivale a tocar el botón "B Retroceder" de la barra inferior.
- **No existe salto directo entre sistemas** (ej. no hay atajo para pasar del listado de juegos de NES al de SNES sin volver primero a la pantalla de sistemas) — comportamiento definido intencionalmente así.
- El botón físico **Menú** (presente en la mayoría de consolas Android) abre el panel de opciones — es el equivalente físico al botón X de la barra superior.

**Estilo de los elementos:**
- Elementos cuadrados con ícono/carátula del juego o emulador.
- Estado **no enfocado**: borde blanco de grosor pronunciado (tipo marco, tal como se ve en el mockup de referencia — no es un borde fino de 1px, es notoriamente grueso), con el contenido/carátula llenando el espacio interior hasta el borde, sin espacio en blanco intermedio.
- Estado **enfocado**: el borde completo (mismo grosor que el no enfocado) cambia a un degradado celeste → azul cubriendo el 100% del contorno, más un resplandor/glow azul suave hacia afuera del elemento (soft shadow difuminado), y un leve efecto de escala (sutil, estilo 3DS) para reforzar la selección.
- Transiciones animadas suaves entre estados de foco.

### 4. Audio

- **Música de fondo**: sonido ambiental/loop de fondo mientras se navega por la app (pantallas de sistemas y juegos). Volumen ajustable de forma independiente en Configuración (slider).
- **Sonido de foco**: efecto de sonido corto al cambiar el foco entre elementos del grid (por cruceta o por toque).
- **Sonido de activación**: efecto de sonido al abrir un sistema o lanzar un juego (botón A).
- Los sonidos de foco y activación comparten un volumen ajustable en Configuración (slider), independiente del de música de fondo.
- Todos los sonidos deben ser assets originales o de uso libre — no reproducir ni imitar directamente los sonidos propietarios de la 3DS.

## Funcionalidad de emulación

**Jerarquía de navegación:** la app tiene hasta tres niveles, todos usando el mismo componente de grid descrito arriba:
1. **Pantalla de sistemas**: grid con los sistemas/consolas disponibles (NES, SNES, GBA, etc.), ordenados alfabéticamente.
2. **Pantalla de juegos**: al seleccionar y abrir un sistema, se navega a un grid con los juegos de ese sistema, también ordenados alfabéticamente.
3. **Pantalla de estados guardados** (solo si el juego tiene estados guardados detectados): grid con "Juego nuevo" como primer elemento, seguido de los slots de save state disponibles. Ver sección "Estados guardados" más abajo para el detalle completo del flujo.

**Lanzamiento de juegos vía Intents — múltiples emuladores según el sistema:**

La app NO ejecuta la emulación por sí misma. Cada sistema tiene asignado un emulador externo (instalado por separado en el dispositivo), y la app le delega la ejecución mediante **Intents explícitos**, con el esquema de intent propio de cada emulador. Debe existir una capa de abstracción tipo "launcher strategy" por sistema, para que agregar/cambiar el emulador de un sistema en el futuro sea sencillo (patrón Strategy o similar, un launcher por sistema/emulador).

Mapeo inicial de sistema → emulador:

| Sistema | Emulador | Notas |
|---|---|---|
| NES | RetroArch | Selección de core configurable (ver abajo) |
| SNES | RetroArch | Selección de core configurable |
| Game Boy (GB) | RetroArch | Selección de core configurable |
| Game Boy Color (GBC) | RetroArch | Selección de core configurable |
| Game Boy Advance (GBA) | RetroArch | Selección de core configurable |
| PlayStation 1 (PS1) | RetroArch | Selección de core configurable |
| Sega Genesis / Mega Drive | RetroArch | Selección de core configurable |
| Sega Master System | RetroArch | Selección de core configurable |
| Neo Geo | RetroArch | Selección de core configurable |
| FinalBurn Neo (FBNeo) | RetroArch | Selección de core configurable (`fbneo_libretro_android.so`) — carpeta `fbneo/` |
| MAME | RetroArch | Core: `mame2010_libretro_android.so` — carpeta `mame/` |
| GameCube | Dolphin | Intent propio de Dolphin (paquete `org.dolphinemu.dolphinemu`), verificar esquema actual de intents/deep links soportado |
| PlayStation 2 | AetherSX2 | Ver nota importante debajo de la tabla |
| PSP | PPSSPP | Intent propio de PPSSPP (paquete `org.ppsspp.ppsspp`), verificar esquema actual de intents |
| Dreamcast | **Seleccionable por el usuario**: Redream *o* RetroArch (core `flycast_libretro_android.so` o `reicast_libretro_android.so`) | Igual que en el caso de RetroArch para otros sistemas, si se elige RetroArch, debe permitir seleccionar el core específico |

**Nota importante sobre AetherSX2 (investigado en esta sesión):**
AetherSX2 fue descontinuado por su desarrollador original a comienzos de 2023 y removido oficialmente de Google Play Store en marzo de 2024. Ya no recibe actualizaciones oficiales. La comunidad mantiene un fork activo llamado **NetherSX2** (por el desarrollador Trixarian), que elimina publicidad, mejora estabilidad y compatibilidad, y ya se distribuye como APK standalone. También existe un sucesor más ambicioso en desarrollo llamado **EtherealSX2**, aún no confirmado como estable/publicado.

Se mantiene AetherSX2 como emulador configurado para PS2 según lo solicitado, pero se deja documentado que:
- El paquete/esquema de intent de AetherSX2 puede no estar vigente o puede requerir verificación manual antes de implementar, dado que ya no tiene distribución oficial.
- Se recomienda que la app permita configurar el **package name del emulador de PS2** en Settings (en vez de dejarlo hardcodeado), para que el usuario pueda apuntar a AetherSX2, NetherSX2, o el fork que tenga instalado, sin depender de que Claude Code adivine el paquete correcto exacto vigente al momento del build.

**Selección de core en sistemas manejados por RetroArch:**
- Para cada sistema mapeado a RetroArch, la configuración debe permitir **elegir el core libretro específico** a usar (ej. para SNES: `snes9x_libretro_android.so` vs `bsnes_libretro_android.so`; para GBA: `mgba_libretro_android.so` vs `vba_next_libretro_android.so`, etc.).
- Para MAME se confirma el core `mame2010_libretro_android.so` como valor por defecto. **Nota importante**: los cores de MAME son sensibles a la versión del romset (`mame2010` funciona con romsets de una versión específica de MAME, aproximadamente 0.139 — romsets de versiones más nuevas o más viejas de MAME probablemente no funcionen o den errores de "CRC mismatch"). Esto es una limitación del romset del usuario, no algo que la app pueda solucionar por software; solo documentarlo en el README o en un tooltip de ayuda para evitar confusión si algunas ROMs de MAME no cargan.
- Este core seleccionado se pasa como extra en el intent hacia RetroArch (`LIBRETRO`), junto a la ruta del ROM (`ROM`).
- La lista de cores disponibles por sistema puede ser una lista curada/hardcodeada inicialmente (no es necesario detectar dinámicamente los cores instalados en v1, aunque sería una mejora futura deseable).

**Manejo de emulador no instalado:**
- Antes de lanzar el intent, verificar si el paquete del emulador correspondiente está instalado.
- Si no lo está, mostrar un aviso claro indicando qué emulador falta y, de ser posible, un enlace a su página de descarga/Play Store.

**Panel de opciones del elemento:** definir qué contiene (ver sección de omisiones, punto correspondiente). Para sistemas basados en RetroArch, este panel es también el lugar natural para exponer el selector de core.

## Estructura de almacenamiento (ROMs y Assets)

**Carpeta base de ROMs:**
- El usuario especifica **una única carpeta base de ROMs** (no una por sistema). Esta carpeta debe seguir la estructura típica usada por frontends de emulación (compatible con la convención de ES-DE), una subcarpeta por sistema, nombrada igual que en la estructura de Assets (`nes`, `snes`, `gb`, `gbc`, `gba`, `ps`, `genesis`, `mastersystem`, `neogeo`, `fbneo`, `mame`, `gc`, `ps2`, `psp`, `dreamcast`).

```
[Carpeta base de ROMs]/
├── nes/
├── snes/
├── gb/
├── gbc/
├── gba/
├── ps/
├── genesis/
├── mastersystem/
├── neogeo/
├── fbneo/
├── mame/
├── gc/
├── ps2/
├── psp/
└── dreamcast/
```

- Dentro de cada subcarpeta van los archivos de ROM de ese sistema directamente (sin más subniveles).
- **Filtrado por extensión**: al escanear cada subcarpeta, solo se deben listar como juegos los archivos con extensiones válidas para ese sistema. Debe existir una tabla/mapa de extensiones válidas por sistema (a definir/confirmar — ver sección de omisiones), por ejemplo: `.nes` para NES, `.sfc`/`.smc` para SNES, `.gba` para GBA, `.iso`/`.chd`/`.bin` para PS1/PS2, etc. Archivos con extensiones no reconocidas para ese sistema se ignoran en el listado.
- Si la carpeta base no tiene alguna de las subcarpetas de sistema, ese sistema simplemente no debe listar juegos (no es obligatorio que existan todas).

**Convención de nombres entre ROM e ícono:**
- El nombre del archivo de ícono en `Assets/[sistema]/icons/` debe ser el **nombre completo del archivo de ROM (incluyendo su extensión) + `.png`**.
- Ejemplo: para la ROM `BomberMan.nes`, el ícono esperado es `BomberMan.nes.png`.
- Si no existe el ícono correspondiente para una ROM, debe mostrarse una imagen placeholder genérica (definir un diseño simple acorde al estilo visual de la app).

**Carpeta de Usuario y estructura de Assets:**
- Además, existe una configuración separada: **carpeta de Usuario** (una única ruta base elegida por el usuario, distinta de las carpetas de ROMs).
- Al seleccionar la carpeta de Usuario, la app debe **crear automáticamente** dentro de ella la siguiente estructura:

```
[Carpeta de Usuario]/
└── Assets/
    ├── nes/
    │   ├── icons/
    │   └── system.png
    ├── snes/
    │   ├── icons/
    │   └── system.png
    ├── gb/
    │   ├── icons/
    │   └── system.png
    ├── gbc/
    │   ├── icons/
    │   └── system.png
    ├── gba/
    │   ├── icons/
    │   └── system.png
    ├── ps/
    │   ├── icons/
    │   └── system.png
    ├── genesis/
    │   ├── icons/
    │   └── system.png
    ├── mastersystem/
    │   ├── icons/
    │   └── system.png
    ├── neogeo/
    │   ├── icons/
    │   └── system.png
    ├── fbneo/
    │   ├── icons/
    │   └── system.png
    ├── mame/
    │   ├── icons/
    │   └── system.png
    ├── gc/
    │   ├── icons/
    │   └── system.png
    ├── ps2/
    │   ├── icons/
    │   └── system.png
    ├── psp/
    │   ├── icons/
    │   └── system.png
    └── dreamcast/
        ├── icons/
        └── system.png
```

- Dentro de la carpeta `icons/` de cada sistema van las carátulas PNG de cada ROM (el usuario las coloca manualmente ahí; el nombre de archivo debe poder mapearse al nombre/identificador de la ROM correspondiente — definir convención de nombres, ej. mismo nombre que el archivo de ROM sin extensión).
- El archivo `system.png` (al mismo nivel que `icons/`, no dentro) es el logotipo del sistema, usado en la pantalla de sistemas.
- **Al crear la estructura por primera vez**, la app debe generar automáticamente los archivos `system.png` de cada sistema con un logotipo por defecto (no debe dejar la app sin logos). Estas imágenes por defecto deben empaquetarse como assets/recursos dentro del propio proyecto Android (ej. `res/drawable` o `assets/`), para luego copiarse físicamente a la carpeta de Usuario al momento de crear la estructura.
- **Acción para Claude Code**: crear en el repositorio del proyecto una carpeta dedicada (ej. `app/src/main/assets/system-logos/` o similar, a definir según el stack elegido) donde yo depositaré manualmente las imágenes `system.png` de cada sistema (nes.png, snes.png, gc.png, etc.). El código debe leer desde esa carpeta del proyecto al momento de inicializar/crear la estructura en la carpeta de Usuario del dispositivo.
- Si la carpeta de Usuario ya tiene la estructura (total o parcial) al reconfigurar la ruta, la app no debe sobrescribir `icons/` existentes, pero sí debe crear lo que falte (carpetas o `system.png` ausentes).
- **Diccionario de nombres opcional (`names.json`, agregado 2026-07-30)**: cada carpeta `Assets/<sistema>/` admite además un archivo `names.json` opcional, **no** creado automáticamente por la app (a diferencia de `icons/` y `system.png`) — el usuario lo coloca/edita a mano cuando quiere mostrar un nombre legible en vez del nombre crudo del archivo/carpeta de ROM (típicamente necesario en sistemas arcade como NeoGeo/MAME/FBNeo, donde el nombre de archivo es una clave corta tipo `3countb.zip`). Formato: array JSON `[{"file": "3countb.zip", "name": "The Three Countdown"}, ...]`, comparación case-insensitive contra el nombre de archivo (o de carpeta, en sistemas "carpeta por juego" como Switch). Si el archivo no existe, no se puede leer, el JSON es inválido, o un ROM puntual no tiene entrada, se muestra el nombre de archivo/carpeta como ya se hacía. Ver detalle en `docs/architecture.md`.

## Panel de opciones / Configuración

El botón **X** de la barra superior (o el botón físico **Menú**) abre esta pantalla, que funciona como la configuración general de la app. Contiene:

- **Cantidad de filas de la grilla** (1–4).
- **Ruta del folder de Usuario** (ver sección "Estructura de almacenamiento").
- **Ruta del folder de ROMs** (ver sección "Estructura de almacenamiento").
- **Selección del emulador y core para los sistemas**: por cada sistema, elegir el emulador (cuando aplique, como en Dreamcast) y el core de RetroArch correspondiente (cuando aplique).
- **Volumen de música de fondo** (slider independiente).
- **Volumen de sonidos de interfaz** (foco/activación, slider independiente del anterior).

Pantalla de ajustes accesible desde el panel de opciones, debe incluir al menos estos cuatro puntos. Adicionalmente, y como parte de la misma pantalla o una sub-sección relacionada:
- Package name configurable del emulador de PS2 (ver nota sobre AetherSX2/NetherSX2), como parte de la configuración de emulador/core por sistema.
- Mapeo de botones físicos (para adaptarse a distintas consolas Android).
- Tema visual (si se planea soporte de temas más adelante).

## Estados guardados (Save States)

**Viabilidad técnica** (para contexto, ya investigado):
- Para sistemas manejados por RetroArch: viable leyendo las miniaturas PNG que RetroArch genera automáticamente junto a cada save state (opción `savestate_thumbnail_enable` en RetroArch, activación manual requerida — documentar el paso en el README). Nuestra app solo necesita leer esa carpeta (ruta configurable en Settings) y mostrar las miniaturas encontradas.
- Para emuladores standalone (Dolphin, PPSSPP, AetherSX2/NetherSX2): cada uno usa un formato propio (ej. PPSSPP embebe la captura dentro del `.ppst`), por lo que queda fuera del alcance de v1; a investigar como fase 2 si se desea más adelante.

**Flujo de UI (definido):**
- Al presionar A sobre un juego (dentro del grid de juegos de un sistema):
  - Si el juego **tiene** estados guardados detectados: se navega a una **pantalla de estados guardados de ese juego**, que reutiliza el mismo componente de grid (mismo estilo visual, misma lógica de filas configurables/snap centrado/navegación por cruceta que las pantallas de sistemas y juegos).
    - El **primer elemento de esa lista siempre es "Juego nuevo"** (inicia el juego desde cero, sin cargar ningún estado).
    - Le siguen los slots de estados guardados detectados, cada uno mostrando su miniatura como ícono (igual que las carátulas en el grid de juegos).
    - Al seleccionar un elemento (A), se lanza el juego: "Juego nuevo" lo inicia limpio; un estado guardado específico intenta cargar ese slot (sujeto a que el esquema de intents del emulador correspondiente permita indicar qué slot cargar al iniciar — verificar por emulador; RetroArch es el caso más probable de soportarlo).
  - Si el juego **no tiene** estados guardados: se **omite esta pantalla** y el juego se lanza directamente (comportamiento igual al actual).
- El botón **B** en la pantalla de estados guardados regresa al grid de juegos del sistema (mismo patrón de navegación ya definido).

## Dirección estética

La referencia visual son dos interfaces de Nintendo:
- **3DS HOME Menu**: fondo claro, tarjetas/íconos con esquinas suavemente redondeadas, barra superior e inferior delgadas con información contextual, sensación "limpia" y luminosa, foco de selección con borde de color y ligera elevación.
- **Wii U Menu**: paleta clara con acentos en celeste/turquesa, íconos tipo "tile" con sombra suave y profundidad sutil (efecto casi 3D/glossy), tipografía redondeada y amigable, sensación de "panel de vidrio esmerilado" en las superficies.

**Objetivo**: lograr una identidad visual **propia y original** que se sienta como si perteneciera a esta familia de interfaces (misma sensación de calidez, limpieza y "hardware Nintendo de esa época"), pero sin copiar directamente ningún asset, ícono, tipografía o elemento con derechos de autor de ninguna de las dos. Se trata de capturar el *lenguaje visual* (paleta, formas, jerarquía, tratamiento de luz/sombra, tono general), no de reproducir estos menús.

Lineamientos concretos para Claude Code:
- Paleta base clara (blancos/grises muy suaves) con acentos en celeste → azul (coherente con el borde de foco ya definido).
- **Fondo de la app con un toque "aurora"**: en vez de un fondo plano de un solo color, usar un degradado suave multicolor (tonos pastel: verde, lila, durazno/crema, celeste — ver mockup de referencia) como fondo general de la pantalla, detrás de las barras y el panel de la grilla. Debe ser **sutil y difuminado**, no saturado ni protagonista — funciona como ambientación, no debe competir con el contenido ni afectar la legibilidad de los elementos sobre él.
- Esquinas redondeadas consistentes en toda la interfaz (tarjetas, barras, botones, panel de la grilla).
- Uso sutil de sombras/profundidad tipo "glassmorphism" o "soft UI", sin exagerar (no glassmorphism ultra moderno tipo iOS, sino algo más cercano a la sobriedad de estas interfaces de consola).
- Tipografía redondeada, legible a distancia (uso típico en TV/pantallas pequeñas de consola).
- Iconografía de botones físicos (A, B, X, Menú, etc.) como badges circulares simples, consistentes con el estilo general.
- Nunca usar logos, personajes, ni tipografías propietarias de Nintendo — todo debe ser diseño original inspirado en la sensación general, no una réplica.
- **Resplandores de color (glow) en las tres piezas de la interfaz** (barra superior, panel de la grilla, barra inferior): cada una debe tener un resplandor difuminado hacia afuera, en un color distinto y suave, reforzando la sensación "aurora" del fondo:
  - Barra superior: resplandor celeste-azulado (`box-shadow: 0px 0px 30px #aabcfd`).
  - Panel de la grilla: resplandor rosado (`box-shadow: 0px 0px 20px pink`).
  - Barra inferior: resplandor verde suave (`box-shadow: 0px 0px 30px #baf1ac`).
  - Estos valores son los confirmados en el mockup de referencia; usarlos tal cual salvo que se ajusten más adelante.

Se adjunta un mockup visual de referencia (HTML estático, no funcional) con esta propuesta, incluido junto a este documento, para que Claude Code entienda el resultado final esperado antes de implementar.

## Entregables esperados de Claude Code

1. Documento breve de arquitectura (stack elegido, estructura de carpetas, gestión de estado).
2. Implementación del proyecto Android completo, compilable a APK.
3. Componente de grid con snap centrado y soporte D-Pad, idealmente aislado/reutilizable.
4. Manejo de permisos de almacenamiento para escanear ROMs.
5. README con instrucciones de build, instalación y configuración inicial.
6. Usar el mockup HTML adjunto (`mockup-visual.html`) como referencia de dirección estética antes de implementar la UI real (no es código a reutilizar tal cual, solo referencia visual).

---

## ⚠️ Cosas a definir antes de pasar este prompt (no incluidas arriba)

Estos son puntos que noté ambiguos u omitidos en tu descripción original. Te recomiendo decidirlos y agregarlos al prompt antes de dárselo a Claude Code, porque son decisiones que afectan bastante la arquitectura:

1. ~~¿Qué son exactamente los "elementos" listados?~~ **Resuelto**: jerarquía sistemas → juegos, confirmado.

2. ~~¿Cómo se lanzan los juegos?~~ **Resuelto**: intents explícitos hacia RetroArch (ver sección "Funcionalidad de emulación").

3. **Extensiones válidas por sistema.** Falta la tabla exacta de extensiones a aceptar por sistema para el filtrado de la carpeta de ROMs. Propuesta inicial a confirmar/ajustar:

| Sistema | Extensiones propuestas |
|---|---|
| NES | `.nes` |
| SNES | `.sfc`, `.smc` |
| GB | `.gb` |
| GBC | `.gbc` |
| GBA | `.gba` |
| PS1 | `.iso`, `.bin`, `.cue`, `.chd`, `.pbp` |
| Genesis | `.md`, `.gen`, `.bin` |
| Master System | `.sms` |
| Neo Geo | `.zip` (roms arcade suelen ir comprimidas) |
| FBNeo | `.zip` |
| MAME | `.zip` |
| GameCube | `.iso`, `.gcm`, `.rvz` |
| PS2 | `.iso`, `.chd`, `.bin` |
| PSP | `.iso`, `.cso`, `.pbp` |
| Dreamcast | `.chd`, `.gdi`, `.cdi` |

¿Confirmas esta tabla o la ajustamos?

4. ~~Contenido del "panel de opciones"~~ **Resuelto**: es la pantalla general de Configuración (filas de grilla, carpeta de Usuario, carpeta de ROMs, emulador/core por sistema).

5. ~~Búsqueda/filtro~~ **Resuelto**: no se implementa buscador; en su lugar, L/R saltan N filas (según configuración de filas del grid).

6. ~~Sonidos y transiciones~~ **Resuelto**: música de fondo + sonido de foco + sonido de activación (ver sección "Audio").

7. ~~Navegación entre sistemas y juegos~~ **Resuelto**: B retrocede un nivel (también botón táctil "B Retroceder" en la barra inferior, solo dentro del listado de juegos, no navegable por cruceta). Sin saltos directos entre sistemas.

8. ~~Botón dedicado para abrir el panel de opciones~~ **Resuelto**: botón físico Menú (equivalente a X).

9. ~~Guardado de progreso/estados~~ **Resuelto**: ver sección "Estados guardados" — pantalla de saves reutilizando el grid, con "Juego nuevo" como primer elemento; se omite si el juego no tiene saves.

10. ~~Estado sin batería/tablet sin sensor de batería~~ **Resuelto**: si se detecta que el dato no es fiable, se oculta el indicador por completo (ver sección "Barra superior").

11. ~~Identidad visual/legal~~ **Resuelto**: ver sección "Dirección estética" — inspirado en 3DS HOME Menu + Wii U Menu, sin copiar assets/logos propietarios.

Si quieres, puedo ayudarte a redactar respuestas a estos puntos y actualizar el documento antes de pasarlo a Claude Code.
