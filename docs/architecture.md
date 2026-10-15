# Arquitectura — Ginny Frontend

> **Nota (2026-10-13):** `ginny.cfg` ya no guarda la carpeta de ROMs, las carpetas de ROMs por sistema ni `lastScanAt`
> (son de cada dispositivo; ver `DEVICE_LOCAL_KEYS` en `GinnyConfigRepository`). Sí guarda `themeMode` y `favorites`, y
> conserva las claves desconocidas al reescribirse.

> **Nota (2026-10-14):** la capa `ui/` fue **reconstruida paso a paso** (etapas 1 y 2, ver
> [plan-reconstruccion-ui.md](plan-reconstruccion-ui.md) y [pendientes-etapa-2.md](pendientes-etapa-2.md)) y el
> respaldo de la UI anterior (`docs/ui-legacy/`) se retiró; si hiciera falta, queda en el historial de git. Este
> documento describe la UI actual (secciones «Estructura de paquetes», «La grilla», «Interfaz actual»); las
> menciones a `PagedFocusGrid`, `TopBar`, `GinnyNavHost`, `GameOptionsDialog` y similares que queden en otros
> apartados son históricas. La capa de datos, lanzadores y audio no cambiaron.

## Stack elegido

**Kotlin nativo + Jetpack Compose.**

Se evaluó Flutter como alternativa (mencionada como opción en el prompt original), pero se
descartó por:

- El requisito central de navegación por D-Pad/gamepad físico se apoya directamente en el modelo
  de eventos de `KeyEvent`/`MotionEvent` de Android y en el sistema de foco de Compose
  (`Modifier.onKeyEvent`, `FocusRequester`) — en Flutter esto exige puentear por platform channels
  o depender de paquetes de terceros con soporte desigual.
- El scroll-snap centrado personalizado (sección "Contenedor central") es más directo de construir
  a mano sobre `LazyVerticalGrid` + `LazyGridState.animateScrollToItem` que replicando el mismo
  comportamiento sobre un `ScrollController` de Flutter.
- Sin dependencia de Play Services, el acceso a `Intent`s explícitos hacia RetroArch/Dolphin/PPSSPP
  es una API de plataforma directa en Kotlin; en Flutter requeriría un plugin de intents.
- Consumo de batería y rendimiento de animaciones: Compose sobre Kotlin nativo evita el overhead
  del engine de Flutter, relevante en hardware de retroconsola de gama baja.

## Estructura de paquetes

