# Cómo publicar un release de Ginny Frontend (Android)

Receta para consultar cada vez que haya que sacar una versión nueva. Todo se ejecuta desde la carpeta
`Ginny Frontend Android` (PowerShell o doble clic en los `.bat`). No hace falta abrir Android Studio.

## Requisitos (una sola vez)

- `keystore.properties` y el `.jks` que referencia (firma del APK). **No se versionan**; guárdalos a salvo: sin ellos,
  una versión nueva no se instala encima de la anterior. Ver `README.md`, «Generar tu propio APK».
- `adb` (SDK en `D:\Android\Sdk\platform-tools`) y `gh` (GitHub CLI, con sesión iniciada: `gh auth status`).
- Remoto `origin` = `git@github.com:sanchezmendozajean/ginny-frontend.git`.

## Pasos

1. **Todo commiteado y probado.** `git status` limpio; la rama es `main`.

2. **Subir la versión.**
   ```powershell
   .\bump-version.bat          # patch: 0.2.1 -> 0.2.2   (también .\bump-version.bat minor | major)
   ```
   Sube `versionCode` +1 y `versionName` en `app/build.gradle.kts` y actualiza los ejemplos de `README.md`.
   (`versionCode` debe crecer siempre: si no, `adb install -r` falla con `INSTALL_FAILED_VERSION_DOWNGRADE`.)

3. **Commit de la versión.**
   ```powershell
   git add app/build.gradle.kts README.md
   git commit -m "Versión X.Y.Z (versionCode N)"
   ```

4. **Compilar el APK firmado.**
   ```powershell
   .\build-release.bat
   ```
   Deja `dist\GinnyFrontend-X.Y.Z.apk` (borra los APK anteriores de `dist`). Si falla, el error sale en la terminal.

5. **Probarlo en el dispositivo** con `install-apk.bat`, pasándole como parámetro la ruta del APK:
   ```powershell
   .\install-apk.bat dist\GinnyFrontend-X.Y.Z.apk
   ```
   (Sustituye `X.Y.Z` por la versión real, por ejemplo `dist\GinnyFrontend-0.2.2.apk`.) El script instala el APK encima de
   la versión anterior, conservando los datos, y abre la app. No hace falta tener `adb` en el PATH: usa el del SDK
   (`D:\Android\Sdk\platform-tools`). Si el dispositivo no aparece, comprueba la conexión con `adb devices`.
   Para una prueba rápida de desarrollo sin firmar, `.\debug.bat` compila e instala la variante debug.

6. **Subir el código y la etiqueta** (solo después de probar):
   ```powershell
   git push origin main
   git tag -a vX.Y.Z -m "Ginny Frontend X.Y.Z"
   git push origin vX.Y.Z
   ```

7. **Publicar el release en GitHub** con el APK adjunto:
   ```powershell
   gh release create vX.Y.Z dist\GinnyFrontend-X.Y.Z.apk --title "Ginny Frontend X.Y.Z" --notes "Notas…"
   ```
   Para notas más largas: `--notes-file notas.md`. Las notas resumen lo cambiado desde la versión anterior
   (`git log vANTERIOR..HEAD --oneline` ayuda).

## Comprobación final

`gh release list` debe mostrar la versión como *Latest*, y el APK descargado debe llamarse `GinnyFrontend-X.Y.Z.apk`.

## Si algo sale mal

- **Se subió una etiqueta equivocada:** `git tag -d vX.Y.Z` y `git push origin :refs/tags/vX.Y.Z`; luego repetir el paso 6.
- **El release salió con el APK equivocado:** `gh release upload vX.Y.Z dist\GinnyFrontend-X.Y.Z.apk --clobber`.
- **Falta la firma (`keystore.properties`):** `assembleRelease` compila igual pero sin firmar y el dispositivo rechaza
  instalarlo sobre una versión firmada; restaura el `.jks` y vuelve a compilar.

## Archivos relacionados

| Archivo | Para qué |
|---|---|
| `bump-version.bat` / `bump-version.ps1` | Sube la versión (patch por defecto) |
| `build-release.bat` | Compila el APK de release y lo copia a `dist\` |
| `install-apk.bat <ruta>` | Instala un APK (con `adb install -r`, usando el `adb` del SDK) y abre la app |
| `debug.bat` | Instala la variante debug en el dispositivo y abre la app |
| `app/build.gradle.kts` | Donde viven `versionCode` y `versionName` |
