@echo off
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"
echo.
echo ALEYON BRIDGE - INSTALAR APK
echo.

set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if not exist "%ADB%" (
  echo ERROR: No encontre adb en %ADB%
  pause
  exit /b 1
)

set "APK="
if exist "Aleyon-Bridge-v0.4.0-alpha1-debug.apk" set "APK=%CD%\Aleyon-Bridge-v0.4.0-alpha1-debug.apk"
if not defined APK (
  for /f "delims=" %%F in ('dir /b /a-d /o-d "Aleyon-Bridge-v*-debug.apk" 2^>nul') do (
    set "APK=%CD%\%%F"
    goto :found
  )
)
:found
if not defined APK (
  echo ERROR: No encontre APK. Ejecuta COMPILAR_APK_WINDOWS.bat
  pause
  exit /b 1
)

echo APK: %APK%
"%ADB%" devices
echo.
echo Acepta "Permitir depuracion USB" en el telefono si aparece.
"%ADB%" install -r "%APK%"
set ERR=%ERRORLEVEL%
echo.
if "%ERR%"=="0" (
  echo INSTALACION COMPLETADA.
  echo Abre Aleyon Bridge y habilita su automatizacion en Accesibilidad.
) else (
  echo ERROR adb: %ERR%
)
pause
exit /b %ERR%
