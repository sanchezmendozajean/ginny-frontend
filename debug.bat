@echo off
rem Compila la variante debug, la instala en el dispositivo conectado por adb y abre la app.
rem Alternativa a Android Studio: no hace falta abrirlo ni esperar su sincronizacion de Gradle.
setlocal
cd /d "%~dp0"

rem adb: el del PATH si existe; si no, el del SDK que indica local.properties.
where adb >nul 2>nul
if errorlevel 1 set "PATH=%PATH%;D:\Android\Sdk\platform-tools"

call "%~dp0gradlew.bat" installDebug
if errorlevel 1 (
    echo.
    echo La compilacion o la instalacion fallo.
    exit /b 1
)

adb shell am start -n com.ginny.frontend/.MainActivity
