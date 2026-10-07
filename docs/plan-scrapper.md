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

## Pasos de implementación (cada uno es un commit independiente)

### 0. Base de red
- `AndroidManifest.xml`: permiso `INTERNET` (y `ACCESS_NETWORK_STATE` si se quiere avisar sin red).
- `build.gradle.kts`: `implementation("com.squareup.okhttp3:okhttp:4.12.0")` (la versión que ya trae
  Coil 2.6; confirmar con `gradlew dependencies`).
- Reglas de backup que excluyan el DataStore `ginny_scrapper`.

### 1. Credenciales + sección "Scrapper" en Configuración
- `data/scrap/ScrapperSettingsRepository.kt`: DataStore propio `ginny_scrapper`. Campos:
  - SteamGridDB: `apiKey`.
  - ScreenScraper: `userId`, `userPassword` (opcionales: sin cuenta se usa el cupo anónimo, más
    limitado) y `devId`, `devPassword` (ver "Preguntas abiertas").
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

1. **Credenciales de ScreenScraper** (incluidas las de desarrollador): el usuario las tiene y las
   dejó en `docs/Notas.secret.md` (ignorado por git vía `*.secret.md`). Se ingresan a mano en la
   sección Scrapper; **no se copian al código ni a ningún archivo versionado**. Campos en el grupo
   ScreenScraper (las de desarrollador bajo "Avanzado").
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
| | | |
