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
│   ├── AssetRepository.kt     # Íconos y logos desde la carpeta de Usuario (Assets/<sistema>/...)
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
    ├── components/            # SnapFocusGrid (grid reutilizable), Tile, TopBar, BottomBar,
    │                          # GlassSurface (glow), AuroraBackground, ButtonBadge
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
- Sin Room/SQLite: la lista de juegos se deriva en vivo escaneando la carpeta de ROMs vía SAF cada
  vez que cambian las URIs configuradas — el catálogo de sistemas es estático (`SystemCatalog`).

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

## Limitaciones conocidas / seguidas para v2

- Selector de tema (Settings) es un placeholder — solo hay un tema claro por ahora.
- Mapeo de botones en Settings no es D-Pad-navegable todavía (usa foco por defecto de Compose,
  funciona por toque siempre; por teclado/D-Pad depende del focus traversal por defecto).
- Save states: solo RetroArch en v1, tal como acota el prompt. Dolphin/PPSSPP/AetherSX2 quedan para
  una fase 2 si se decide investigar sus formatos propios.
- Los logos de sistema por defecto son generados en runtime (mosaico de color + sigla) salvo que se
  reemplacen los PNGs en `app/src/main/assets/system-logos/` antes de compilar.
