@echo off
rem Sube la version de la app (versionCode +1 y versionName). Sin argumento: patch.
rem   bump-version.bat          0.2.1 -> 0.2.2
rem   bump-version.bat minor    0.2.1 -> 0.3.0
rem   bump-version.bat major    0.2.1 -> 1.0.0
rem Edita app\build.gradle.kts y los ejemplos de README.md; no hace commit ni compila.
setlocal
cd /d "%~dp0"

set "KIND=%~1"
if "%KIND%"=="" set "KIND=patch"

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0bump-version.ps1" -Kind %KIND%
if errorlevel 1 (
    echo.
    echo No se pudo cambiar la version.
    exit /b 1
)
