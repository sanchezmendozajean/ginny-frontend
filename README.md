# Ginny Frontend

Launcher/frontend para emuladores en Android, con una interfaz inspirada en el HOME Menu de la
Nintendo 3DS y el Wii U Menu (scroll vertical, no horizontal), pensado para consolas Android con
D-Pad/joystick físico. Ver [docs/prompt-frontend-emuladores-3ds.md](docs/prompt-frontend-emuladores-3ds.md)
para la especificación completa y [docs/architecture.md](docs/architecture.md) para las decisiones
de arquitectura.

Herramienta complementaria de escritorio (para completar íconos de juegos en la Carpeta de Usuario):
ver [docs/prompt-asset-manager-nwjs.md](docs/prompt-asset-manager-nwjs.md) — spec/prompt para
construirla en NW.js, todavía no implementada.

## Requisitos

- Android Studio (Iguana o más nuevo recomendado) con JDK 17.
- Android SDK: `compileSdk`/`targetSdk` 34, `minSdk` 29 (Android 10+).
- Un dispositivo o retroconsola Android con al menos uno de los emuladores soportados instalado
  (ver tabla más abajo) — la app no emula nada por sí misma.

## Build

Abrí la carpeta del proyecto en Android Studio y dejá que sincronice Gradle, o por línea de
comandos:

```bash
./gradlew assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/app-debug.apk`. Para instalarlo directo a un
dispositivo conectado por ADB:

```bash
./gradlew installDebug
```

> **Nota sobre este repo**: el código se verificó compilando (`assembleDebug` exitoso) contra una
> configuración de Gradle/AGP/Kotlin más vieja porque el entorno donde se generó este proyecto no
> podía correr JDK 17 (ver docs/architecture.md, sección "Verificación de build"). La primera vez
> que lo abras en Android Studio con JDK 17 debería sincronizar sin problemas, pero si ves algún
> error de versión de Gradle/AGP, es el punto a revisar primero.

## Primera configuración (dentro de la app)

Abrí el panel de Configuración (botón **X** en la barra superior, o botón físico **Menú**) y
configurá, en este orden:

1. **Carpeta de ROMs**: la carpeta base que contiene una subcarpeta por sistema (`nes/`, `snes/`,
   `gba/`, etc. — ver estructura completa abajo). No hace falta que existan todas.
2. **Escanear biblioteca**: botón al lado del selector de Carpeta de ROMs, en la misma pestaña
   Carpetas. La app **no** escanea la carpeta de ROMs sola ni al abrir Sistemas/Juegos — hay que
   presionar este botón para que indexe sistemas y juegos. Puede tardar unos segundos si hay muchos
   archivos; una vez que termina, la navegación lee de ese índice y es instantánea. Volvé a
   presionarlo cada vez que agregues, saques o renombres ROMs/carpetas de sistema.
3. **Carpeta de Usuario**: una carpeta aparte donde la app crea automáticamente
   `Assets/<sistema>/{icons/, system.png}` la primera vez que la elegís. No pisa íconos que ya
   existan si volvés a seleccionar la misma carpeta.
4. **Emulador/core por sistema**: casi todo pasa por RetroArch — elegí entre las 3 variantes reales
   de RetroArch para Android según cuál tengas instalada (ver tabla más abajo) y el core; GameCube
   usa Dolphin, PS2 usa AetherSX2/NetherSX2, PSP usa PPSSPP, y Dreamcast te deja elegir entre
   Redream o RetroArch.
5. **Filas de la grilla** (1 a 4), volúmenes de música/sonidos de interfaz y la pista de música de
   fondo, a gusto.

El botón **Y Recargar** en la barra inferior de Sistemas/Juegos solo relee ese índice ya escaneado
(instantáneo) — no vuelve a tocar el disco. Para que aparezcan juegos nuevos hace falta el botón
"Escanear biblioteca" de Configuración → Carpetas.

## Estructura de carpetas esperada

**Carpeta de ROMs** — una subcarpeta por sistema, con los archivos de ROM directamente adentro
(sin subniveles), filtrados por extensión:

| Sistema | Carpeta | Extensiones |
|---|---|---|
| NES | `nes/` | `.nes` |
| SNES | `snes/` | `.sfc`, `.smc` |
| Game Boy | `gb/` | `.gb` |
| Game Boy Color | `gbc/` | `.gbc` |
| Game Boy Advance | `gba/` | `.gba` |
| PlayStation | `ps/` | `.iso`, `.bin`, `.cue`, `.chd`, `.pbp` |
| Genesis / Mega Drive | `genesis/` | `.md`, `.gen`, `.bin` |
| Master System | `mastersystem/` | `.sms` |
| Neo Geo | `neogeo/` | `.zip` |
| FinalBurn Neo | `fbneo/` | `.zip` |
| MAME | `mame/` | `.zip` |
| GameCube | `gc/` | `.iso`, `.gcm`, `.rvz` |
| PlayStation 2 | `ps2/` | `.iso`, `.chd`, `.bin` |
| PSP | `psp/` | `.iso`, `.cso`, `.pbp` |
| Dreamcast | `dreamcast/` | `.chd`, `.gdi`, `.cdi` |
| Nintendo Switch | `switch/` | `.nsp`, `.xci`, `.nsz`, `.xcz` (ver más abajo, organización distinta) |
| PICO-8 | `pico8/` | `.p8`, `.p8.png` (ver más abajo, ícono embebido) |

