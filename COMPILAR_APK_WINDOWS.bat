@echo off
setlocal
cd /d "%~dp0"
set "VER=desconocida"
if exist "VERSION" set /p VER=<VERSION
echo.
echo ALEYON BRIDGE %VER% - COMPILAR APK
echo CARPETA: %~dp0
echo.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\build_windows.ps1"
set ERR=%ERRORLEVEL%
echo.
if not "%ERR%"=="0" (
  echo La compilacion termino con error.
) else (
  echo Compilacion terminada correctamente.
)
echo.
pause
exit /b %ERR%
