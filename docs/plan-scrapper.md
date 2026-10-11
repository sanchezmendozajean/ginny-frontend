# Plan: Scrapper integrado en Ginny Frontend

Creado: 2026-10-07. Estado: **aprobado; en implementación** (ver "Registro de avances").

## Objetivo

Que Ginny Frontend sea autosuficiente: que obtenga por sí misma los metadatos y el arte de cada
juego, sin depender del Assets Manager (que se actualizará después para el mismo fin).

| Proveedor | Qué se obtiene |
|---|---|
| **SteamGridDB** | Ícono cuadrado del juego (**no** el fondo, por ahora) |
| **ScreenScraper** | Metadatos (descripción, sistema, fecha, desarrollador…), video, wheel, box front, box back, box spine |

## Alcance de esta fase

**Entra:** credenciales en Configuración (sección "Scrapper", agrupadas por proveedor) y la
**mecánica** de scrap (clientes, almacenamiento, orquestación). Se verifica solo que compila
(`gradlew compileDebugKotlin` por PowerShell, ver `architecture.md`); **no se prueba contra las APIs**.

**No entra (se decide después):** dónde se muestra cada arte, qué se hace cuando falta, el fondo
(heroes) de SteamGridDB, el Assets Manager, leer los metadatos en la UI.

## Hallazgos del código que condicionan el diseño

1. **`ginny.cfg` no puede llevar credenciales.** `GinnyConfigRepository` espeja casi toda la
   configuración a un archivo de texto plano en la Carpeta de Usuario, que además se sincroniza entre
   dispositivos (existe un `sync.ffs_db` de FreeFileSync en `Ginny USER/`). Las credenciales van en
   un DataStore **aparte** (`ginny_scrapper`) que ese repositorio no lee.
2. **`allowBackup="true"`** en el manifest: ese DataStore debe excluirse de las copias de seguridad
   (`dataExtractionRules` / `fullBackupContent`).
3. **No hay permiso `INTERNET`** en el manifest ni cliente HTTP declarado. Coil 2.6 ya trae OkHttp de
   forma transitiva; se declara explícitamente. El JSON se parsea con `org.json` (ya usado).
4. **Convención de Assets existente:** `Assets/<sistema>/icons/<romFileName>.png`, donde
   `romFileName` incluye la extensión (`Mario.nes.png`). El ícono de SteamGridDB va ahí: la UI actual
   (`Element`, `AssetRepository.loadIconIndex`) lo usa sin cambios.
5. **El ícono se cachea en Room** (`ScannedGameEntity.iconUri`) en cada escaneo. Un ícono descargado
   no aparece hasta reescanear, salvo que se actualice esa fila (paso 6).
6. **Room usa `fallbackToDestructiveMigration`:** cambiar el esquema borra la caché de la biblioteca
   (obliga a reescanear). Por eso esta fase **no** toca Room salvo una consulta `UPDATE` nueva.
7. **ScreenScraper pone usuario y contraseña en la query string** de cada petición. Nunca se loguean
   URLs ni se incluyen en mensajes de error (se limpian antes de mostrarlos).

## Decisiones de diseño

### Orden por juego: ScreenScraper primero, SteamGridDB después
ScreenScraper identifica el juego (por hash o nombre de ROM) y devuelve el **nombre canónico**;
con ese nombre se busca en SteamGridDB, que no tiene filtro por plataforma y se equivoca fácil con
nombres de archivo crudos (`3countb.zip`). Si falta una credencial, el otro proveedor sigue
funcionando (SteamGridDB cae a `displayName`).

### Dónde se guarda (dentro de `Assets/<sistema>/`, mismo contrato que el Assets Manager)

| Carpeta | Contenido | Origen |
|---|---|---|
| `icons/` | `<romFileName>.png` — ícono cuadrado | SteamGridDB |
| `wheel/` | `<romFileName>.<ext>` | ScreenScraper `wheel` |
| `box-front/` | `<romFileName>.<ext>` | ScreenScraper `box-2D` |
| `box-back/` | `<romFileName>.<ext>` | ScreenScraper `box-2D-back` |
| `box-spine/` | `<romFileName>.<ext>` | ScreenScraper `box-2D-side` |
| `video/` | `<romFileName>.mp4` | ScreenScraper `video-normalized` (respaldo: `video`) |
| `metadata/` | `<romFileName>.json` | ScreenScraper (y estado de búsqueda) |

- Un archivo JSON **por juego** (no uno por sistema): evita leer-modificar-escribir un archivo
  grande por SAF y no hay carreras si dos juegos terminan a la vez.
- Los nombres de media de ScreenScraper son de memoria: **verificarlos contra
  `https://api.screenscraper.fr/webapi2.php`** al implementar.
- El ícono se convierte a PNG (convención existente). El resto **conserva su formato original**
  (convertir cajas JPEG a PNG las infla 3-5×).
- **Nunca se sobrescribe** un archivo existente (puede ser arte puesto a mano o por el Assets
  Manager), salvo "forzar".

### Contenido de `metadata/<romFileName>.json`
```
{
  "status": "found" | "not_found",
  "scrapedAt": <epoch ms>,
  "matchedBy": "hash" | "name" | "search",      // confianza de la coincidencia
  "screenscraper": { "gameId": 123, "systemId": 3 },
  "steamgriddb":   { "gameId": 456 },
  "name": "...",
  "synopsis": "...",                             // idioma elegido (ver Región/idioma)
  "system": "NES",                               // SystemId propio
  "releaseDate": "1990-05-12",                   // la que ScreenScraper dé; año derivable
  "developer": "...", "publisher": "...",
  "genres": [...], "players": "1-2", "rating": 0.0-1.0,
  "regionUsed": "wor"
}
```
Los campos extra (géneros, jugadores, nota) cuestan nada guardar y se piden con frecuencia después.
`not_found` es una **caché negativa**: evita volver a consultar en cada corrida un juego que no existe.