```
app/src/main/java/com/ginny/frontend/
├── MainActivity.kt          # Shell único, modo inmersivo; manejo global de B / Menú / Y por hardware (dispatchKeyEvent)
├── GinnyApplication.kt      # Punto de entrada del contenedor de DI manual
├── di/AppContainer.kt       # DI manual (repos + casos de uso), sin Hilt — la app es chica
├── data/
│   ├── model/                # SystemId, SystemDefinition, SystemCatalog (catálogo hardcodeado), EmulatorTarget,
│   │                          # RetroArchCore, GameEntry, RomOrganization, ButtonLayout, MusicTrack
│   ├── SettingsRepository.kt  # DataStore Preferences: filas de grilla, carpetas, emulador/core por sistema,
│   │                          # volúmenes, mapeo de botones, tema, favoritos, pantalla de bienvenida pendiente
│   ├── GinnyConfigRepository.kt # Lee/escribe ginny.cfg en la Carpeta de Usuario (ver docs/ginny-frontend.md)
│   ├── RomRepository.kt       # Escaneo de ROMs vía SAF (DocumentFile), filtrado por extensión
│   ├── GameIndexRepository.kt # Fachada sobre Room: caché de la última corrida de escaneo
│   ├── db/                    # Room: ScannedGameEntity, ScannedGameDao, GinnyDatabase
│   ├── AssetRepository.kt     # Íconos, logos de sistema y diccionario de nombres (names.json) desde Assets/
│   ├── MusicRepository.kt     # Pistas de música de fondo (embebidas + Interface/Background Music)
│   ├── StorageBootstrapper.kt # Crea Assets/<sistema>/{icons/, system.png} en la carpeta de Usuario
│   ├── scrap/                 # Scrapper (ScreenScraper + SteamGridDB): clientes, almacenamiento en Assets/,
│   │                          # orquestador del lote. Ver «Scrapper» más abajo
│   └── ButtonMapping.kt       # Mapeo lógico -> keycode físico remapeable
├── launch/                   # Patrón Strategy: un LauncherStrategy por emulador (RetroArch, Dolphin, melonDS,
│                              # genérico por ACTION_VIEW) + GameLauncher (caso de uso)
├── audio/SoundManager.kt     # SoundPool (foco / activar / volver) + MediaPlayer en loop (música de fondo)
├── system/BatteryObserver.kt # Heurística de batería no confiable (ver sección abajo)
├── util/SafUtils.kt
└── ui/
    ├── GinnyApp.kt            # Raíz: fondo, cabecera/contenido/pie, pantallas, modales y su orden de prioridad
    ├── theme/                 # Paleta OKLCH por matiz (4 temas + Ciclo animado), tipografía M PLUS Rounded 1c
    ├── layout/                # Header, Content (flecha · grilla · flecha) y Footer: las tres filas de toda pantalla
    ├── components/            # Container (vidrio), Icon, ButtonLegend, GinnyMenu (desplegable propio), PageIndicator,
    │   │                      # DeviceStatus/BatteryIcon, ScrapActivityIcon, GeometricBackground…
    │   ├── game/              # Element (tarjeta), ElementGrid (grilla paginada), GridNavigation (aritmética pura),
    │   │                      # Cursor, PageArrow
    │   ├── box/               # GameBox3D / BoxGeometry: la caja del juego en 3D
    │   └── settings/          # SettingsControls (filas, botones, desplegable…) y SettingsNavigation
    │                          # (foco por cruceta + cursor en Configuración)
    ├── modifiers/             # Backdrop (vidrio desenfocado), InnerShadow
    ├── display/               # Pantalla secundaria (consolas de doble pantalla)
    ├── navigation/Screen.kt   # Screen.Systems | Screen.Games(scope, title): estado simple, sin NavHost
    ├── util/                  # AppContainer, LocalSoundManager, DrawableUri
    └── screens/
        ├── systems/           # SystemsScreen + ViewModel (grilla de sistemas con logos, Favoritos)
        ├── games/             # GamesScreen + ViewModel, GameModal (+ diálogos, video, picker de ícono), GamesScope
        ├── settings/          # SettingsModal: barra lateral + Interfaz / Carpetas / Sistemas / Scrapper / Créditos
        ├── scrap/             # ScrapLaunchModal, ScrapProgressModal, ScrapViewModel
        ├── welcome/           # WelcomeScreen: primer arranque (carpetas, escaneo)
        └── exit/              # ExitConfirmModal: «Cerrar Ginny» desde Sistemas
```

## Gestión de estado

- **ViewModel + StateFlow** por pantalla (`SystemsViewModel`, `GamesViewModel`,
  `SettingsViewModel`, `ScrapViewModel`), instanciados con `viewModelFactory { initializer { } }`
  contra el `AppContainer` — no se usa Hilt/Dagger: la superficie de la app no lo justifica.
- **DataStore Preferences** (`SettingsRepository`) para toda la configuración persistente: filas de
  grilla, URIs de carpetas (ROMs/Usuario), emulador y core por sistema, override
  de package de PS2, volúmenes, mapeo de botones.
- El foco del grid (`focusedIndex`) vive en el ViewModel de cada pantalla, no en el componente de
  grid, para sobrevivir recomposiciones y ser reutilizable entre pantallas.
- **Room** (`data/db/`) cachea el resultado del último escaneo de ROMs — el catálogo de sistemas
  sigue siendo estático (`SystemCatalog`), pero la lista de *juegos* ya no se deriva en vivo. Detalle
  y motivación en "Caché de biblioteca" más abajo.

## La grilla (`ElementGrid`)

Pieza más compleja de la UI, en `ui/components/game/ElementGrid.kt` + `GridNavigation.kt` (aritmética pura, sin
Compose, para razonar la navegación por separado del layout). La usan Sistemas y Juegos a través de `ui/layout/Content.kt`
(flecha izquierda · grilla · flecha derecha). Modelo de **páginas discretas**:

- Lado del elemento = `(alto disponible − separaciones) ÷ filas`, así siempre caben exactamente las filas pedidas;
  columnas = las que entren en el ancho. Una página son `columnas × filas` elementos; si no se llena, el resto son
  huecos (`Element(empty = true)`, los puntos blancos) para que la grilla conserve su forma — también mientras carga.
- Las filas (Configuración > Interfaz) llegan como `Int?`: hasta leer el ajuste no se dibuja la grilla, para que no
  cambie de forma al llegar el valor real.
- Navegación: cruceta y L/R leídas con el `ButtonMapping` remapeable (no hardcodea `KEYCODE_DPAD_*`); izquierda/derecha
  no hacen wrap a la fila adyacente; L/R y las flechas táctiles cambian de página. El cursor (`Cursor`) se desliza al
  elemento nuevo y «rebota» contra los bordes.
