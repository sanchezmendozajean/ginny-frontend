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

## Generar tu propio APK

### Opción rápida: APK de debug

Sirve para instalar y probar en tu dispositivo/retroconsola sin configurar nada extra — Android
Studio (o Gradle) lo firma solo con un keystore de debug generado automáticamente.

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

### Opción recomendada para uso diario: APK de release, firmado con tu propia key

Un APK de debug funciona, pero su keystore es genérico (el mismo para cualquiera que compile el
proyecto) y puede expirar. Para un APK "tuyo" que reinstalás/actualizás con confianza en tu
retroconsola a largo plazo, generá tu propia key de firma una sola vez:

1. **Generá el keystore** (pide un JDK con `keytool` — el que trae Android Studio alcanza; en
   Windows usá el `keytool.exe` que viene con Android Studio si no tenés uno en el `PATH`):

   ```bash
   keytool -genkeypair -v -keystore ginny-release.jks -alias ginny -keyalg RSA -keysize 2048 -validity 10000
   ```

   Te va a pedir una contraseña para el keystore y otra (o la misma) para la key — anotalas, las
   vas a necesitar cada vez que compiles un release y para cualquier actualización futura sobre el
   mismo APK instalado (Android rechaza instalar un update firmado con una key distinta). Guardá
   `ginny-release.jks` en un lugar seguro fuera del control de versiones — si lo perdés, no vas a
   poder actualizar ese mismo APK nunca más, solo desinstalar y reinstalar de cero.

2. **Creá `keystore.properties`** en la raíz del proyecto (`Ginny Frontend/keystore.properties`,
   al lado de `settings.gradle.kts`) — ya está en `.gitignore`, nunca se commitea:

   ```properties
   storeFile=../ginny-release.jks
   storePassword=TU_CONTRASEÑA_DE_KEYSTORE
   keyAlias=ginny
   keyPassword=TU_CONTRASEÑA_DE_KEY
   ```

   `storeFile` es relativo a la carpeta `app/`, así que si dejaste el `.jks` en la raíz del
   proyecto (un nivel arriba de `app/`) el valor `../ginny-release.jks` de arriba es correcto;
   ajustalo si lo pusiste en otro lado. Sin este archivo, `assembleRelease` sigue compilando igual
   que antes pero el APK queda **sin firmar** (no instalable directamente).

3. **Compilá el release**:

   > **Si corrés `./gradlew` desde una terminal suelta (PowerShell, cmd) y no desde Android
   > Studio**, puede tirarte `ERROR: JAVA_HOME is not set and no 'java' command could be found in
   > your PATH`. Android Studio trae su propio Java incluido (no hace falta instalar nada aparte),
   > pero una terminal externa no sabe dónde está a menos que se lo digas. Solución permanente
   > (una sola vez, para tu usuario de Windows — sobrevive a reinicios y aplica a toda ventana
   > *nueva* que abras de ahí en adelante):
   >
   > ```powershell
   > setx JAVA_HOME "C:\Program Files\Android\Android Studio\jbr"
   > ```
   >
   > Después de correrlo, **cerrá esa ventana y abrí una nueva** (`setx` no actualiza la ventana
   > donde lo ejecutaste, solo las que abras después) y confirmá con `echo $env:JAVA_HOME`. La
   > alternativa es abrir la Terminal integrada de Android Studio (View → Tool Windows → Terminal),
   > que ya viene configurada sola sin este paso.

   ```bash
   ./gradlew assembleRelease
   ```

   El APK firmado queda en `app/build/outputs/apk/release/GinnyFrontend-<versionName>.apk` (por
   ejemplo `GinnyFrontend-0.2.0.apk` — el nombre se arma solo a partir de `versionName`, ver punto
   4). Instalalo con:

   ```bash
   adb install -r app/build/outputs/apk/release/GinnyFrontend-0.2.0.apk
   ```

   (`-r` reinstala/actualiza si ya tenías una versión anterior instalada, siempre que esté firmada
   con la misma key y el `versionCode` nuevo sea mayor o igual al instalado — ver punto 4.)