### Identificación del juego en ScreenScraper
- Parámetros: `systemeid` + `romnom` + `romtaille` + hashes (`crc`/`md5`/`sha1`).
- **Hashes solo si el archivo es chico** (tope configurable, ej. 64 MB): calcularlos por SAF en
  ISOs de GC/Wii/PS2/Wii U/Switch es inviable. Por encima del tope: solo `romnom` + `romtaille`.
- **No hashear `.zip` de arcade** (MAME/FBNeo/NeoGeo/Naomi/Atomiswave): el hash del zip no es el de
  la ROM; ahí se identifica por `romnom`.
- `FOLDER_PER_GAME` (Switch): `romnom` = nombre de la carpeta.
- Si `jeuInfos` no da resultado: `jeuRecherche` con el nombre; se marca `matchedBy: "search"`.
- Mapa `SystemId → systemeid` de ScreenScraper: campo nuevo en `SystemDefinition`
  (`screenScraperSystemId: Int?`). Se llena contra `systemesListe.php`, no de memoria.

### Elección de media: región e idioma
ScreenScraper devuelve **varias imágenes por tipo, una por región** (en tus capturas: Wheel =
Mundo, Box front = Japón). Hace falta una política de prioridad: por defecto
`wor → eu → us → jp → (la primera)`; para la sinopsis, idioma `es → en → (el primero)`. Configurable
más adelante; por ahora una constante.

### SteamGridDB: ¿qué es "ícono cuadrado"?
Hay dos cosas: **grids** (hay variantes 512×512 y 1024×1024, filtrables con `dimensions`) e **icons**
(logos pequeños, a menudo transparentes). Propuesta: **grids cuadrados 512×512** primero (mejor
para un tile), y `icons` solo como respaldo. Filtros fijos: `nsfw=false`, `humor=false`, sin
animados. Se toma el mejor puntuado.

### Límites y errores
- ScreenScraper: hilos por cuenta (1 por defecto) → **peticiones secuenciales**; `429` = límite por
  minuto (espera y reintenta); `430` = cupo diario agotado (**cortar el lote** y avisar, no seguir
  gastando); `431` = demasiados sin coincidencia; `403` = credenciales rechazadas.
- SteamGridDB: secuencial con pausa corta; `401` = clave inválida; `429` = espera.
- Resultado tipado (`sealed`): `Ok / NotFound / AuthFailed / QuotaExhausted / RateLimited / Network`.
  Las pantallas decidirán después cómo mostrarlo.

### Dónde corre
En el `applicationScope` del `AppContainer` (sobrevive a salir de Configuración), con cancelación.
Al ser **idempotente** (salta lo que ya existe), si el sistema mata el proceso se retoma sin pérdida.
Un servicio en primer plano / WorkManager queda fuera de esta fase (se necesitará si el lote debe
seguir con la app en segundo plano; hoy `MainActivity` mantiene la pantalla encendida).

## Prueba de reconocimiento de las APIs (2026-10-08)

Peticiones sueltas con `curl` (no es código de la app), con las credenciales del usuario:

- **ScreenScraper sin ID de desarrollador: no funciona.** `jeuInfos.php` responde HTTP 403,
  "Vérifier vos identifiants développeur". `ssuserInfos.php` responde 403 "Vérifier les identifiants
  utilisateurs" (no permite concluir si la cuenta es válida: probablemente también exige el par de
  desarrollador). **Hay que solicitar `devid`/`devpassword`.** Según la documentación oficial
  (`api.screenscraper.fr/webapi2.php`) se piden presentando el software en el foro:
  `https://www.screenscraper.fr/forumsujets.php?frub=12&numpage=0`. La API es solo para aplicaciones
  "completamente gratuitas y distribuidas" (o con autorización previa); `softname` es obligatorio;
  los hilos simultáneos y el cupo diario dependen del nivel del usuario (contribuciones a la base
  o apoyo económico); 430 = cupo del día agotado.
- **SteamGridDB: funciona.** `search/autocomplete/{nombre}` devuelve `id`, `name`, `verified`,
  `types` y `release_date` (epoch en segundos; sirve de respaldo del año). `grids/game/{id}` con
  `dimensions=512x512` devolvió 4 resultados y con `1024x1024`, 19 (el filtro funciona; `mime`
  png o jpeg). Cada grid trae `score`, `style`, `language`, `width`, `height`, `url`, `thumb`.
  - Hay más 1024 que 512: si solo existe 1024, se **reduce** a 512 (reducir sí está permitido; lo
    que no se hace es agrandar).
  - `language` permite preferir `es`/`en` sobre `ja` al elegir entre varios.
  - Todos los resultados tenían `score: 0` en esta muestra: no ordenar solo por puntuación.

### Decisiones que salieron de las pruebas (2026-10-10)

- **Año de publicación = la fecha más antigua de todas las regiones** (Super Mario Bros.: 1985, aunque
  en Europa salió en 1987). La política de región solo rige para las imágenes. El JSON de metadatos
  guarda además todas las fechas por región.
- **Caja:** se elige la **primera región (wor → eu → us → jp) que tenga las tres caras**; si ninguna las
  tiene, se parte de la que tenga frente y se completa con las demás. Así frente, trasera y lomo salen
  de la misma edición siempre que exista una completa (en la muestra real, `wor` solo tenía frente y
  `eu` las tres: gana `eu`).
- **El respaldo por búsqueda cuesta una sola petición:** `jeuRecherche.php` devuelve hasta 3 juegos con
  todos sus datos y medias, no hace falta una segunda consulta por `gameid`.
- **Juego inexistente = HTTP 404** con "Erreur : Rom/Iso/Dossier non trouvée !". Los tres valores de
  `romtype` son `rom`, `iso` y `dossier`.