- El índice seleccionado vive en el ViewModel de cada pantalla, no en la grilla. Hacia afuera la grilla informa el
  elemento seleccionado y las páginas (`onSelectionChanged` / `onPagingChanged`) para que el pie los muestre.
- Cada `Element` aparece con un fade cuando su imagen está lista; la sombra entra después del fade (con opacidad < 1
  la capa recortaría la sombra).
- B nunca forma parte de la navegación por foco: lo atiende `MainActivity.dispatchKeyEvent` (y `BackHandler`s en
  `GinnyApp`), sin importar qué composable tenga el foco.

## Caché de biblioteca (Room)

Con muchos sistemas/juegos configurados, escanear la carpeta de ROMs completa vía SAF en cada
apertura de Sistemas/Juegos (como hacía v1) se vuelve visiblemente lento — SAF/`DocumentFile` es
mucho más costoso que I/O de archivo normal. Se resolvió así:

- **`RomRepository.scanAllSystems`** hace el walk completo (todos los sistemas) y solo se dispara
  desde un botón explícito, "Escanear biblioteca", en Settings > Carpetas, al lado del selector de
  Carpeta de ROMs (`SettingsViewModel.scanLibrary`). No corre en ningún otro momento — ni al cambiar
  las URIs de carpetas, ni al navegar.
- El resultado se persiste íntegro en **Room** (`data/db/GinnyDatabase.kt`, tabla `scanned_games`
  vía `ScannedGameDao`), reemplazando la tabla entera (`replaceAll`: borra + reinserta) en vez de
  mergear incrementalmente — más simple y evita lidiar con juegos borrados/renombrados en disco desde
  el último escaneo.
- **`GameIndexRepository`** es la única fachada de lectura sobre esa tabla; `SystemsViewModel` y
  `GamesViewModel` la consumen vía `Flow`, así que la UI se actualiza sola apenas termina un escaneo,
  sin necesidad de recargar la pantalla.
- **Por qué Room y no un archivo JSON en `filesDir`**: Room es el mecanismo estándar de Android para
  exactamente este patrón (índice cacheado que la UI observa reactivamente), integra `Flow` de forma
  nativa sin deserializar todo a memoria en cada lectura, y escala mejor si la biblioteca crece a
  miles de juegos. El costo es una dependencia nueva (`androidx.room` + plugin `kapt`, agregado a
  `build.gradle.kts`) que el proyecto no tenía — se evaluó y se consideró que valía la pena frente a
  la alternativa más liviana (JSON), dado que el propósito explícito de esta caché es evitar
  relecturas repetidas de disco.
- El botón "Y Recargar" que ya existía en las pantallas de Sistemas/Juegos se mantuvo, pero ahora es
  solo una relectura manual de la caché (`ViewModel.reload()`) — no dispara un escaneo de SAF. Es
  redundante con el `Flow` reactivo en la práctica, pero se dejó funcional (no como no-op) para no
  repetir el bug de "botón que no hace nada" que motivó esta caché en primer lugar.
- `SettingsRepository.lastScanAt` (DataStore) guarda el timestamp del último escaneo completado, para
  mostrarlo en Settings y para que los mensajes de estado vacío en Sistemas/Juegos puedan sugerir
  "Escaneá la biblioteca" en vez de sonar como que la carpeta de ROMs está mal configurada.
- **`ScanProgressDialog` con alto fijo y cancelación (agregado 2026-08-11)**: el modal original dejaba
  crecer la lista de sistemas escaneados sin límite, así que con muchos sistemas terminaba más alto
  que la pantalla. Ahora la lista vive en un `Column` de alto fijo (`EntriesBoxHeight = 260.dp`) con
  su propio `verticalScroll`, dentro de una caja con borde punteado (dibujado a mano vía
  `Modifier.drawWithContent` + `PathEffect.dashPathEffect`, Compose no trae uno nativo). El título
  muestra el `CircularProgressIndicator` junto al texto en vez de metido en la lista. El botón de
  acción mientras escanea dice "Detener" y ahora cancela de verdad: `SettingsViewModel.scanLibrary`
  guarda el `Job` del `viewModelScope.launch` y `stopScanning()` lo cancela — como
  `GameIndexRepository.replaceAll` recién corre al final del escaneo, cancelar a mitad de camino no
  deja resultados parciales persistidos.

## Lanzamiento de emuladores

Patrón Strategy (`LauncherStrategy`), una implementación por emulador:

