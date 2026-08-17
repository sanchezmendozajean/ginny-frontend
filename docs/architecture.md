# Arquitectura — Ginny Frontend

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
    ├── theme/                 # Paleta 1:1 con el mockup, tipografía Baloo2 (variable font, OFL)
    ├── components/            # SnapFocusGrid (grid reutilizable), Tile, TopBar (menú de opciones ⋮
    │                          # con D-Pad, ver sección propia abajo), BottomBar, GlassSurface (glow),
    │                          # AuroraBackground, ButtonBadge
    ├── common/                # Modales compartidos entre pantallas: EmulatorConfigDialog,
    │                          # DeleteGameDialog, ScanProgressDialog
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

## El componente de grid (`SnapFocusGrid`)

Pieza más compleja del proyecto, aislada en `ui/components/SnapFocusGrid.kt` +
`FocusGridMath.kt` (aritmética pura, sin Compose, para poder razonar/testear la navegación por
separado del layout):

- Tamaño de tile = `(alturaDisponible - gap*(filas-1)) / filas`; columnas = las que entren en el
  ancho disponible con ese tamaño de tile — igual que pide el spec.
- Navegación D-Pad: `Modifier.onKeyEvent` interpreta el keycode físico a través del
  `ButtonMapping` remapeable configurado en Settings, no hardcodea `KEYCODE_DPAD_*`.
- **Decisión de diseño no especificada en el prompt**: Izquierda/Derecha *no* hace wrap a la fila
  anterior/siguiente — se frena en los bordes de la fila. Documentado aquí porque el prompt lo dejó
  abierto ("wrap opcional... decidir comportamiento").
- **Snap centrado**: se interpreta como "mantener la fila con foco dentro de la ventana visible de N
  filas, moviendo la ventana lo mínimo posible" (no como centrado matemático estricto, ambiguo
  cuando N es par). Para el swipe táctil, al terminar el scroll (`LazyGridState.isScrollInProgress`
  pasa a `false`) se corrige la posición a la fila completa más cercana.
- B (retroceder) deliberadamente *nunca* es parte de la navegación por foco del grid — es solo
  táctil/botón físico dedicado, manejado a nivel de `MainActivity.dispatchKeyEvent` para funcionar
  sin importar qué composable tenga el foco en ese momento.

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

## Menú de opciones (⋮) y gestión de juegos (agregado 2026-08-17)

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
  al menos un juego válido adentro (`SystemsViewModel.availableSystems`, recalculado cada vez que
  cambian las carpetas configuradas) — evita mostrar sistemas vacíos que solo llevarían a un grid
  en blanco.
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
  un `FocusableTile` con fondo blanco (`GinnyColors.CardBg`), el resultado ya sale redimensionado
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
  chica (`labelSmall`, color `GinnyColors.TextSecondary`) debajo del nombre seleccionado. En
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

`BatteryObserver` oculta el indicador si: no llega ningún `ACTION_BATTERY_CHANGED` en los primeros
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

## Limitaciones conocidas / seguidas para v2

- Selector de tema (Settings) es un placeholder — solo hay un tema claro por ahora.
- Mapeo de botones en Settings no es D-Pad-navegable todavía (usa foco por defecto de Compose,
  funciona por toque siempre; por teclado/D-Pad depende del focus traversal por defecto).
- Save states: solo RetroArch en v1, tal como acota el prompt. Dolphin/PPSSPP/AetherSX2 quedan para
  una fase 2 si se decide investigar sus formatos propios.
- Los logos de sistema por defecto son generados en runtime (mosaico de color + sigla) salvo que se
  reemplacen los PNGs en `app/src/main/assets/system-logos/` antes de compilar.
