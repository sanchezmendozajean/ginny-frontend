# Arquitectura — Ginny Frontend

> **Nota (2026-10-13):** `ginny.cfg` ya no guarda la carpeta de ROMs, las carpetas de ROMs por sistema ni `lastScanAt`
> (son de cada dispositivo; ver `DEVICE_LOCAL_KEYS` en `GinnyConfigRepository`). Sí guarda `themeMode` y `favorites`, y
> conserva las claves desconocidas al reescribirse.

> **Nota (2026-10-13):** la pantalla de **Estados guardados** se eliminó (`SaveStatesViewModel`,
> `SaveStateRepository`, `SaveStateEntry`, `saveStateSlot`/`STATE_SLOT`, ajuste `retroArchSavesUri`). Las
> menciones a ella más abajo son históricas. Ver [pendientes-etapa-2.md](pendientes-etapa-2.md) punto 1.

> **Nota (2026-09-27):** la capa `ui/` se está reconstruyendo paso a paso — ver
> [plan-reconstruccion-ui.md](plan-reconstruccion-ui.md). Las secciones de este documento que
> describen componentes visuales (`PagedFocusGrid`, TopBar, overlays, tema) se refieren a la
> versión archivada en [ui-legacy/](ui-legacy/); la capa de datos, lanzadores y audio siguen vigentes.

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
├── MainActivity.kt          # Shell único, immersive mode, manejo global de B/Menu por hardware
├── GinnyApplication.kt      # Punto de entrada del contenedor de DI manual
├── di/AppContainer.kt       # DI manual (repos + casos de uso), sin Hilt — la app es chica
├── data/
│   ├── model/                # SystemId, SystemDefinition, SystemCatalog (catálogo hardcodeado),
│   │                          # EmulatorTarget, RetroArchCore, GameEntry, SaveStateEntry
│   ├── SettingsRepository.kt  # DataStore Preferences: filas de grilla, carpetas, emulador/core
│   │                          # por sistema, volumen, mapeo de botones
│   ├── RomRepository.kt       # Escaneo de ROMs vía SAF (DocumentFile), filtrado por extensión
│   │                          # (scanAllSystems: corrida completa, solo desde Settings)
│   ├── GameIndexRepository.kt # Fachada sobre Room: caché de la última corrida de escaneo
│   ├── db/                    # Room: ScannedGameEntity, ScannedGameDao, GinnyDatabase
│   ├── AssetRepository.kt     # Íconos, logos y diccionario de nombres (names.json) desde la
│   │                          # carpeta de Usuario (Assets/<sistema>/...)
│   ├── SaveStateRepository.kt # Detección de miniaturas de save-state de RetroArch
│   ├── scrap/                 # Scrapper (ScreenScraper + SteamGridDB): clientes, almacenamiento en
│   │                          # Assets/, orquestador del lote. Ver "Scrapper" más abajo
│   ├── StorageBootstrapper.kt # Crea Assets/<sistema>/{icons/, system.png} en la carpeta de Usuario
│   └── ButtonMapping.kt       # Mapeo lógico->keycode físico remapeable
├── launch/                   # Patrón Strategy: un LauncherStrategy por emulador
│   ├── LauncherStrategy.kt    # Interfaz + chequeo de instalación + resultado de lanzamiento
│   ├── RetroArchLauncher.kt   # Intent explícito a RetroActivityFuture + extras ROM/LIBRETRO
│   ├── GenericViewLauncher.kt # Dolphin/PPSSPP/AetherSX2/Redream vía ACTION_VIEW + setPackage
│   ├── LauncherFactory.kt     # EmulatorTarget -> LauncherStrategy
│   └── GameLauncher.kt        # Caso de uso: resuelve emulador/core/override desde Settings y lanza
├── audio/SoundManager.kt     # SoundPool (foco/selección) + MediaPlayer en loop (música), tracks
│                              # provistos por el usuario (ver app/src/main/res/raw) — la pista de
│                              # fondo es elegible en Settings entre las 2 embebidas y cualquier
│                              # archivo en Interface/Background Music de la Carpeta de Usuario
├── system/BatteryObserver.kt # Heurística de batería no confiable (ver sección abajo)
└── ui/
    ├── theme/                 # Paleta OKLCH generada por matiz (4 temas + modo Ciclo animado),
    │                          # tipografía M PLUS Rounded 1c (pesos estáticos, OFL)
    ├── components/            # PagedFocusGrid (grid reutilizable, paginado), Tile, TopBar (acceso
    │                          # directo a Configuración, ver sección propia abajo), BottomBar,
    │                          # GlassSurface, AuroraBackground, ButtonBadge, SegmentedPillPicker,
    │                          # GinnySlider, PillDropdown, PageJumpButton
    ├── common/                # Modales compartidos: GinnyModalSurface (wrapper), GameOptionsDialog,
    │                          # EmulatorConfigDialog, ScanProgressDialog
    ├── navigation/GinnyNavHost.kt  # systems -> games -> saves, + settings
    └── screens/{systems,games,savestates,settings}/  # Screen + ViewModel por pantalla