- **RetroArch**: intent explícito contra `com.retroarch(.aarch64)/.../RetroActivityFuture` con
  extras `ROM` y `LIBRETRO` (ruta al core `.so`) — esquema documentado y usado por otros frontends
  de terceros. El extra `STATE_SLOT` para forzar un save-state al iniciar se envía "a ver si pega":
  no hay confirmación oficial de que RetroArch lo respete en todas las builds (ver README).
- **Dolphin / PPSSPP / AetherSX2 / Redream**: `ACTION_VIEW` genérico + `setPackage(...)` + permiso
  de lectura sobre el Uri del ROM. Se eligió este enfoque, más portable, en vez de apuntar a una
  Activity interna específica de cada emulador, porque el prompt mismo señala que esos esquemas
  "hay que verificar" — apuntar a una clase interna que cambie de versión rompería el launcher.
- El package de PS2 y de Switch son configurables en Settings (`SystemDefinition.supportsPackageOverride`)
  vía una clave genérica por sistema en `SettingsRepository.packageOverrideFor` — no hardcodeados,
  porque en ambos casos el ecosistema de apps es inestable (forks, o directamente sin emulador
  Android confirmado todavía en el caso de Switch).
- **Emulador no instalado**: cuando `LaunchResult.EmulatorNotInstalled` llega hasta `GamesScreen`, el modal del juego lo
  avisa y deja elegir otro emulador / core / package para ese sistema sin salir de la pantalla (los mismos controles de
  «Gestión de juegos»); `GamesViewModel` expone `settingsRepository` / `assetRepository` directamente.

## Gestión de juegos (modal del juego)

Las acciones de un juego (jugar, favorito, emulador / core / package del sistema, reescanear el sistema, eliminar) viven en
el modal del juego (`ui/screens/games/GameModal.kt` y `GameModalDialogs.kt`), que se abre al activar un elemento de la
grilla de Juegos. Emulador, core y package son **del sistema** (afectan a todos sus juegos), igual que en Configuración >
Sistemas, y se sincronizan con `ginny.cfg`. (El antiguo menú desplegable ⋮ y `GameOptionsDialog` ya no existen.)

**Reescanear un solo sistema** (`GamesViewModel.rescanSystem`): mismo escaneo SAF que `SettingsViewModel.scanLibrary`
pero acotado a un `systemId`: reutiliza `RomRepository.listGames` y escribe con `GameIndexRepository.replaceForSystem` /
`ScannedGameDao.replaceForSystem` (`DELETE … WHERE systemId = :id` + insert, en vez del `clearAll` de `replaceAll`).

**Eliminar juego** (`GamesViewModel.deleteGame`): irreversible, sin backup, confirmado antes en el modal. Pasos:

1. **`RomRepository.deleteGame`** — busca el `DocumentFile` *por nombre* dentro de `<carpeta ROMs>/<sistema>/`
   (`SafUtils.findChild`), no a partir del `romUri` guardado: en sistemas `FOLDER_PER_GAME` (Switch) ese `romUri` apunta
   solo al archivo base lanzable, y borrar solo ese dejaría atrás updates/DLC. Buscar por `romFileName` (que ahí es el
   nombre de la carpeta) borra la carpeta completa; `DocumentFile.delete()` sobre un directorio es recursivo.
2. **`AssetRepository.deleteIcon`** — borra `Assets/<sistema>/icons/<romFileName>.png` si existe; además
   `ScrapStorage.deleteGameAssets` borra lo que descargó el scrapper. No toca `names.json` (una entrada huérfana no estorba).
3. **`GameIndexRepository.deleteGame`** (`ScannedGameDao.deleteByRomUri`) — saca la fila cacheada y se quita de favoritos.
   Como la grilla observa un `Flow`, se actualiza sola.

**Permiso de escritura sobre la Carpeta de ROMs.** `SettingsViewModel.onRomFolderPicked` persiste también
`FLAG_GRANT_WRITE_URI_PERMISSION`. No es retroactivo: una carpeta configurada antes conserva el grant de solo lectura y el
usuario debe volver a elegirla una vez; hasta entonces `DocumentFile.delete()` falla en silencio (`deleteGame` devuelve
`false`) y se muestra el toast «No se pudo eliminar "<juego>".» (ver `README.md`).

## Sistemas mostrados y organización de ROMs

- La pantalla de sistemas solo lista los que tienen `<carpeta ROMs>/<sistema>/` presente **y** con
  al menos un juego válido adentro (`SystemsViewModel.tiles`, recalculado cada vez que cambian las
  carpetas configuradas) — evita mostrar sistemas vacíos que solo llevarían a un grid en blanco.
  Desde el rediseño 2026-09-25, `tiles` es `List<SystemTileItem>` en vez de `List<SystemDefinition>`
  directamente: antepone un tile sintético "Favoritos" cuando hay al menos un juego marcado como
  favorito (ver "Rediseño visual y de navegación").
