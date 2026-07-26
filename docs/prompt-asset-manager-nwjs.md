# Prompt: Ginny Asset Manager (app de escritorio en NW.js)

Este documento es el prompt/especificación para construir, en una sesión aparte, una app de
escritorio (Windows en primer lugar; NW.js es multiplataforma así que macOS/Linux deberían andar
sin casos especiales si se usa siempre el módulo `path` de Node) que completa los **íconos de
juegos** de la "Carpeta de Usuario" que consume **Ginny Frontend** (la app Android de este mismo
repo), cruzándola contra el contenido real de la **Carpeta de ROMs**.

No es parte del proyecto Android — es una herramienta complementaria para usar en la PC antes de
copiar/sincronizar la Carpeta de Usuario al dispositivo Android (o directo sobre una carpeta
compartida/montada).

## Supuestos hechos en este documento (avisar si alguno está mal — son baratos de corregir)

- **Una sola "Carpeta de ROMs" base**, con una subcarpeta por sistema (misma convención que ya usa
  la app Android — no selectors de ruta independientes por sistema). Si en realidad tenés los ROMs
  de cada sistema en discos/rutas sueltas sin una carpeta base común, avisame y cambio el diseño a
  "un selector de carpeta por sistema" en vez de uno solo.
- El único asset que esta herramienta administra es el **ícono por juego** (`Assets/<sistema>/icons/`).
  Completar el logo de sistema (`system.png`) queda como mejora opcional (ver "Fuera de alcance").