4. **Actualizá la versión antes de cada release nuevo.** Vive en
   [app/build.gradle.kts](app/build.gradle.kts), sección `defaultConfig`:

   ```kotlin
   defaultConfig {
       versionCode = 1
       versionName = "0.2.0"
   }
   ```

   - **`versionCode`**: un entero que tenés que subir en cada release (`1` → `2` → `3`...). No es
     cosmético — Android lo usa para decidir si un APK nuevo puede instalarse "encima" del que ya
     tenés. Si compilás un release con el mismo `versionCode` (o uno menor) que el ya instalado,
     `adb install -r` falla con `INSTALL_FAILED_VERSION_DOWNGRADE` y hay que desinstalar primero.
   - **`versionName`**: el string libre que ves en la app y que también nombra el APK (punto 3) —
     usá lo que quieras, por ejemplo `"0.2.0"` para el próximo cambio.

   Subí ambos valores, guardá el archivo, y recién ahí corré `./gradlew assembleRelease` — el APK
   nuevo sale nombrado con el `versionName` actualizado automáticamente, sin tocar nada más.

### Opcional: credenciales de desarrollador de ScreenScraper

Para que el scrapper pueda usar ScreenScraper, el APK necesita las credenciales de **desarrollador**
de Ginny (`devid` / `devpassword`; identifican al programa, no a quien lo usa — cada usuario escribe
su propia cuenta en Configuración > Scrapper). Se piden al equipo de ScreenScraper
(<https://www.screenscraper.fr/forumsujets.php?frub=12>) y se incrustan al compilar:

1. Copiá `scrapper.properties.example` como `scrapper.properties` (misma carpeta, al lado de
   `settings.gradle.kts`) — está en `.gitignore`, nunca se commitea.
2. Completá `screenscraper.devId` y `screenscraper.devPassword`.

Sin ese archivo el proyecto compila igual, pero ScreenScraper queda desactivado en ese APK. Ojo:
al quedar dentro del APK, cualquiera que lo reciba puede extraerlas (es lo habitual con esta API);
si algún día se distribuye el APK, ScreenScraper puede revocarlas y emitir otras.

## Primera configuración (dentro de la app)

Abrí el menú (botón **⋮** en la barra superior, o botón físico **Menú**) y elegí **Configuración**.
Configurá, en este orden:

1. **Carpeta de ROMs**: la carpeta base que contiene una subcarpeta por sistema (`nes/`, `snes/`,
   `gba/`, etc. — ver estructura completa abajo). No hace falta que existan todas. La app pide
   permiso de **lectura y escritura** sobre esta carpeta (la escritura la usa "Eliminar juego", ver
   más abajo) — si la habías configurado antes de esa función, volvé a seleccionarla una vez para
   que se actualice el permiso, o "Eliminar juego" va a fallar con "No se pudo eliminar".
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

## Menú de opciones (⋮)

Se abre tocando el ícono de tres puntos de la barra superior, o con el botón físico **Menú** (un
segundo toque/press lo cierra). Una vez abierto es navegable con la cruceta: Arriba/Abajo mueve el
resaltado entre las opciones, **A** confirma la resaltada, **B** lo cierra sin retroceder de
pantalla.

En Sistemas solo tiene **Configuración** y **Salir de Ginny**. Dentro del
listado de juegos de un sistema aparecen además, en este orden:

1. **Seleccionar emulador** *(con un juego seleccionado)* — abre el mismo modal para elegir
   emulador/core que aparece cuando falla un lanzamiento por emulador no instalado, pero para
   cambiarlo de una vez sin tener que ir a Configuración ni esperar a que falle un lanzamiento.
2. **Reescanear este sistema** — vuelve a escanear vía SAF solo el sistema actual y actualiza su
   parte de la biblioteca, sin tocar el resto de sistemas ya indexados. Más rápido que "Escanear
   biblioteca" completo cuando solo cambiaron ROMs de un sistema puntual.
3. **Eliminar juego** *(con un juego seleccionado)* — pide confirmación y, al aceptar, borra
   físicamente el juego del dispositivo (la carpeta completa con base+updates+DLC en Switch), su
   ícono en `Assets/<sistema>/icons/` si tenía uno, y su entrada en el índice — la lista se
   actualiza sola, sin reescanear. **Es irreversible: no se hace ningún backup.** Necesita permiso
   de escritura sobre la Carpeta de ROMs (ver paso 1 de "Primera configuración" más arriba).

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
| GameCube | `gc/` | `.iso`, `.gcm`, `.rvz`, `.gcz` |
| PlayStation 2 | `ps2/` | `.iso`, `.chd`, `.bin` |
| PSP | `psp/` | `.iso`, `.cso`, `.pbp` |
| Dreamcast | `dreamcast/` | `.chd`, `.gdi`, `.cdi` |
| Nintendo Switch | `switch/` | `.nsp`, `.xci`, `.nsz`, `.xcz` (ver más abajo, organización distinta) |
| PICO-8 | `pico8/` | `.p8`, `.p8.png` (ver más abajo, ícono embebido) |
| Nintendo DS | `nds/` | `.nds` |
| Nintendo 3DS | `n3ds/` | `.3ds`, `.cci`, `.cxi` |

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
| Nintendo DS | melonDS DS | `me.magnum.melonds` |
| Nintendo 3DS | Azahar | `io.github.lime3ds.android` (Azahar absorbió el proyecto Lime3DS y mantuvo su applicationId de Android; AzaharPlus, en su variante "Replace", reusa el mismo id a propósito) |

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

**GameCube/Wii (Dolphin) y Nintendo DS (melonDS DS)** no aceptan el mecanismo genérico de "mandar
el ROM por `ACTION_VIEW`" que usa el resto de emuladores standalone (verificado con
`adb shell dumpsys package` contra instalaciones reales: ninguno de los dos registra un
intent-filter de apertura de archivos). Cada uno usa su propio contrato de lanzamiento explícito
documentado por su proyecto (`AutoStartFile` para Dolphin, `Intent.data` a `EmulatorActivity` para
melonDS DS) — ver [DolphinLauncher.kt](app/src/main/java/com/ginny/frontend/launch/DolphinLauncher.kt)
y [MelonDSLauncher.kt](app/src/main/java/com/ginny/frontend/launch/MelonDSLauncher.kt). Si en el
futuro una versión nueva de cualquiera de los dos cambia ese contrato, el síntoma **no** es el
diálogo de "emulador no instalado" — es que no pasa nada visible al tocar el juego (v1 ignora
`LaunchResult.Failed` en silencio, ver `GamesScreen.kt`), aunque el emulador esté instalado y
detectado correctamente.

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