- `SystemDefinition.romOrganization` distingue dos formas de escanear un sistema
  (`RomRepository.listGames`): `FLAT_FILES` (un archivo = un juego, todos los sistemas salvo Switch)
  y `FOLDER_PER_GAME` (una subcarpeta = un juego, con el base + updates/DLC juntos adentro — la
  convención de Switch, reconocida igual por emuladores de escritorio como Eden). Para
  `FOLDER_PER_GAME`, `pickBaseGameFile` elige qué archivo de la carpeta lanzar: descarta los que
  tengan "update"/"upd"/"dlc"/"patch" en el nombre, y si eso no alcanza para desambiguar, se queda
  con el más grande.
- Íconos de Switch: se extraen del icon.png en `Assets/switch/icons/<NombreDeCarpeta>.png`, igual
  que el resto de los sistemas — **no** se intenta extraer el ícono embebido de los archivos
  NSP/XCI. Eso requeriría reimplementar buena parte de un parser de contenedores de Switch
  (NCA/PFS0/RomFS) con descifrado AES usando las claves propias del usuario (`prod.keys`), al
  estilo de lo que hace la librería LibHac en Ryujinx — un proyecto en sí mismo, fuera del alcance
  de este frontend.
- **PICO-8 (agregado 2026-08-01), extensiones compuestas**: el formato de cart "visual" de PICO-8
  es `.p8.png` — un PNG real (visible como screenshot/label del juego) con el código y los datos del
  cart esteganografiados en los bits menos significativos de los píxeles; el `.p8` a secas es el cart
  de texto plano, sin imagen. Como `.p8.png` tiene dos puntos, `RomRepository` matchea extensiones
  por sufijo (`matchesExtension`, `endsWith(".$ext")`) en vez de "lo que sigue al último punto"
  (`substringAfterLast`), que solo hubiera visto `"png"`. `SystemCatalog.kt` juega con el core libretro
  **fake-08** (reimplementación open-source compatible con PICO-8, no el runtime oficial de
  Lexaloffle) vía RetroArch, igual que el resto de los sistemas RetroArch.
- **Ícono embebido en el propio ROM (agregado 2026-08-01)**: `SystemDefinition.embeddedIconExtensions`
  marca qué extensiones (compuestas o no) ya son, en sí mismas, una imagen válida — solo PICO-8
  (`.p8.png`) por ahora. `RomRepository.scanFlatFiles` usa el ícono propio de
  `Assets/<sistema>/icons/` cuando existe, y si no, cae al `Uri` del ROM mismo (`GameEntry.iconUri
  = romUri`) en vez del placeholder genérico. Como el cart `.p8.png` es **portrait** (no cuadrado) y
  `AsyncImage` en `GamesScreen.kt` no fija `contentScale` (default de Coil: `ContentScale.Fit`) sobre
  un `FocusableTile` con fondo blanco, el resultado ya sale redimensionado
  para entrar en el cuadrado sin recortar y con el sobrante en blanco — **sin ningún procesamiento de
  bitmap adicional**, es consecuencia directa de cómo ya se renderizaban todos los íconos.

## Diccionario de nombres de juego (`names.json`)

- Muchos sistemas (NeoGeo, MAME, FBNeo, etc.) usan nombres de archivo de ROM en clave arcade
  (`3countb.zip`) en vez del nombre real del juego. Para mostrar un nombre legible sin depender de
  scraping externo, cada sistema admite un diccionario **opcional** y de edición manual:
  `Assets/<sistema>/names.json`, al mismo nivel que `icons/` y `system.png`.
- Formato: un array JSON de objetos `{"file": "<nombre exacto de archivo o carpeta>", "name":
  "<nombre a mostrar>"}`. `file` es el nombre completo tal como aparece en la carpeta de ROMs
  (con extensión para `FLAT_FILES`, nombre de carpeta para `FOLDER_PER_GAME` como Switch) — la
  comparación contra `GameEntry.romFileName` es case-insensitive (`AssetRepository.loadNameIndex`),
  igual que la de íconos.
- Si el archivo no existe, no se puede leer, o el JSON es inválido, se ignora silenciosamente y el
  sistema completo cae al comportamiento anterior (nombre de archivo sin extensión / nombre de
  carpeta). Si existe pero un ROM puntual no tiene entrada, ese ROM individual también cae al mismo
  fallback — el diccionario no necesita cubrir el 100% de los archivos de la carpeta.