Esta tabla es una propuesta inicial (marcada como tal en el documento de especificación, sección
"Cosas a definir" #3) — si tus ROMs usan otras extensiones, avisame y ajusto
`SystemCatalog.kt`.

**Switch es la excepción a "un archivo = un juego".** Cada juego es una subcarpeta dentro de
`switch/`, con el juego base y sus updates/DLC juntos adentro (la misma convención que reconocen
emuladores de escritorio como Eden):

```
switch/
└── The Legend of Zelda TOTK/
    ├── TOTK [BASE].nsp
    ├── TOTK [UPD] [v65536].nsp
    └── TOTK [DLC].nsp
```

La app escanea cada subcarpeta como un solo juego (el nombre de la carpeta es lo que se muestra) y
elige automáticamente el archivo base para lanzar: descarta los que tengan "update", "upd", "dlc" o
"patch" en el nombre, y si eso no alcanza para desambiguar, se queda con el archivo más grande.

**Solo se listan los sistemas con juegos.** Si `<carpeta de ROMs>/<sistema>/` no existe o no tiene
ningún archivo (o carpeta, para Switch) válido, ese sistema simplemente no aparece en la pantalla de
sistemas — no hace falta ocultarlo a mano.

**PICO-8 usa el propio cart como ícono cuando no le pusiste uno propio.** El formato `.p8.png` es un
PNG real (normalmente un screenshot/label del juego) con el código y los datos del cart
esteganografiados en los píxeles — la app simplemente muestra esa imagen tal cual. Como es *portrait*
y no cuadrada, se redimensiona para entrar en la tarjeta cuadrada sin recortarse, con el sobrante en
blanco (mismo comportamiento que cualquier ícono no cuadrado en la grilla). Los carts `.p8` a secas
(texto plano, sin imagen) no tienen esta opción y muestran el placeholder de siempre hasta que le
pongas un ícono en `Assets/pico8/icons/`.

**Carpeta de Usuario** (se genera sola al configurarla, o al volver a seleccionarla si agregamos
algo nuevo a la estructura después):

```
[Carpeta de Usuario]/
├── Assets/
│   ├── nes/{icons/, system.png}
│   ├── snes/{icons/, system.png}
│   └── ... (una por sistema, incluyendo switch/)
└── Interface/
    └── Background Music/
```

- Ícono de una ROM: `Assets/<sistema>/icons/<NombreDelArchivoDeROM>.png` (con extensión incluida,
  ej. `BomberMan.nes.png`). Para Switch, el nombre es el de la *carpeta* del juego en vez de un
  archivo, sin extensión (ej. `The Legend of Zelda TOTK.png`). Si falta, se muestra un placeholder.
- `system.png` es el logo del sistema en la pantalla de sistemas. Se genera un placeholder simple
  automáticamente si no le ponés uno propio — ver siguiente sección.
- `Interface/Background Music/`: dejá ahí tus propios archivos de audio (`.mp3`, `.wav`, `.ogg`,
  `.m4a`, `.flac`) para que aparezcan como opción de pista de fondo en Configuración, junto a las
  dos que vienen incluidas — ver sección "Sonidos".

> Si ya tenías una Carpeta de Usuario configurada antes de la versión con `Interface/Background
> Music/`, volvé a seleccionarla una vez en Configuración → Carpetas para que se cree la carpeta
> nueva (o creala vos mismo a mano, el nombre tiene que ser exactamente ese).

## Reemplazar los logos de sistema por defecto

Dejá los PNG reales en `app/src/main/assets/system-logos/<carpeta>.png` (mismo nombre que la
carpeta del sistema, ej. `nes.png`, `snes.png`) **antes de compilar** — se empaquetan en la APK y se
copian a la carpeta de Usuario la primera vez. Sin esto, la app genera un mosaico de color con la
sigla del sistema para no dejarlo sin logo. Nunca uses logos/tipografías propietarias de
Nintendo/Sega/Sony — el spec pide una identidad visual original.

## Emuladores soportados

| Sistema | Emulador | Package |
|---|---|---|
| NES/SNES/GB/GBC/GBA/PS1/Genesis/Master System/Neo Geo/FBNeo/MAME/Dreamcast/PICO-8 | RetroArch (3 variantes, elegibles en Settings) | `com.retroarch` (universal), `com.retroarch.aarch64` (64-bit), `com.retroarch.ra32` (32-bit) |
| GameCube | Dolphin | `org.dolphinemu.dolphinemu` |
| PlayStation 2 | AetherSX2 o NetherSX2 (fork activo — AetherSX2 fue descontinuado en 2023/2024) | `xyz.aethersx2.android` (mismo package para ambos — no podés tener los dos instalados a la vez) |
| PSP | PPSSPP | `org.ppsspp.ppsspp` |
| Dreamcast | Redream (por defecto) o RetroArch, elegible en Settings | `io.recompiled.redream` |
| Nintendo Switch | Eden | `dev.eden.eden_emulator` |

**RetroArch para Android se distribuye en 3 builds instalables por separado** (universal, 64-bit y
32-bit — ver `buildbot.libretro.com`, o `pkg/android/phoenix/build.gradle` en el repo oficial de
RetroArch para el detalle de packages). El selector de "Emulador" en Settings te deja elegir cuál de
las tres tenés instalada, por sistema — si tu juego dice "RetroArch no está instalado" fijate que la
variante elegida coincida con la que realmente instalaste (por ejemplo, si instalaste la versión de
64 bits, elegí "RetroArch (64-bit)").

El "Package override" en Settings existe para PS2 y Switch (`SystemDefinition.supportsPackageOverride`)
— por si tu instalación en particular usa un package distinto al default, aunque para ambos casos ya
hay un valor confirmado cargado (AetherSX2/NetherSX2 comparten `xyz.aethersx2.android`; Eden usa
`dev.eden.eden_emulator`).

**Sobre packages "no detectados" (Android 11+)**: la app declara en `AndroidManifest.xml`
(`<queries>`) los packages de todos los emuladores soportados, requisito de Android 11+ para poder
ver si otra app está instalada. Si usás un "Package override" con un package que no está en esa
lista (típicamente un fork de PS2/Switch distinto a los ya confirmados), Android puede seguir
reportándolo como "no instalado" aunque sí lo esté — en ese caso hay que agregar ese package al
`<queries>` del manifiesto y recompilar.

**AetherSX2 / NetherSX2 comparten el mismo package** (`xyz.aethersx2.android` — confirmado con
`aapt dump badging` sobre un APK real de "NetherSX2 Classic"). Esto significa que solo uno de los
dos puede estar instalado a la vez: para pasar de uno al otro hay que desinstalar el que ya tengas
(Android rechaza la instalación con "conflicts with an existing package" si no). El selector de
emulador en Settings ya trae ese package cargado por defecto para ambas opciones; el campo de
override solo hace falta si tu build en particular usa un package distinto.

**MAME**: el core por defecto (`mame2010_libretro_android.so`) es sensible a la versión del romset
(funciona con romsets de MAME ~0.139). ROMs de versiones más nuevas o viejas de MAME probablemente
den error de "CRC mismatch" — es una limitación del romset, no de la app.

**NES**: además de FCEUmm (por defecto), QuickNES y Nestopia UE, también está disponible el core
Mesen, elegible en Settings.

**PICO-8**: usa el core **fake-08** (`fake08_libretro_android.so`) — una reimplementación
open-source compatible con PICO-8, no el runtime oficial de Lexaloffle (que no tiene build para
Android). Soporta tanto los carts de texto plano (`.p8`) como los "visuales" (`.p8.png`, un PNG real
con el código embebido en los píxeles — ver la sección de íconos más arriba).

## Save states

Solo soportado para sistemas manejados por RetroArch en v1. Necesitás:

1. Activar `savestate_thumbnail_enable` en la configuración de RetroArch (para que genere las
   miniaturas PNG junto a cada save state).
2. Configurar en Settings, sección "Carpetas", la ruta donde RetroArch guarda esos estados.

Si un juego no tiene save states detectados, se lanza directo (sin mostrar la pantalla intermedia),
tal como pide el spec.

## Sonidos

`app/src/main/res/raw/{sfx_focus,sfx_activate,music_track_1,music_track_2}.mp3` son los efectos de
foco/selección y las dos pistas de música de fondo embebidas. Para reemplazarlos por audio propio,
mantené los mismos nombres de archivo y recompilá.

La pista de fondo es elegible en Configuración → Interfaz → Audio, entre las dos embebidas y
cualquier archivo de audio que dejes en `Interface/Background Music/` dentro de la Carpeta de
Usuario (ver sección "Estructura de carpetas esperada").

## Licencias de terceros

La tipografía Baloo 2 (`app/src/main/res/font/baloo2.ttf`) está bajo SIL Open Font License — ver
[licenses/Baloo2-OFL.txt](licenses/Baloo2-OFL.txt).