Ginny **no tiene pantalla de estados guardados** (eliminada el 2026-10-13: demasiada complejidad para quien
solo quiere encender la consola y jugar). "Jugar" siempre lanza el juego directo; cargar o guardar estados
es cosa del propio emulador (en RetroArch, sus atajos y el autoguardado). Ya no hay ajuste de carpeta de
estados ni se envía `STATE_SLOT`.

## Sonidos

`app/src/main/res/raw/` contiene los efectos (`sfx_focus`, `sfx_enter_system` y `sfx_open_modal`, `sfx_launch`) y la pista de música de fondo (`music_track_1`).
Para reemplazarlos por audio propio,
mantené los mismos nombres de archivo y recompilá.

La pista de fondo es elegible en Configuración → Interfaz → Audio, entre la embebida y
cualquier archivo de audio que dejes en `Interface/Background Music/` dentro de la Carpeta de
Usuario (ver sección "Estructura de carpetas esperada").

## Licencias de terceros

La tipografía M PLUS Rounded 1c (`app/src/main/res/font/mplus_rounded1c_*.ttf`) está bajo SIL
Open Font License — ver [licenses/MPLUSRounded1c-OFL.txt](licenses/MPLUSRounded1c-OFL.txt).

## Créditos

También se muestran dentro de la app, en Configuración → Créditos.

- **Música**: "Bossa Nova" de Joth ([OpenGameArt](https://opengameart.org), CC0).
- **Efectos de sonido**: "Interface Sounds" de [Kenney](https://kenney.nl) (CC0, ver [licenses/Kenney-InterfaceSounds-CC0.txt](licenses/Kenney-InterfaceSounds-CC0.txt)).
- **Metadatos, wheel, cajas y video**: [ScreenScraper](https://www.screenscraper.fr).
- **Íconos**: [SteamGridDB](https://www.steamgriddb.com).
- **Bibliotecas** (Apache 2.0): Jetpack Compose / AndroidX, Coil, OkHttp y Haze.

## Licencia

El código de Ginny Frontend se distribuye bajo la [licencia MIT](LICENSE). Esa licencia cubre solo el
código: la música, los efectos, la tipografía y el arte descargado de ScreenScraper y SteamGridDB
mantienen las suyas (ver Créditos).