### Prueba con credenciales de desarrollador (2026-10-10)

El equipo de ScreenScraper aprobó la solicitud y creó la cuenta de desarrollador. Con ella, **la
API funciona**: `ssuserInfos.php` y `jeuInfos.php` (HTTP 200, `success: true`). Hallazgos:

- **Cuenta del usuario:** `maxthreads` = **1**, `maxrequestspermin` = 3072, `maxrequestsperday` =
  **50 000**, `maxrequestskoperday` = 5 000 (no encontrados), `maxdownloadspeed` = 128.
  Un solo hilo → peticiones **estrictamente secuenciales**. El cupo diario sobra.
- **Latencia: ~4-5 s por `jeuInfos`** (tiempo de servidor, no de ancho de banda). Una biblioteca de
  500 juegos son ~40 min solo en metadatos, más las descargas de media. Refuerza el diseño
  reanudable y la barra de progreso con estimación.
- **Peso de la respuesta:** muy variable. Un juego famoso (Super Mario Bros., NES) devolvió
  **1,26 MB** de JSON (cientos de hacks y traducciones en `roms`); uno común (Bomberman II), 67 KB.
  Hay que parsear en streaming o aceptar picos de ~1-2 MB por respuesta (sin guardarla entera).
- **Identificación por nombre funciona:** `systemeid=3` + `romtype=rom` + `romnom=<archivo>` bastó
  (sin hash). `systeme.id` 3 = NES confirmado.
- **Campos del juego** (`response.jeu`): `id`, `romid`, `noms[{region,text}]`,
  `synopsis[{langue,text}]` (en, fr, de, **es**, it, pt), `dates[{region,text}]` (por región:
  `us`/`jp`/`eu`…), `developpeur{id,text}`, `editeur{id,text}`, `joueurs{text}`, `note{text}`
  (sobre 20), `genres[{id, noms[{langue,text}]}]`, `medias[...]`.
- **Medias** (`jeu.medias[]`, cada una con `type`, `region`, `format`, `size`, `url`, `crc`/`md5`/
  `sha1`): nombres **confirmados** `wheel`, `box-2D`, `box-2D-back`, `box-2D-side`,
  `video-normalized` y `video` (ambos mp4; 1,6 MB y 2,3 MB en el ejemplo). `video`/`video-normalized`
  vienen **sin región**. Hay entradas **repetidas** para una misma región (tomar la primera).
  Las cajas 2D pesan 170-540 KB (frente) y 30-680 KB (trasera/lomo).
- **Dos tipos de media nuevos y relevantes para la caja 3D:** `box-texture` (textura de la caja
  completa en una sola imagen, ~0,5-1,2 MB) y `box-3D` (render ya en 3D, no sirve para rotar).
  `box-texture` podría ahorrar armar la caja con tres imágenes; **decidir en la fase visual** si se
  descarga además (o en lugar) de las tres caras sueltas.
- **La `url` de cada media lleva las credenciales incrustadas** (`devid`, `devpassword`, `ssid`,
  `sspassword`). Confirma la regla de no loguear URLs ni mostrarlas en errores.
- **Regiones:** en el ejemplo `wor` no apareció (wheel: eu, fr, us); frente de caja: de, eu, …; la
  región `eu` tenía frente, trasera y lomo a la vez, lo que cuadra con la regla de caja consistente.

## Pasos de implementación (cada uno es un commit independiente)

### 0. Base de red
- `AndroidManifest.xml`: permiso `INTERNET` (y `ACCESS_NETWORK_STATE` si se quiere avisar sin red).
- `build.gradle.kts`: `implementation("com.squareup.okhttp3:okhttp:4.12.0")` (la versión que ya trae
  Coil 2.6; confirmar con `gradlew dependencies`).
- Reglas de backup que excluyan el DataStore `ginny_scrapper`.

### 1. Credenciales + sección "Scrapper" en Configuración
- `data/scrap/ScrapperSettingsRepository.kt`: DataStore propio `ginny_scrapper`. Campos:
  - SteamGridDB: `apiKey`.
  - ScreenScraper: `userId`, `userPassword` (se recomienda tenerlos: sin cuenta, ScreenScraper
    aplica el cupo de usuarios no registrados; **no probado**). `devId`/`devPassword` **no** van
    aquí: ver Decisiones, punto 1.
- `AppContainer`: instanciarlo. **No** tocar `GinnyConfigRepository`.
- UI: `SettingsSection.Scrapper("Scrapper")` + `ScrapperOptions.kt` con un `SettingsGroup` por
  proveedor, usando `GinnyTextField`. Campos secretos **enmascarados**. `SettingsModal` ya hace
  `when (section)`: agregar la rama.
- Interruptores "Qué descargar" (metadatos, ícono, wheel, box front/back/spine, video): el video
  pesa varios MB por juego, conviene que sea opcional y **desactivado por defecto**.
- Los controles de la sección Scrapper no son navegables con cruceta todavía (igual que el resto de
  Configuración, punto 2 de `pendientes-etapa-2.md`).

### 2. Núcleo común (`data/scrap/`)
- `ScrapHttp`: `OkHttpClient` con timeouts, `User-Agent` propio, **sin interceptor de log**.
- `ScrapResult` (sealed, ver arriba) y limpieza de credenciales en mensajes.
- `ScrapRateLimiter`: pausa mínima entre peticiones por proveedor + espera con retroceso en `429`.
- `NameNormalizer`: minúsculas, sin acentos/puntuación/artículos/paréntesis de región `(USA)` `[!]`,
  y similitud para aceptar o rechazar coincidencias de SteamGridDB.

### 3. Cliente SteamGridDB
- `SteamGridDbClient`: `search/autocomplete/{nombre}` → `grids/game/{id}?dimensions=512x512,…` →
  mejor puntuado; respaldo `icons/game/{id}`. Cabecera `Authorization: Bearer <apiKey>`.