- Esto **solo afecta el nombre mostrado** (`GameEntry.displayName`); el nombre de archivo real (`romFileName`) sigue
  siendo la identidad del juego (favoritos, assets, lanzamiento).
- **Nombre de archivo visible junto al nombre bonito (agregado 2026-07-31)**: `GinnyBottomBar`
  (`ui/components/BottomBar.kt`) admite un `selectedSecondaryText` opcional, una segunda línea más
  chica (`labelSmall`, color `palette.muted`) debajo del nombre seleccionado. En
  `GamesScreen` se le pasa `romFileName`, pero **solo cuando difiere de `displayName`** — si no hay
  `names.json` (o el ROM puntual no tiene entrada), `displayName` ya es el nombre de archivo (sin
  extensión, o la carpeta completa en `FOLDER_PER_GAME`), así que mostrar la segunda línea sería
  repetir el mismo texto. La condición es la misma razón por la que el equivalente en Ginny Assets
  Manager muestra el nombre de archivo entre paréntesis solo cuando `displayName !== id`.
- Es un archivo de texto plano en la Carpeta de Usuario. El usuario puede editarlo directamente a
  mano, **o** desde Ginny Assets Manager (la herramienta de escritorio hermana): agregado
  2026-08-01, ahora administra `names.json` para MAME/NeoGeo/FBNeo vía el escaneo automático y para
  cualquier sistema vía "Buscar opciones" o "Editar metadatos" — ver
  `docs/ginny-assets-manager.md#diccionario-de-nombres-namesjson` para el detalle completo (ya no
  aplica la nota de "fuera de alcance" de `docs/prompt-asset-manager-nwjs.md`, desactualizada desde
  ese agregado).

## Batería no confiable

`BatteryObserver` oculta el indicador si: el sistema reporta que no hay batería (`EXTRA_PRESENT` falso, desde 2026-10-14), no llega ningún `ACTION_BATTERY_CHANGED` en los primeros
5s tras registrar el receiver, el valor reportado está fuera de 0–100, o el valor queda exactamente
fijo por más de 10 minutos de uso *sin estar cargando* (para no confundir un 100% real mientras carga
con un valor stub).

## Verificación de build

Este entorno de ejecución no tenía JDK/Android SDK instalados. Se descargaron un JDK y el SDK de
línea de comandos ad-hoc para validar el proyecto real, pero el runner de Gradle de este sandbox no
puede abrir sockets de loopback con JDK 17 (requerido por AGP 8.4.2) — es una restricción del
entorno, no del proyecto. Para no dejar el código sin verificar, se compiló el mismo código fuente
en una copia temporal con versiones más viejas de AGP/Gradle/Kotlin/Compose (compatibles con JDK 11,
que sí pudo abrir el socket) y se llegó a `assembleDebug` exitoso (APK generado). Eso confirma que
el Kotlin/Compose es correcto; lo único no probado end-to-end en este entorno son las versiones
exactas de AGP 8.4.2/Gradle 8.7 que quedaron configuradas en el proyecto real — deberían compilar
igual o mejor en Android Studio con JDK 17, pero conviene que la primera sync la corras vos y me
avises si algo no matchea.

La incorporación de Room + plugin `kapt` (ver "Caché de biblioteca") es posterior a esa verificación
y **no se compiló** — se agregó sin volver a correr el workaround de JDK/SDK ad-hoc descripto arriba.
Primera sync/build después de ese cambio conviene hacerla con más atención de lo normal, en particular
el annotation processing de Room (`kapt`, generación de `GinnyDatabase_Impl`/DAOs).

**Actualización 2026-09-25**: en esta máquina sí había un JDK/Android SDK reales instalados
(`C:\Program Files\Android\Android Studio\jbr`, el JBR de Android Studio, JDK 21; y el SDK en
`%LOCALAPPDATA%\Android\Sdk`) — se corrió `gradlew compileDebugKotlin` y `gradlew assembleDebug`
**vía PowerShell, no bash** (el sandbox de bash de este entorno sigue sin poder abrir sockets de
loopback, pero PowerShell no tiene esa restricción) con las versiones reales del proyecto (Gradle
8.7, Kotlin 1.9.24, AGP tal como está en `build.gradle.kts`), sin downgrades. Ambos terminaron en
`BUILD SUCCESSFUL`. Esto verifica compilación y empaquetado (dexing, recursos, fuentes, manifest)
del rediseño completo (ver sección siguiente), pero **no** verifica renderizado/comportamiento en
un dispositivo real: no había ningún emulador/dispositivo conectado (`adb devices` vacío) para
tomar capturas o probar la navegación por D-Pad a mano. Conviene correrlo vos y avisar si algo no
se ve como se espera.