- Los íconos siempre se guardan como **PNG**, sea cual sea el formato de origen (ver "Conversión de
  imagen" más abajo) — así coincide con lo que espera `AssetRepository.kt` del lado Android
  (`"$romFileName.png"` exacto, sin importar la extensión real de la imagen pegada/descargada).

## Contexto: qué espera la app Android (fuente de verdad)

### Catálogo de sistemas

Mismo catálogo que `app/src/main/java/com/ginny/frontend/data/model/SystemCatalog.kt` /
`SystemId.kt` del proyecto Android — conviene copiarlo a un JSON propio (`systems.json`) en vez de
hardcodearlo disperso en el código, para poder actualizarlo a mano el día que la app Android agregue
un sistema nuevo:

| id | Carpeta (ROMs y Assets) | Extensiones válidas | Organización |
|---|---|---|---|
| NES | `nes` | `.nes` | archivo por juego |
| SNES | `snes` | `.sfc`, `.smc` | archivo por juego |
| Game Boy | `gb` | `.gb` | archivo por juego |
| Game Boy Color | `gbc` | `.gbc` | archivo por juego |
| Game Boy Advance | `gba` | `.gba` | archivo por juego |
| PlayStation | `ps1` | `.iso`, `.bin`, `.cue`, `.chd`, `.pbp` | archivo por juego |
| Genesis / Mega Drive | `genesis` | `.md`, `.gen`, `.bin` | archivo por juego |
| Master System | `mastersystem` | `.sms` | archivo por juego |
| Neo Geo | `neogeo` | `.zip` | archivo por juego |
| FinalBurn Neo | `fbneo` | `.zip` | archivo por juego |
| MAME | `mame` | `.zip` | archivo por juego |
| GameCube | `gc` | `.iso`, `.gcm`, `.rvz` | archivo por juego |
| PlayStation 2 | `ps2` | `.iso`, `.chd`, `.bin` | archivo por juego |
| PSP | `psp` | `.iso`, `.cso`, `.pbp` | archivo por juego |
| Dreamcast | `dreamcast` | `.chd`, `.gdi`, `.cdi` | archivo por juego |
| Nintendo Switch | `switch` | `.nsp`, `.xci`, `.nsz`, `.xcz` | **carpeta por juego** (ver abajo) |

- **"archivo por juego"**: cada archivo con extensión válida directamente dentro de
  `<Carpeta de ROMs>/<carpeta>/` (sin subniveles) es un juego.
- **"carpeta por juego"** (solo Switch): cada subcarpeta dentro de `<Carpeta de ROMs>/switch/` es un
  juego; el nombre para mostrar y para el ícono es el **nombre de esa carpeta**, no el de ningún
  archivo de adentro (el juego base + updates/DLC conviven en archivos sueltos dentro de esa
  subcarpeta, pero no importan para esta herramienta).

### Convención de la Carpeta de Usuario

```
[Carpeta de Usuario]/
└── Assets/
    ├── nes/{icons/, system.png}
    ├── snes/{icons/, system.png}
    └── ... (una por sistema)
```

- Ícono esperado para un juego "archivo por juego": `Assets/<carpeta>/icons/<NombreDeArchivoConExtensión>.png`
  — ej. el ROM `Super Mario Bros 3.nes` espera el ícono `Assets/nes/icons/Super Mario Bros 3.nes.png`
  (**la extensión del ROM queda incluida en el nombre del PNG, y después se le agrega `.png`**).
- Ícono esperado para Switch: `Assets/switch/icons/<NombreDeCarpetaDelJuego>.png` (sin extensión,
  porque la clave es el nombre de la carpeta).
- La comparación de nombre de archivo la hace la app Android en minúsculas
  (`AssetRepository.loadIconIndex`), pero para evitar sorpresas esta herramienta debería escribir el
  nombre tal cual aparece el ROM/carpeta (mismo casing), sin normalizarlo.
- Si `Assets/<carpeta>/` o `Assets/<carpeta>/icons/` no existen todavía (por ejemplo, la Carpeta de
  Usuario nunca se abrió desde la app Android), esta herramienta debería poder crearlas ella misma
  al detectar que faltan — no asumir que siempre existen de antemano.

## Objetivo funcional

1. Elegís una **Carpeta de Usuario** y una **Carpeta de ROMs** (recordar ambas rutas entre sesiones).
2. La app escanea la Carpeta de ROMs con el catálogo de arriba y arma, por sistema, la lista de
   juegos encontrados (solo se listan sistemas con al menos un juego).
3. Para cada juego, cruza contra `Assets/<sistema>/icons/` en la Carpeta de Usuario:
   - Si el ícono existe, se muestra como miniatura.
   - Si no existe, se muestra un placeholder claro (ej. ícono genérico + "Sin ícono") — no cortar el
     juego de la lista ni bloquear nada, es justamente lo que hay que poder completar.
4. Hacés **clic en un juego** de la lista para seleccionarlo (selección única, se resalta
   visualmente).
5. Con el juego seleccionado, **Ctrl+V**:
   - Si el portapapeles tiene una **imagen** (copiada de un editor, de "Copiar imagen" en un
     navegador, etc.): se decodifica, se convierte a PNG y se guarda como el ícono de ese juego.
   - Si el portapapeles tiene **texto que es una URL** (`http://` o `https://` que apunte a algo que
     resuelva como imagen): se descarga, se valida que sea una imagen, se convierte a PNG y se guarda
     igual que el caso anterior.
   - Si no es ninguna de las dos cosas, mostrar un mensaje claro ("El portapapeles no tiene una
     imagen ni una URL válida") sin romper nada.
6. La miniatura del juego se actualiza al instante después de guardar. Pegar de nuevo sobre el mismo
   juego **reemplaza** el ícono anterior sin pedir confirmación (repetir el gesto ya es la intención
   de reemplazar).

## Stack técnico sugerido

- **NW.js** (versión estable reciente — usar la última LTS/`stable` publicada al momento de
  construir esto, no fijarse en una versión vieja). A diferencia de Electron, NW.js permite acceso
  directo a Node (`fs`, `path`, `https`) desde el contexto de la página sin IPC — aprovechalo para
  mantener el código simple (un solo proceso de UI, sin main/preload separados, ya que es una
  herramienta interna de un solo usuario y no hay superficie de ataque relevante).
- **UI**: HTML/CSS/JS plano alcanza y de sobra para esta herramienta — no hace falta React/Vue ni
  bundler. Un `index.html` + `renderer.js` + `style.css` es un diseño razonable. Esto es una
  sugerencia, no un requisito estricto: si quien construye la app prefiere un framework liviano, no
  hay objeción, pero no es necesario.
- **Selección de carpetas**: usar el atributo especial de NW.js `nwdirectory` en un `<input
  type="file">` (`<input type="file" nwdirectory>`), que abre el selector nativo de carpetas del SO
  y devuelve la ruta absoluta — no hace falta ninguna librería extra para esto.
- **Escaneo de archivos**: `fs.readdirSync`/`fs.promises.readdir` + `path.join`, filtrando por
  extensión (comparar en minúsculas) según el catálogo de sistemas.
- **Pegado desde el portapapeles**: escuchar el evento `paste` a nivel de documento (no hace falta
  que el foco esté en un input específico, basta con tener un juego seleccionado):
  - `event.clipboardData.items` → buscar un item cuyo `kind === 'file'` y `type` empiece con
    `image/` → `item.getAsFile()` → `Blob`.
  - Si no hay imagen, buscar un item `kind === 'string'` con `type === 'text/plain'` → leer el texto
    con `item.getAsString(...)` → si matchea `^https?:\/\//i`, tratarlo como URL a descargar.
- **Descarga de URL**: `fetch(url)` (Chromium moderno lo soporta nativo en NW.js) → validar
  `response.headers.get('content-type')` empieza con `image/` → `response.blob()`.
- **Conversión de imagen a PNG** (para portapapeles y para URL por igual, sea cual sea el formato de
  origen — jpg, webp, gif, bmp, png ya existente, etc.): crear un `<img>` a partir de un
  `URL.createObjectURL(blob)`, dibujarlo en un `<canvas>` del mismo tamaño, y exportar con
  `canvas.toBlob(cb, 'image/png')`. Convertir el blob resultante a `ArrayBuffer`/`Buffer` y escribirlo
  a disco con `fs.promises.writeFile(rutaDestino, buffer)`. Esto evita depender de librerías nativas
  de procesamiento de imágenes.
- **Persistencia de configuración** (últimas rutas usadas): un `config.json` simple al lado del
  ejecutable, o en `nw.App.dataPath` si se prefiere no depender de la carpeta de instalación.

## Estructura de UI sugerida

- Panel superior: dos campos de solo lectura + botón "Elegir…" para Carpeta de Usuario y Carpeta de
  ROMs, más un botón "Volver a escanear" (por si cambiaste archivos con la app abierta).
- Panel lateral o pestañas: una por sistema detectado (con contador de "faltan N íconos" para saber
  de un vistazo dónde falta trabajo), en el mismo orden/nombre que la tabla de arriba.
- Panel principal: grilla de tarjetas, una por juego del sistema seleccionado — miniatura (o
  placeholder) + nombre del juego. Click para seleccionar (resaltado visible, ej. borde de color).
- Alguna indicación visual/textual persistente de "juego seleccionado: <nombre>" y de qué hacer
  (`Ctrl+V para pegar una imagen o una URL`), para que no haga falta adivinar el gesto.
- Mensajes de error/éxito breves (toast o barra de estado) para: pegado sin imagen/URL válida,
  descarga fallida (404, timeout, contenido que no es imagen), o guardado exitoso.

## Fuera de alcance (v1) — no implementar salvo que se pida explícitamente

- Scraping/metadata externa de juegos (nombres "bonitos", región, año, etc.) — el nombre mostrado es
  siempre el del archivo/carpeta del ROM tal cual, sin lookup contra ninguna base de datos externa.
- Completar `system.png` (logo de sistema) — mismo mecanismo que los íconos de juego sería trivial de
  agregar después si se pide, pero no es parte de este pedido.
- Arrastrar y soltar (`drag & drop`) como alternativa a `Ctrl+V` — si se quiere, es una mejora
  incremental sobre el mismo pipeline de conversión/guardado, no un rediseño.
- Editar cualquier otra configuración de la app Android (mapeo de botones, emulador/core por sistema,
  volúmenes, etc.) — esta herramienta solo administra íconos.
- Replicar el look visual (tema aurora/vidrio) de la app Android — una UI utilitaria de escritorio
  está bien, priorizar función sobre estética.

## Notas de compatibilidad multiplataforma

- Usar siempre `path.join`/`path.sep` de Node en vez de concatenar `/` o `\` a mano, para que
  funcione igual en Windows/macOS/Linux.
- Los nombres de carpeta con espacios (ej. `Background Music`, o cualquier carpeta de sistema) deben
  funcionar sin problema vía Node `fs` — no hace falta escapado especial, a diferencia de un shell.
