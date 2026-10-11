@echo off
rem Compila el APK de release y lo deja en dist\ (el APK queda con nombre GinnyFrontend-<versionName>.apk).
setlocal
cd /d "%~dp0"

call "%~dp0gradlew.bat" assembleRelease
if errorlevel 1 (
    echo.
    echo La compilacion fallo.
    exit /b 1
)

if not exist dist mkdir dist
del /q dist\*.apk 2>nul
copy /y app\build\outputs\apk\release\*.apk dist\ >nul
if errorlevel 1 (
    echo No se encontro el APK en app\build\outputs\apk\release\
    exit /b 1
)

echo.
echo Listo. APK en:
dir /b dist\*.apk