## Interfaz actual (reconstruida 2026-09-27 → 2026-10)

Look «glass»/pastel derivado de OKLCH, a partir de la maqueta `docs/diseño/`. Se reconstruyó paso a paso (historia y
decisiones en [plan-reconstruccion-ui.md](plan-reconstruccion-ui.md)).

**Estructura**: `GinnyApp` dibuja el fondo (`GeometricBackground`) y, encima, tres filas: `Header`, `Content` y `Footer`.
No hay `NavHost`: `Screen` (`Systems` / `Games(scope, title)`) es un estado simple y el cambio entre pantallas es un
fundido de 180 ms (`AnimatedContent`; entrar a Juegos además crece de 0.97 a 1; la pantalla que sale no responde a la
cruceta). Cada sistema es una pantalla distinta (`contentKey`) con su propio `GamesViewModel`.

**Sistema de color y tipografía** (`ui/theme/`): toda la UI deriva sus colores de un matiz (`GinnyThemes.current`);
`ThemeMode` ofrece 4 matices fijos más `CICLO`, que anima el matiz (el reloj por fotograma solo corre en Ciclo). El tema se
guarda en `SettingsRepository` y en `ginny.cfg` (`themeMode`). Tipografía M PLUS Rounded 1c (OFL, pesos estáticos
Medium/Bold/ExtraBold/Black).

**Configuración como overlay** (`SettingsModal`): una capa más en el mismo árbol (no un `Dialog`), con la interfaz de atrás
desenfocada (`Modifier.blur`, API 31+; en 29–30 solo la cortina). Barra lateral de secciones (Interfaz, Carpetas,
Sistemas, Scrapper, Créditos) y panel de opciones. Es **navegable con cruceta**: `SettingsNav` (foco espacial sobre los
controles marcados con `settingsFocus`, cursor `SettingsCursorOverlay`, panel con desplazamiento y bordes que se
desvanecen). La sección Sistemas es maestro-detalle: lista de sistemas a la izquierda (panel secundario, `SettingsPane`) y
las opciones del elegido a la derecha. Los modales de scrapeo usan `rememberModalNav` / `ScrapModalScrim` con el mismo cursor.

**Botones y prioridad de «atrás»**: `MainActivity.dispatchKeyEvent` atiende primero Menú / Y / B por hardware (Y abre
Configuración en Sistemas y el modal de juego en Juegos). El resto lo resuelven `BackHandler`s en `GinnyApp`, con prioridad
por orden de registro (gana el último): modal del juego, scrapeo, credenciales, desplegable (`GinnyMenu`), Configuración,
volver de Juegos a Sistemas y, en Sistemas, «Cerrar» (B) → `ExitConfirmModal` → `finishAffinity()`.

**Desplegables propios** (`GinnyMenu`): se dibujan en la misma ventana (capa raíz, patrón *portal*), con cruceta y
sonidos, en lugar de `DropdownMenu` (ventana aparte que no recibe la cruceta de forma fiable).

**Favoritos**: `SettingsRepository.favoriteGameKeys` (claves `"${systemId}|${romFileName}"`) — deliberadamente **no** en
Room, porque `replaceAll`/`replaceForSystem` borran y reinsertan en cada escaneo. `SystemTileItem` agrega el tile
sintético «Favoritos» cuando hay alguno, y `GamesViewModel` generaliza con `GamesScope` (`Single(systemId)` o `Favorites`).
Se sincronizan con `ginny.cfg`.

**Primer arranque** (`WelcomeScreen`): si falta la Carpeta de Usuario o la de ROMs (o `welcomePending`), se muestra la
bienvenida con los botones Usuario / ROMs / Continuar; al continuar escanea y muestra los resultados.

**Pantalla secundaria** (`ui/display/`): en consolas de doble pantalla muestra el logo del sistema enfocado o la
portada del juego enfocado (`SecondaryScreen.focus`).

## Scrapper (agregado 2026-10-10)

Ginny Frontend obtiene por sí mismo los metadatos y el arte de cada juego (el Assets Manager se
actualizará después para hacer lo mismo). Plan completo, decisiones y hallazgos de las pruebas contra
las APIs reales: [plan-scrapper.md](plan-scrapper.md). Todo vive en `data/scrap/`.

- **Proveedores.** ScreenScraper (metadatos, wheel, caja frente/trasera/lomo, video; sistema
  `screenscraper/`) y SteamGridDB (ícono cuadrado; sistema `steamgriddb/`). Cada uno tiene su cliente,
  parser puro, selección y traducción de códigos HTTP.
