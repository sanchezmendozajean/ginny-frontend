@echo off
rem Instala un APK en el dispositivo conectado por adb (sobre la version anterior, conservando los datos) y abre la app.
rem Uso:  install-apk.bat <ruta-del-apk>
rem   install-apk.bat dist\GinnyFrontend-0.2.1.apk
setlocal
cd /d "%~dp0"

if "%~1"=="" (
    echo Falta la ruta del APK.
    echo Uso: install-apk.bat ^<ruta-del-apk^>
    echo Ej.: install-apk.bat dist\GinnyFrontend-0.2.1.apk
    exit /b 1
)
if not exist "%~1" (
    echo No existe el archivo: %~1
    exit /b 1
)

rem adb: el del PATH si existe; si no, el del SDK que indica local.properties.
where adb >nul 2>nul
if errorlevel 1 set "PATH=%PATH%;D:\Android\Sdk\platform-tools"

adb install -r "%~1"
if errorlevel 1 (
    echo.
    echo La instalacion fallo. Revisa que el dispositivo este conectado ^(adb devices^) y que el APK tenga la misma firma.
    exit /b 1
)

adb shell am start -n com.ginny.frontend/.MainActivity
