@echo off
setlocal EnableExtensions
cd /d "%~dp0"
echo ===================================================
echo  Quest Plus - Minecraft dev client (mod loaded)
echo ===================================================
echo.
call "%~dp0scripts\_gradle.bat" runClient
set "STATUS=%ERRORLEVEL%"
echo.
if not "%STATUS%"=="0" echo [Quest Plus] Echec : consulter les logs Minecraft / Gradle.
if "%STATUS%"=="0" echo [Quest Plus] Client arrete normalement.
echo.
pause
exit /b %STATUS%
