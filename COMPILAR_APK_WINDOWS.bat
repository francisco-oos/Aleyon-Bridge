@echo off
setlocal
cd /d "%~dp0"
echo.
echo ALEYON BRIDGE v0.4 - COMPILAR APK
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