- **Resultados tipados, sin excepciones** (`ScrapResult`): `Ok`, `NotFound`, `AuthFailed`,
  `QuotaExhausted`, `RateLimited`, `Unavailable`, `StorageFailed`, `NetworkBlocked`.
- **Red.** `ScrapHttp` (OkHttp, sin log: las URLs de ScreenScraper llevan credenciales; cancelable
  también a mitad de una lectura), `ScrapRateLimiter` (serializa por proveedor; la cuenta de ScreenScraper
  permite 1 hilo; reintento con espera ante 429), `ScrapNetwork` ("Solo con Wi-Fi"), `ScrapRedactor`
  (quita credenciales de cualquier texto).
- **Credenciales.** Las del usuario (cuenta de ScreenScraper, clave de SteamGridDB) están en un DataStore
  propio (`ScrapperSettingsRepository`), **fuera de `ginny.cfg` por defecto** (texto plano en la Carpeta de Usuario,
  que se sincroniza). **Opt-in (2026-10-13)**: Configuración > Scrapper > "Guardar credenciales en la Carpeta de Usuario"
  (desactivado por defecto; al activarlo, un modal avisa del texto plano y del riesgo de compartir la carpeta) las escribe
  en `ginny.cfg` (`saveScrapperCredentials` + `scrapperCredentials{screenScraperUser, screenScraperPassword,
  steamGridDbApiKey}`) para compartirlas con Ginny Frontend Desktop; apagarlo las borra del archivo. El DataStore sigue
  excluido de las copias de seguridad (`res/xml/backup_rules.xml`,
  `data_extraction_rules.xml`). Las de **desarrollador** de ScreenScraper van incrustadas en el APK
  (`BuildConfig`, desde `scrapper.properties`, ignorado por git; ver `ScrapperDevCredentials` y el README).
- **Almacenamiento** (`ScrapStorage` / `SystemAssets`, SAF). Escribe en `Assets/<sistema>/`; ver el
  contrato abajo. **Nunca sobrescribe** imágenes, escribe en `.part` y renombra (un corte no deja
  archivos truncados), lista cada carpeta una sola vez y convierte cualquier excepción en
  `StorageFailed`. El ícono se reduce a 512 px y se guarda como PNG (`IconProcessor`), nunca se agranda.
- **Orquestación** (`ScrapRepository.run` → `Flow<ScrapProgress>`; `ScrapRunner` lo ejecuta en el scope de
  la aplicación). Secuencial, reanudable y cancelable. La decisión de qué pedir es código puro y probado
  (`ScrapPlanner`): no se repite lo guardado ni lo que el proveedor dijo que no tiene; "no encontrado"
  dura 30 días; forzar ignora esas cachés pero no sobrescribe archivos. Un proveedor con credenciales
  rechazadas o cupo agotado se deja de usar y el lote sigue con el otro.
- **Contrato de `Assets/<sistema>/`** (compartido con el Assets Manager). Cada archivo se llama
  `<romFileName>.<extensión>`, con `romFileName` = nombre del archivo de la ROM **con su extensión** (o de
  la carpeta, en Switch): `icons/` (PNG cuadrado, el de siempre), `wheel/`, `box-front/`, `box-back/`,
  `box-spine/` (PNG/JPG/WebP en su formato original), `video/` (MP4) y `metadata/` (JSON, `GameMetadata`:
  nombre estándar, sinopsis, fecha de primera publicación y por región, desarrollador, editor, géneros,
  jugadores, nota, región de cada imagen, `status` found / not_found / pending y `missingMedia`).
  Las carpetas se crean al primer uso, no al elegir la Carpeta de Usuario.
- **Pruebas unitarias en JVM** (`app/src/test`, JUnit + `org.json`): la lógica pura (parsers, selección,
  planificador, normalizador de nombres, hash, redactor, metadatos). La capa SAF, `IconProcessor` y los
  clientes por red no tienen pruebas automáticas: usan APIs de Android o la red.

## Limitaciones conocidas / seguidas para v2

- Mapeo de botones en Settings no es D-Pad-navegable todavía (usa foco por defecto de Compose,
  funciona por toque siempre; por teclado/D-Pad depende del focus traversal por defecto).
- Save states: solo RetroArch en v1, tal como acota el prompt. Dolphin/PPSSPP/AetherSX2 quedan para
  una fase 2 si se decide investigar sus formatos propios.
- Los logos de sistema por defecto son generados en runtime (mosaico de color + sigla) salvo que se
  reemplacen los PNGs en `app/src/main/assets/system-logos/` antes de compilar.