```

## Gestión de estado

- **ViewModel + StateFlow** por pantalla (`SystemsViewModel`, `GamesViewModel`,
  `SaveStatesViewModel`, `SettingsViewModel`), instanciados con `viewModelFactory { initializer { } }`
  contra el `AppContainer` — no se usa Hilt/Dagger: la superficie de la app no lo justifica.
- **DataStore Preferences** (`SettingsRepository`) para toda la configuración persistente: filas de
  grilla, URIs de carpetas (ROMs/Usuario/saves de RetroArch), emulador y core por sistema, override
  de package de PS2, volúmenes, mapeo de botones.
- El foco del grid (`focusedIndex`) vive en el ViewModel de cada pantalla, no en el componente de
  grid, para sobrevivir recomposiciones y ser reutilizable entre pantallas.
- **Room** (`data/db/`) cachea el resultado del último escaneo de ROMs — el catálogo de sistemas
  sigue siendo estático (`SystemCatalog`), pero la lista de *juegos* ya no se deriva en vivo. Detalle
  y motivación en "Caché de biblioteca" más abajo.

## El componente de grid (`PagedFocusGrid`, rediseño 2026-09-25)

Pieza más compleja del proyecto, aislada en `ui/components/PagedFocusGrid.kt` +
`FocusGridMath.kt` (aritmética pura, sin Compose, para poder razonar/testear la navegación por
separado del layout). Reemplaza al `SnapFocusGrid` original (scroll vertical continuo) por un
modelo de **páginas discretas que se deslizan horizontalmente** — ver "Rediseño visual y de
navegación" más abajo para el porqué del cambio:

- Tamaño de tile = `(alturaDisponible - gap*(filas-1)) / filas`; columnas = las que entren en el
  ancho disponible con ese tamaño de tile — igual que pide el spec (sin cambios respecto al grid
  anterior).
- Una "página" son `columnas × filasVisibles` ítems; `FocusGridMath.pageOf`/`pageCount`/
  `topRowForPage` calculan a qué página pertenece cada índice — a diferencia del `nextTopRow`
  original (ventana de desplazamiento mínimo), la ventana de una página siempre está alineada a un
  múltiplo de `filasVisibles`, nunca a mitad de fila.
- Navegación D-Pad: `Modifier.onKeyEvent` interpreta el keycode físico a través del
  `ButtonMapping` remapeable configurado en Settings, no hardcodea `KEYCODE_DPAD_*`.
  `moveUp/Down/Left/Right` se reusan sin cambios del grid anterior (Izquierda/Derecha sigue sin
  hacer wrap a la fila anterior/siguiente). L/R (además de los botones táctiles `PageJumpButton` en
  los bordes de la pantalla) saltan una página entera en vez de saltar filas dentro del scroll.
- `PagedFocusGrid` reporta `currentPage`/`pageCount`/`goToPage` hacia la pantalla que lo usa vía
  `onPagingChanged` (un `SideEffect`, no un callback durante composición), para que
  `GinnyBottomBar` pueda dibujar los puntos de página y los botones L/R queden fuera del propio
  componente de grid — evita que el grid necesite conocer detalles de layout de la barra inferior.
- B (retroceder) deliberadamente *nunca* es parte de la navegación por foco del grid — es solo
  táctil/botón físico dedicado, manejado a nivel de `MainActivity.dispatchKeyEvent` para funcionar
  sin importar qué composable tenga el foco en ese momento.
- Y (nuevo `LogicalButton`) abre Configuración en Sistemas/Estados guardados, y el modal
  "Opciones de juego" (`GameOptionsDialog`) en Juegos — ver "Rediseño visual y de navegación".

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
- **Emulador no instalado → reconfigurar en el momento (agregado 2026-08-11)**: cuando
  `LaunchResult.EmulatorNotInstalled` llega hasta `GamesScreen`/`SaveStatesScreen`, ya no se muestra
  un aviso de solo lectura (`EmulatorMissingDialog`, eliminado) — se abre
  `ui/common/EmulatorConfigDialog.kt`, que deja elegir un emulador/core (o override de package)
  distinto para ese sistema sin salir de la pantalla. La selección se mantiene en estado local del
  diálogo (no se persiste hasta confirmar) y se escribe a `SettingsRepository` recién al tocar
  "Guardar y Lanzar la ROM" (persiste y reintenta el mismo `GameEntry`/`SaveStateEntry` que había
  fallado) o "Guardar y Cerrar" (persiste sin relanzar). Para esto, `GamesViewModel` y
  `SaveStatesViewModel` exponen ahora `settingsRepository`/`assetRepository` directamente (mismo
  patrón que ya usaba `SettingsViewModel`), en vez de mantenerlos encapsulados detrás de métodos
  específicos.

## Menú de opciones (⋮) y gestión de juegos (agregado 2026-08-17, superado 2026-09-25)

**Sección histórica** — el rediseño visual de 2026-09-25 (ver más abajo) eliminó por completo el
menú desplegable `OverflowMenu`/`menuExpanded` descripto en esta sección: Menú/la hamburguesa
ahora abre Configuración directo (sin desplegable) y las acciones de juego (seleccionar emulador,
reescanear, eliminar) viven en el modal `GameOptionsDialog` (botón Y). Se deja el resto de esta
sección tal cual para contexto histórico de las decisiones de "Eliminar juego"/permisos de
escritura, que siguen vigentes.

**De botón físico Menú → Configuración directo, a menú desplegable D-Pad-navegable.** El botón
físico Menú abría antes `Routes.SETTINGS` directamente (`MainActivity.dispatchKeyEvent`), sin pasar
por el menú desplegable que ya usaba el botón táctil X. Ahora ambos abren el mismo menú:

- El estado `menuExpanded: MutableState<Boolean>` se crea en `GinnyNavHost` (no local a
  `GinnyTopBar`) y se expone hacia `MainActivity` vía `onMenuStateReady`, el mismo patrón ya usado
  para `onNavControllerReady`/`NavHostController` — necesario porque `MainActivity.dispatchKeyEvent`
  corre fuera de Compose y necesita poder togglear el menú sin importar qué pantalla esté activa en
  ese momento. `GinnyTopBar` resetea el estado a `false` en su `DisposableEffect(Unit) { onDispose
  { ... } }`, así una pantalla recién entrada nunca hereda un menú que quedó abierto en la anterior.
- Con el menú abierto, **B** ahora lo cierra en vez de navegar hacia atrás — chequeado primero en
  `MainActivity.dispatchKeyEvent` (`if (menu.value) { menu.value = false; return true }`), antes del
  chequeo de `popBackStack()`, porque el intercept de teclado a nivel Activity corre *antes* que
  cualquier `onKeyEvent` de Compose y por lo tanto antes que el propio menú pudiera reaccionar a B.
- **Navegación por cruceta dentro del menú** (`OverflowMenu`, privado en `TopBar.kt`): en vez de
  confiar en que `DropdownMenu` soporte flechas de teclado de forma implícita (no está documentado
  que lo haga de forma confiable), se reutiliza el mismo patrón manual ya probado en
  `SnapFocusGrid`/`FocusGridMath` — un único `FocusRequester` a nivel contenedor (no uno por ítem) +
  un índice `highlightedIndex` local que Arriba/Abajo mueve y A dispara, leyendo el keycode físico a
  través del `ButtonMapping` remapeable (no hardcodea `KEYCODE_DPAD_*`), igual que el grid.
- **Opciones agrupadas** (`GinnyTopBar(extraMenuGroups: List<List<MenuAction>>)`): cada pantalla
  arma sus propios grupos, cada uno separado del siguiente por un divisor; grupos vacíos se
  descartan antes de renderizar (así "Seleccionar emulador"/"Eliminar juego" desaparecen limpio
  cuando no hay juego seleccionado, sin dejar un separador huérfano). `SystemsScreen`/
  `SaveStatesScreen` no pasan ningún grupo extra (queda solo Configuración/Salir de Ginny);
  `GamesScreen` arma dos grupos: `[Seleccionar emulador (si hay selección), Reescanear este
  sistema]` y `[Eliminar juego (si hay selección)]`.

**Reescanear un solo sistema** (`GamesViewModel.rescanSystem`): mismo escaneo SAF que
`SettingsViewModel.scanLibrary`, pero acotado a un `systemId` en vez de `SystemCatalog.all` —
reutiliza `RomRepository.listGames` (ya existía, usado internamente por `scanAllSystems`) y escribe
con el nuevo `GameIndexRepository.replaceForSystem`/`ScannedGameDao.replaceForSystem`
(`DELETE FROM scanned_games WHERE systemId = :id` + insert, en vez del `clearAll` sin filtro que usa
`replaceAll`). Evita re-escanear toda la biblioteca cuando el usuario solo tocó los ROMs de un
sistema puntual.

**Eliminar juego** (`GamesViewModel.deleteGame`, `ui/common/DeleteGameDialog.kt`): irreversible, sin
backup, confirmado con un modal antes de ejecutar (mismo patrón que `EmulatorConfigDialog`: estado
`gameToDelete: GameEntry?` en `GamesScreen`, mostrado condicionalmente). Tres pasos:

1. **`RomRepository.deleteGame`** — resuelve el `DocumentFile` a borrar buscándolo *por nombre*
   dentro de `<carpeta ROMs>/<sistema>/` (`SafUtils.findChild`), no reconstruyéndolo a partir del
   `romUri` guardado en el `GameEntry`. La razón: en sistemas `FOLDER_PER_GAME` (Switch), ese
   `romUri` apunta solo al archivo base que `pickBaseGameFile` eligió como lanzable, adentro de la
   carpeta del juego — borrar únicamente ese archivo dejaría atrás los updates/DLC de la misma
   carpeta. Buscar por `romFileName` (que para esos sistemas *es* el nombre de la carpeta) encuentra
   y borra la carpeta completa de una. `DocumentFile.delete()` sobre un directorio es recursivo
   (`DocumentsContract.deleteDocument` está documentado como tal), así que no hace falta vaciarla a
   mano primero.
2. **`AssetRepository.deleteIcon`** — borra `Assets/<sistema>/icons/<romFileName>.png` si existe.
   No toca `names.json` (la entrada del juego borrado queda huérfana ahí, pero es un diccionario de
   edición manual y no afecta nada si sobra una entrada sin ROM correspondiente).
3. **`GameIndexRepository.deleteGame`** (`ScannedGameDao.deleteByRomUri`) — saca la fila cacheada.
   Como `GamesViewModel` observa `observeGames(systemId)` vía `Flow`, la grilla se actualiza sola;
   no hace falta releer nada explícitamente tras el borrado.

**Permiso de escritura sobre la Carpeta de ROMs (fix de bug retroactivo).** Antes de esto,
`SettingsViewModel.onRomFolderPicked` solo persistía `FLAG_GRANT_READ_URI_PERMISSION` — suficiente
mientras la carpeta solo se leía (escaneo). Con "Eliminar juego" necesitando escritura, se agregó
`FLAG_GRANT_WRITE_URI_PERMISSION` a esa llamada. **Esto no es retroactivo**: una Carpeta de ROMs ya
configurada por un usuario antes de este cambio conserva el grant de solo lectura que el sistema le
dio en su momento — `takePersistableUriPermission` solo puede formalizar flags presentes en un grant
de Intent vigente, y ese grant expiró hace rato. El único arreglo es que el usuario vuelva a
seleccionar la misma carpeta una vez desde Configuración; hasta entonces, `DocumentFile.delete()`
falla en silencio (`RomRepository.deleteGame` devuelve `false`, no lanza excepción) y
`GamesViewModel.deleteGame` lo traduce al toast "No se pudo eliminar \"<juego>\"." — documentado en
el `README.md`.

**"Seleccionar emulador" reutiliza `EmulatorConfigDialog`** en vez de un modal nuevo — es el mismo
diálogo que ya aparecía cuando fallaba un lanzamiento por `LaunchResult.EmulatorNotInstalled`. Como
ahora también se abre a propósito (el emulador puede estar perfectamente instalado, el usuario solo
quiere cambiarlo), el diálogo ganó parámetros `title`/`description` para no mostrar el copy de
"Emulador no encontrado" fuera de contexto: `GamesScreen` distingue el motivo con un
`EmulatorConfigRequest(game, manual: Boolean)` local en vez del `GameEntry?` suelto que había antes.

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
- Esto **solo afecta el nombre mostrado** (`GameEntry.displayName`); `SaveStateRepository` sigue
  matcheando los thumbnails de save-state de RetroArch contra el nombre de archivo real
  (`romFileName`, con y sin extensión), no contra el nombre del diccionario, porque RetroArch nombra
  esos archivos según el contenido cargado, no según ningún alias cosmético.
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

## Rediseño visual y de navegación (agregado 2026-09-25)

Rediseño completo del look & feel y parte de la navegación, a partir de una maqueta interactiva
(`docs/diseño/Ginny Frontend.dc.html`) que reemplaza la estética plana original por un lenguaje
"glass"/pastel derivado de OKLCH. Mantiene el 100% de las funciones que ya existían (mapeo de
botones, todas las carpetas, escaneo, emuladores/cores por sistema) — nada se sacó, solo se le
aplicó la piel nueva y se sumaron features que la maqueta insinuaba pero la app no tenía.

**Sistema de color** (`ui/theme/OklchColor.kt`, `GinnyPalette.kt`, `ThemeMode.kt`, `CycleHue.kt`):
toda la UI deriva sus colores de un solo matiz (`hue: Float`) vía `ginnyPalette(hue)`, una
traducción directa del `renderVals()` de la maqueta (conversión OKLCH→sRGB propia, sin librería
externa). `ThemeMode` tiene 4 hues fijos (Menta/Cielo/Durazno/Lila) más `CICLO`, que anima el hue
con `cycleHue()` (hold 6s + easing cúbico 2.5s entre paradas, replicado 1:1 del prototipo) vía
`rememberCyclingHue()` — el reloj de animación por frame **solo corre si Ciclo está seleccionado**,
para no gastar batería de más en el caso común de un tema fijo. Persistido en
`SettingsRepository.themeMode` (clave `theme_mode`, un enum-name, mismo idioma que el resto del
repo) y seleccionable en Configuración > Interfaz > Tema (mismo `SegmentedPillPicker` que "Filas
visibles"). `GinnyColors` (paleta plana hardcodeada) se eliminó por completo — todo el código lee
`LocalGinnyPalette.current` en su lugar.

**Tipografía**: Baloo2 → M PLUS Rounded 1c (Google Fonts, OFL, ver `licenses/MPLUSRounded1c-OFL.txt`).
A diferencia de Baloo2 (una sola variable font), M PLUS Rounded 1c se distribuye como archivos
estáticos por peso — se bundlean solo los 4 pesos realmente usados (Medium/Bold/ExtraBold/Black;
Regular se omitió porque el texto de cuerpo sigue en Roboto, igual que antes) para no inflar de más
el APK; cada peso ronda 3.4MB, ~14MB en total — un tradeoff de tamaño consciente frente a Baloo2's
~680KB, aceptable para un frontend que no es storage-constrained pero vale la pena tenerlo presente.

**Navegación paginada**: ver "El componente de grid (`PagedFocusGrid`)" más arriba.

**Configuración como overlay, no como pantalla** (`GinnyNavHost.kt`, `MainActivity.kt`): `Routes.SETTINGS`
se eliminó. `settingsOpen: MutableState<Boolean>` se hoistea en `GinnyNavHost` (mismo patrón que el
`menuExpanded` que reemplaza) y Configuración se renderiza como una capa más en el mismo árbol de
composición (no un `Dialog`/Window separado), envolviendo el `NavHost` en `Modifier.blur()` mientras
está abierta — así la pantalla de atrás se ve borrosa de verdad detrás del modal, como pide la
maqueta. `Modifier.blur()` es no-op por debajo de API 31: en esos dispositivos el efecto se degrada
a solo el scrim oscuro, sin blur, pero sigue siendo funcional. El botón físico Menú ahora abre
Configuración directo (antes togglaba el menú ⋮, ver nota en esa sección más arriba).

**Botón Y y `GameOptionsDialog`** (`data/ButtonMapping.kt`, `ui/common/GameOptionsDialog.kt`): nuevo
`LogicalButton.Y` (`KEYCODE_BUTTON_Y`), remapeable como cualquier otro botón en Configuración. En
Sistemas/Estados guardados, Y abre Configuración (igual que Menú); en Juegos, abre
`GameOptionsDialog`, que junta lo que antes eran 3 acciones separadas del menú ⋮ ("Seleccionar
emulador", "Reescanear este sistema", "Eliminar juego" + su `DeleteGameDialog` aparte) en un solo
modal con selector de emulador/core, favorito, reescaneo y un borrado de doble-toque inline
(`deleteArmed`, estado local del diálogo) en vez de un segundo diálogo de confirmación.

**Favoritos** (feature nueva, no existía antes): `SettingsRepository.favoriteGameKeys` (DataStore,
`Set<String>` de claves compuestas `"${systemId}|${romFileName}"`, mismo idioma que
`packageOverrideFor`) — deliberadamente **no** vive en `ScannedGameEntity`/Room, porque
`replaceAll`/`replaceForSystem` hacen DELETE+reinsert completo en cada escaneo y cualquier columna
ahí se perdería en el próximo "Escanear biblioteca". `SystemTileItem` (sellado, solo a nivel UI) le
agrega un tile sintético "Favoritos" a la grilla de Sistemas cuando hay al menos un favorito —
`SystemCatalog` no se toca. `GamesViewModel`/`GamesScreen` generalizan de "siempre un `SystemId`
real" a `GamesScope` (`Single(systemId)` o `Favorites`, cruza todos los sistemas vía el nuevo
`GameIndexRepository.observeAllGames()` filtrado por `favoriteGameKeys`) — como `GameEntry` ya
traía su propio `systemId`, lanzar un juego favorito con el emulador/core de *su* sistema no
requirió tocar `GameLauncher`.

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