- Solo acepta coincidencias por nombre normalizado igual o muy similar; ante la duda **no descarga**
  (mejor sin arte que arte de otro juego) y lo registra como `not_found`.
- Prefiere 512×512; si no hay, la más grande cuadrada disponible, **sin agrandar** (ver Decisiones).
- Descarga la imagen, la decodifica y la guarda como PNG cuadrado.

### 4. Cliente ScreenScraper
- `ScreenScraperClient` + `ScreenScraperParser` (puro, sin Android, para poder probarlo con JSON de
  ejemplo más adelante): `jeuInfos.php` y `jeuRecherche.php`, `output=json`.
- Parser: nombre, sinopsis (idioma), fecha (región), desarrollador, editor, géneros, jugadores, nota
  y la lista de `medias` filtrada por tipo y región según la política.
- `SystemDefinition.screenScraperSystemId` + los 28 valores.
- Bonus barato: `ssuserInfos.php` para leer el cupo restante (sirve para el botón "Verificar
  credenciales", ver abajo).

### 5. Almacenamiento (`ScrapStorage`)
- Escribe en `Assets/<sistema>/<carpeta>/` vía SAF, creando carpetas (`SafUtils.getOrCreateDirectory`)
  y respetando la regla de **no sobrescribir**.
- Escritura atómica del JSON (escribir y renombrar no es fiable en SAF: se escribe con modo `"wt"` como
  hace `GinnyConfigRepository`; si falla a mitad queda un JSON inválido, que el lector trata como
  "no scrapeado").
- Extender `StorageBootstrapper` para crear las carpetas nuevas junto con `icons/`.

### 6. Orquestador (`ScrapRepository`)
- `scrapGame(game, opciones)`, `scrapSystem(systemId, opciones)`, `scrapLibrary(opciones)` →
  `Flow<ScrapProgress>` (juego actual, hechos/total, cuántos encontrados / sin coincidencia / con
  error) y cancelable.
- Por juego: leer `metadata/` (si ya está `found` y no se fuerza, saltar lo que ya existe) →
  ScreenScraper → SteamGridDB → escribir.
- Cortar el lote completo ante `QuotaExhausted` o `AuthFailed` (no tiene sentido seguir).
- Tras guardar un ícono: `ScannedGameDao.updateIconUri(systemId, romUri, iconUri)` (consulta nueva)
  para que aparezca sin reescanear. Es una consulta, **no** cambia el esquema.
- `AppContainer`: instanciar y exponer.

### 7. Disparador mínimo en Configuración
- En la sección Scrapper, un grupo "Biblioteca" con **Scrapear biblioteca / Detener** y una línea de
  progreso, igual que el grupo de escaneo en Carpetas (sin modal aparte). Es lo mínimo para poder
  ejecutar la mecánica; el diseño real se decide en la fase visual.
- Se expone vía `SettingsViewModel` (o un `ScrapperViewModel` si crece).

### 8. Documentación
- `docs/architecture.md`: sección "Scrapper".
- `../docs/ginny-frontend.md` y `../docs/workspace-overview.md`: las carpetas nuevas de `Assets/` son
  parte del **contrato con el Assets Manager**, y `screenScraperSystemId` debe reflejarse luego en su
  `systems.json`.
- `docs/prompt-asset-manager-nwjs.md`: anotar el formato de `metadata/*.json`.

## Otras cuestiones que conviene tener presentes

1. **Botón "Verificar credenciales"** en cada proveedor (una petición barata: `ssuserInfos.php` /
   `search/autocomplete` de prueba). Es la única forma de saber que la clave está bien sin lanzar un
   lote. Se puede construir ahora aunque no se pruebe.
2. **Caché negativa y reintento:** un `not_found` se reintenta solo con "forzar" o pasado un tiempo
   (ej. 30 días), por si la base se completa.
3. **Cupo diario de ScreenScraper** (cuentas gratuitas: pocos miles de peticiones/día). Un lote
   grande de una biblioteca grande puede tardar días: de ahí el diseño reanudable. Cada juego cuesta
   1 petición de datos más 1 por cada archivo de media.
4. **Tamaño en disco:** los videos dominan. Tope de tamaño por video (si la API informa el tamaño) y
   conteo en el progreso.
5. **Datos móviles:** sin Wi-Fi, una descarga de videos puede costar caro. Interruptor "solo Wi-Fi"
   (requiere `ACCESS_NETWORK_STATE`), por defecto activo.
6. **Créditos y términos:** ScreenScraper pide identificar el software (`softname`) y mencionar su
   contribución; SteamGridDB atribuye el arte a su comunidad. Falta un lugar de créditos en la app
   (el Assets Manager tiene `credits.html`).
7. **Datos que cambian:** `names.json` (nombre bonito de edición manual) debe seguir mandando sobre
   el nombre scrapeado si existe; el scrapper nunca lo modifica.
8. **Juegos eliminados** (`RomRepository.deleteGame` / `AssetRepository.deleteIcon`): hoy solo borran
   el ícono. Con el scrapper hay que borrar también wheel, cajas, video y metadatos del juego.
9. **Juegos renombrados:** como la clave es `romFileName`, renombrar un ROM deja el arte huérfano
   (limitación ya existente con los íconos).
10. **PICO-8:** su ROM `.p8.png` ya es su propia imagen; probablemente no haya que scrapear arte.
11. **Hash en el hilo correcto:** el cálculo de hashes por SAF va en `Dispatchers.IO` y se cancela
    junto con el lote.
12. **Pruebas futuras:** el parser de ScreenScraper y el normalizador de nombres son puros; son los
    primeros candidatos a pruebas unitarias cuando se decida probar.

## Decisiones tomadas (2026-10-07)

1. **Credenciales de ScreenScraper.** *(Revisada el 2026-10-10.)*
   - **Usuario final** (usuario y contraseña de su cuenta): se escriben en Configuración >
     Scrapper; DataStore propio, fuera de `ginny.cfg` y de las copias de seguridad.
   - **Desarrollador de Ginny** (`devid`/`devpassword`): **no las escribe el usuario final.**
     Van **incrustadas en el APK** como campos de `BuildConfig` (`SCREENSCRAPER_DEV_ID`,
     `SCREENSCRAPER_DEV_PASSWORD`), inyectados al compilar desde `scrapper.properties` (ignorado por
     git, plantilla `scrapper.properties.example`; mismo patrón que `keystore.properties`). El código
     fuente y el repositorio nunca las contienen. Se leen vía `ScrapperDevCredentials`; si se
     compila sin el archivo quedan vacías y ScreenScraper se desactiva (`screenScraperAvailable`).
     Limitación asumida: dentro de un APK son extraíbles; ScreenScraper puede revocarlas.
   - Las credenciales reales están en `docs/Notas.secret.md` (ignorado por git vía `*.secret.md`).
2. **Ícono cuadrado:** grids de SteamGridDB, **512×512 preferido**; si no existe, la **más grande
   disponible que sea cuadrada y menor**, **sin agrandar nunca** (no se reescala hacia arriba). El
   endpoint `icons` queda como último respaldo, con la misma regla.
3. **Región / idioma:** `wor → eu → us → jp → (la primera)`; sinopsis `es → en → (la primera)`.
4. **Formatos:** wheel y cajas en su **formato original**, siempre que sea uno que Android/Coil
   decodifique sin problema (PNG, JPEG, WebP); cualquier otro se descarta o se convierte a PNG.
   **Finalidad de las cajas:** servir de textura de una caja 3D rotable con el dedo (frente, trasera
   y lomo). Consecuencias para el scrapper:
   - Las tres imágenes de una misma caja deben ser **de la misma región/edición**: se elige la
     región del frente y se buscan trasera y lomo de esa misma región antes de caer en otra. Se
     guarda la región usada de cada una en el JSON.
   - Se conservan las dimensiones originales (nada de recortes ni reescalados: la proporción real
     del frente/lomo define las medidas de la caja).
   - Si falta alguna de las tres, el resto igual se guarda; qué hacer sin ellas lo decide la fase
     visual.
5. **Video:** `video-normalized` (re-codificado por ScreenScraper a un formato estándar, mp4/H.264,
   que decodifica por hardware en Android) como única fuente preferida. El `video` original solo se
   acepta de respaldo si es mp4; si no, se omite. Motivo: reproducción ágil y fluida sin trabar la
   app. Desactivado por defecto en "Qué descargar". Se reproducirá con Media3/ExoPlayer en la fase
   visual (decodificación por hardware, en otro hilo; no se agrega ahora).

## Registro de avances

| Fecha | Paso | Notas |
|---|---|---|
| 2026-10-07 | 1. Credenciales + sección "Scrapper" (y la parte de backup del paso 0) | `data/scrap/ScrapperSettingsRepository.kt` (DataStore propio `ginny_scrapper`, fuera de `ginny.cfg`) y `ScrapMedia.kt`. `res/xml/backup_rules.xml` + `data_extraction_rules.xml` excluyen ese archivo de las copias (referenciados en el manifest). `SettingsSection.Scrapper` + `ScrapperOptions.kt`: grupos ScreenScraper (usuario, contraseña, ID y contraseña de desarrollador), SteamGridDB (clave de API), "Qué descargar" (7 interruptores; video apagado por defecto) y General (solo Wi-Fi, mostrar claves). `GinnyTextField` gana `secret`/`masked` (puntos, sin autocorrección). Solo compila (`compileDebugKotlin` + `processDebugManifest`); no probado en dispositivo. **Pendiente del paso 0:** permiso `INTERNET`/`ACCESS_NETWORK_STATE` y OkHttp (se agregan junto a los clientes, para no dejar dependencias sin uso). El botón "Verificar credenciales" queda para cuando existan los clientes. |
| 2026-10-10 | 1b. Credenciales de desarrollador fuera de la pantalla | `devid`/`devpassword` ya no se piden al usuario: `scrapper.properties` (ignorado por git) → `BuildConfig.SCREENSCRAPER_DEV_ID/PASSWORD` (`buildFeatures.buildConfig = true`) → `data/scrap/ScrapperDevCredentials.kt`. Plantilla `scrapper.properties.example` y sección en el README. Se quitaron los dos campos de `ScrapperOptions`, del `SettingsViewModel` y del `ScrapperSettingsRepository`. Verificado: compila y el `BuildConfig` generado trae ambos valores. |
| 2026-10-10 | 0 y 2. Base de red y núcleo común | Permisos `INTERNET` y `ACCESS_NETWORK_STATE`; OkHttp 4.12.0 (la versión que ya traía Coil). `data/scrap/`: `ScrapResult` (Ok / NotFound / AuthFailed / QuotaExhausted / RateLimited / Unavailable / NetworkBlocked; `abortsBatch` para cortar el lote), `ScrapHttp` (cliente único sin log, User-Agent propio, cancelable también a mitad de la lectura), `ScrapRateLimiter` (serializa por proveedor, pausa mínima y reintento con espera creciente ante 429), `ScrapNetwork` ("Solo con Wi-Fi" = red sin tarifa), `ScrapRedactor` (quita devid/devpassword/ssid/sspassword/Bearer de cualquier texto), `NameNormalizer` (normalización + similitud; los números distintos dan 0, para no confundir "Mario Bros" con "Mario Bros 3"). **Primeras pruebas unitarias del proyecto** (`app/src/test`, JUnit 4.13.2, solo JVM): 11 pruebas de `NameNormalizer` y `ScrapRedactor`, todas pasan (`gradlew testDebugUnitTest`). Ninguna toca las APIs. |
| 2026-10-10 | 4. Cliente de ScreenScraper (+ mapa de sistemas) | `data/scrap/screenscraper/`: `ScreenScraperSystems` (los 28 `systemeid`, **tomados de `systemesListe.php`** y no de memoria; en un mapa aparte, no en `SystemDefinition`, con una prueba que avisa si se agrega un `SystemId` sin entrada; `romTypeFor`: rom / iso / dossier), `ScreenScraperModels`, `ScreenScraperParser` (puro, `org.json`), `ScreenScraperSelector` (política de región/idioma/formato, caja coherente, wheel, video), `ScreenScraperErrors` (código HTTP → `ScrapResult`), `ScreenScraperClient` (`findGame`: por archivo con hash opcional y, si no, por búsqueda con parecido ≥ 0,92; `verifyAccount`; `download` con limitador y solo hacia `*.screenscraper.fr`). **Verificado con respuestas reales** (prueba temporal, no commiteada): la de 1,26 MB se lee en 57 ms, UTF-8 intacto, caja `eu` completa y de una sola región, búsqueda elige el juego correcto entre 3. **34 pruebas unitarias** en total, todas pasan. Pendiente: probar el cliente completo (red) dentro de la app. |
| 2026-10-10 | 3. Cliente de SteamGridDB | `data/scrap/steamgriddb/`: `SteamGridDbClient` (`findIcon(nombre, añoEsperado)`, `verifyKey`, `download`), `SteamGridDbParser`, `SteamGridDbSelector`, `SteamGridDbErrors`. Flujo: busca por nombre → solo acepta resultados con parecido ≥ 0,92 (desempata por año esperado, verificado, id) → hasta 2 candidatos → grids cuadrados 512/1024 y, si no hay, icons → elige imagen. **Regla de tamaño (pedida por el usuario):** 512 exacto; si no, las mayores (la menor primero, se **reducen**); si no, las menores (la mayor primero, **nunca se agrandan**); luego idioma es > en > otro, estilo (`alternate` antes que `white_logo`), puntuación. `ScrapIo` (copia con tope de tamaño; también lo usa ahora la descarga de ScreenScraper) y `NameNormalizer.stripTags`. **Verificado con la API real:** 401 = clave inválida/ausente, 404 = juego inexistente (`Game not found`), búsqueda sin resultados = 200 con lista vacía, los `.ico` de `icons` vienen con 0×0 y se descartan. En la muestra real eligió el grid 512 en inglés de entre 23 (había un 512 en japonés). **57 pruebas unitarias** en total, todas pasan. Pendiente: probar la red dentro de la app. |
| 2026-10-10 | 5. Almacenamiento en `Assets/` | `AssetFolder` (carpetas `icons/ wheel/ box-front/ box-back/ box-spine/ video/ metadata/`; contrato con el Assets Manager), `GameMetadata` (JSON por juego, con `status` found/not_found como caché negativa, fechas por región, regiones usadas y `missingMedia`; ida y vuelta probada, tolera truncado y campos desconocidos), `IconProcessor` (decodifica PNG/JPEG/WebP, exige cuadrado, **reduce** a 512 sin agrandar nunca, guarda PNG; la aritmética vive en `IconSizing`, probada), `ScrapStorage`/`SystemAssets` (SAF: lista cada carpeta una sola vez; **nunca sobrescribe** imágenes; escribe en `.part` y renombra, así un corte no deja archivos truncados, y limpia los `.part` sueltos al abrir; los metadatos sí se reemplazan; toda excepción → `StorageFailed`, fatal si falta la carpeta o el permiso). `ScrannedGameDao.updateIconUri` + `GameIndexRepository.updateIcon` (el ícono aparece sin reescanear; es una consulta, no cambia el esquema). "Eliminar juego" ahora borra también wheel, cajas, video y metadatos. **Desvío del plan:** `StorageBootstrapper` **no** crea las carpetas nuevas: serían 6 × 28 = 168 operaciones SAF al elegir la Carpeta de Usuario; se crean al primer uso. **67 pruebas unitarias**; la capa SAF y `IconProcessor` solo están verificadas por compilación (usan APIs de Android; no probado en dispositivo). |
| 2026-10-10 | 6. Orquestador (`ScrapRepository`) | `run(games, force): Flow<ScrapProgress>`: secuencial, reanudable, cancelable. Decisión pura y probada en `ScrapPlanner` (qué falta, cuándo reintentar: caché negativa de 30 días, `missingMedia`, nombre estándar antes del ícono; forzar ignora cachés pero **no sobrescribe archivos**) y `MetadataMerge`; `RomHasher` (CRC32/MD5/SHA-1 en una pasada; solo archivos ≤ 32 MB, no comprimidos, no arcade ni carpetas). Por juego: ScreenScraper (metadatos, wheel, cajas, video) → SteamGridDB (ícono con el nombre estándar y el año) → metadatos JSON, que se guardan aunque se cancele o se corte (`NonCancellable`). **Tolerancia a fallos:** un proveedor con credenciales rechazadas o cupo agotado se **deja de usar** y el lote sigue con el otro (se corta solo si no queda ninguno, falta la Carpeta de Usuario, no hay red / "Solo con Wi-Fi", o 5 fallos de conexión seguidos). `GameMetadata.Status.PENDING` nuevo (ScreenScraper aún no consultado). **92 pruebas unitarias**, todas pasan; el flujo completo contra la red y SAF **no está probado** (solo compila). |
| 2026-10-10 | 7. Disparador mínimo en Configuración > Scrapper | `ScrapRunner` (vive en el scope de la aplicación: salir de Configuración no detiene el lote; un lote a la vez; "Detenido." al cancelar; error inesperado → mensaje, nunca un cierre). Grupo **Biblioteca** al inicio de la sección: "Scrapear" / "Reintentar" (fuerza: vuelve a consultar lo no encontrado y las medias ausentes, sin sobrescribir archivos) / "Detener", con barra de avance, contadores (datos nuevos · sin coincidencia · ya completos · con error), avisos de proveedores dejados de usar y motivo si se cortó. Fila **Verificar** en cada proveedor (ScreenScraper: cuenta + cupo del día + hilos; SteamGridDB: clave); avisa si el APK se compiló sin las credenciales de desarrollador. `SettingsRow(subtitleMaxLines)` y `GinnyProgressBar` nuevos. Es el diseño mínimo para poder ejecutar la mecánica; el visual real se decide después. **Verificado:** compila, 92 pruebas pasan, `assembleDebug` genera el APK (69,1 MB). **No probado en dispositivo ni contra la red desde la app.** |
| 2026-10-10 | 8. Scrapeo en la interfaz (modales) | **Lanzar:** el grupo "Biblioteca" pasó al final de Configuración > Scrapper con un único botón **Scrappear** (si ya hay un lote: "Ver progreso"). Abre `ScrapLaunchModal`: lista de sistemas (casilla, logo, nombre, "N juegos · M pendientes · K sin encontrar"; marcados por defecto los que tienen pendientes) y dos combos: *Sólo pendientes / Sólo los no encontrados / Todos* (`ScrapScope`; los dos últimos ignoran las cachés negativas) y *No sobreescribir si existe / Sobreescribir*. Los números salen de `ScrapRepository.inspect` (lee `Assets/` y aplica `ScrapPlanner` con y sin forzar; aparecen sistema por sistema). **Sobrescribir** (nuevo): `ScrapPlanner.plan(overwrite)` pide de nuevo todo lo elegido y `SystemAssets.write(overwrite)` descarga a `.part` y **solo al terminar bien** borra lo anterior y renombra (si falla, queda el archivo viejo). **Avance:** `ScrapActivityIcon` (aro que gira + flecha) a la izquierda de la hora mientras corre el lote; al tocarlo, `ScrapProgressModal` con barra, "Elemento N de M", Archivo, Sistema, Metadatos Sí/No, Imágenes Sí/No, el detalle de cada media, paso en curso, contadores y botón **Detener**. `ScrapProgress.current` ahora es `ScrapCurrent` (en vivo, varias emisiones por juego). Aviso (Toast) al terminar si el modal estaba cerrado. B cierra primero los modales de scrapeo (`MainActivity` delega en el `BackHandler`). `ScrapViewModel` nuevo. 98 pruebas, compila, `assembleDebug` OK; **no probado en dispositivo**. Pendiente: navegación con cruceta dentro de estos modales. |
| 2026-10-10 | 9. Scrapeo de un juego desde su modal | `GameModal`: menú de **tres puntos** (esquina superior derecha, dibujados) con **Scrappear** y **Eliminar**. *Scrappear* abre "Opciones de Scrappeo" (se ajusta al contenido): combo **Sólo lo que falte / Todo** (Todo = sobrescribir) e Iniciar/Cancelar; `ScrapRunner.startGame` reutiliza el lote (siempre ignora las cachés de "no encontrado"), abre el modal de avance y, si ya había un lote, avisa y muestra ese. *Eliminar* abre una confirmación y **reemplaza al botón Eliminar** de la fila de acciones (ya no hay doble toque). B cierra primero el diálogo; la tarjeta recupera el foco al cerrarse. Compila; no probado en dispositivo. Los menús son táctiles: la cruceta no llega a los tres puntos todavía. |
| 2026-10-10 | 10. Usar lo scrapeado: información y caja 3D en el modal del juego | **Datos:** `ScrapStorage.readGameAssets` (solo lectura, no crea carpetas) devuelve `GameAssets` (metadatos + imágenes de caja y wheel); se relee al abrir el modal y al terminar un scrapeo. **Diseño del modal:** encabezado y acciones fijos; en medio, un contenido con scroll (bordes que se desvanecen; arriba/abajo de la cruceta desplaza) con una fila *Información (izquierda) + caja en 3D (derecha, 38 %)* y, debajo, Archivo y Emulador. Tarjeta angosta (< 560 dp): la caja pasa arriba. Sin cara de caja guardada, la información ocupa todo el ancho. Información: sinopsis, estudio, editor (si difiere), año, géneros, jugadores y valoración (x / 10). **Caja 3D** (`ui/components/box/`): sin librería 3D; `BoxGeometry` (código puro, 12 pruebas: caras visibles, orden, perspectiva, texturas sin espejar en 8 ángulos) + `GameBox3D` (Canvas; cada textura se dibuja sobre su cuadrilátero con `Matrix.setPolyToPoly`; sombreado por cara y sombra en el suelo). Es una caja **negra** cuyas medidas salen de las texturas (ancho = aspecto del frente, fondo = aspecto del lomo); una cara sin imagen queda negra. Empieza a 35 grados (3/4 de perfil, lomo a la izquierda del frente), gira sola a 6 grados por segundo (una vuelta por minuto) y se gira arrastrando en horizontal (el arrastre vertical sigue desplazando el modal); al soltar vuelve suavemente al giro lento. Texturas decodificadas reducidas (lado mayor 900 px). **No probado en dispositivo.** Pendiente: usar el wheel, el video y la caja en otros lugares. |
| 2026-10-10 | 10b. Nueva distribución del modal del juego | Cuerpo = **Row**: a la izquierda una Column con el *encabezado* (fijo) y el *contenido con scroll* (Información, Archivo, Emulador); a la derecha la **caja 3D a toda la altura del cuerpo**, siempre visible (no se desplaza). El pie (botones) queda fuera del Row. Los **tres puntos pasaron a la esquina inferior izquierda**, en el pie. En pantallas angostas (ancho útil < 560 dp) **la caja no se dibuja** (ya no pasa arriba); sin cara de caja guardada, el contenido ocupa todo el ancho. Compila; 110 pruebas; no probado en dispositivo. |
| 2026-10-10 | 11. Secuencia de "Jugar" | Al tocar Jugar (o pulsar A): suena `sfx_launch` y la caja recibe **un golpe de giro** (`GameBox3D(spinTrigger)`: 1000 grados por segundo que se frenan solos hasta el giro lento, con la misma curva que tras soltarla con el dedo); a los **1000 ms** la pantalla se **funde a negro en 700 ms** (capa negra a pantalla completa en `GameModal`, no deja pasar toques) y recién entonces se llama a `viewModel.launch`. Si el lanzamiento falla (emulador no instalado u otro error) se muestra el aviso, la pantalla vuelve a aclararse y se puede reintentar. Un segundo toque/A durante la secuencia no hace nada; B cierra el modal y cancela el lanzamiento. Constantes ajustables: `LaunchSpinMs`, `LaunchFadeMs` (`GameModal.kt`) y `LaunchSpinDegreesPerSecond` (`GameBox3D.kt`). Compila; no probado en dispositivo. |
| 2026-10-10 | 11b. Sonido de lanzamiento sin silencio inicial | `sfx_launch.mp3` tenía ~130 ms de silencio antes del sonido (medido decodificando el archivo: primera muestra audible a 129 ms, ataque a 140 ms), por eso "Jugar" sonaba tarde. Se quitaron sus **4 primeros fotogramas MP3 (104,5 ms)**, sin recodificar: el audio restante es idéntico muestra a muestra (diferencia máxima 0,0001); quitar 5 ya distorsiona el ataque. Ahora el sonido empieza a ~24 ms (1384 ms de duración, antes 1489). La reproducción en sí ya era inmediata (`SoundPool.play` con el sonido precargado). |
| 2026-10-10 | 11c. Sonido de lanzamiento: sin el "tun" inicial | Análisis del audio decodificado (energía y frecuencia por bloques): 20-120 ms = una **primera nota de ~551 Hz** que arranca fuerte (pico 0,55) y se apaga en ~100 ms (el "tun"); 120-260 ms = una cola casi inaudible (-40 a -44 dB); a los **264,5 ms** entra la melodía (salto de -39 a -29 dB), que luego sube de volumen. Se recortó desde 261,5 ms (3 ms antes de esa nota) y se guardó como **WAV** (`sfx_launch.wav`, 193 KB, 44,1 kHz, 16 bits, estéreo; reemplaza al `.mp3`, mismo nombre de recurso): un MP3 solo se corta por fotogramas de 26 ms y cortar justo antes de una nota distorsiona su ataque; el WAV además no tiene retardo de decodificación. Fundido de entrada de 2 ms para evitar un clic. Dura 1123 ms. Si se quisiera saltar también las primeras notas suaves, la melodía "fuerte" arranca hacia los 320 ms del audio original. |
| 2026-10-10 | 11d. Lanzar sin cerrar el modal y sin animación del sistema | (1) El modal del juego **ya no se cierra al lanzar**: la capa negra vive en él, y al cerrarlo la interfaz reaparecía unos instantes mientras Android animaba el cambio de aplicación (la ventana deslizándose). Al volver al frontend (la actividad se pone en pausa y luego se reanuda) la pantalla se aclara en 500 ms y el modal sigue abierto en ese juego; si la app nunca llega a ponerse en pausa (el lanzamiento no ocurrió), se aclara a los 5 s para no dejar al usuario en negro; si el lanzamiento falla se aclara de inmediato con el aviso de siempre. (2) `LauncherStrategy.launch` pide **cero animación de transición** de dos maneras: `ActivityOptions.makeCustomAnimation(context, 0, 0)` en `startActivity` y `overridePendingTransition(0, 0)` (Android no siempre respeta una sola al cambiar de aplicación). **No verificable sin el dispositivo**; si el sistema o el emulador siguen imponiendo su animación, la capa negra ya no desaparece, así que solo se vería negro deslizándose. |
| 2026-10-12 | 12. Cambiar ícono (varios íconos por juego) | **Almacenamiento:** el ícono que se muestra sigue siendo `Assets/<sistema>/icons/<rom>.png` (lo que ya leen la grilla, el escaneo y el Assets Manager: no cambia). Las alternativas van en `Assets/<sistema>/icons-alt/<rom>/1.png, 2.png...`, **máximo 5 por juego** (el ícono actual cuenta). "Usar" copia la elegida sobre `icons/<rom>.png`; el ícono que había se conserva antes como una alternativa. Cuál es la activa se averigua comparando el contenido (SHA-1): no hay archivo de estado. Con 5 ocupados, uno nuevo reemplaza al más antiguo que no sea el activo; una imagen idéntica a una guardada no se repite. "Eliminar juego" borra también `icons-alt/<rom>/`. **Código:** `IconChooser` (+ `IconSlots`, puro y probado), `SystemAssets.listIconAlternatives/writeIconAlternative`, `SteamGridDbClient.findIconCandidates` (hasta 12 imágenes cuadradas, la mejor primero; `SteamGridDbSelector.rankImages`; miniatura `thumb`). Al cambiar, la grilla recibe la dirección con `?v=<ms>` (la ruta del archivo no cambia y Coil guardaría la imagen vieja) y se vacía la caché de memoria de Coil de la ruta limpia. **UI:** menú Más > **Cambiar icono** → `IconPickerModal`: cuadrícula con el cursor (✓ en el activo), pie con **SteamGridDB (Y)**, **Cerrar (B)**, **Usar (A)**. Compila, pruebas pasan; **no probado en dispositivo ni contra SteamGridDB desde la app**. Para el Assets Manager: `icons-alt/` es una carpeta nueva que puede ignorar. |
