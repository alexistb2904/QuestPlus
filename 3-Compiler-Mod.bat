@echo off
setlocal EnableExtensions DisableDelayedExpansion
cd /d "%~dp0"
echo ==============================================================
echo   QuestPlus - Build Fabric + JUnit
echo ==============================================================
echo.
call "%~dp0scripts\_qa.bat" java-build
set "STATUS=%ERRORLEVEL%"
echo.
if "%STATUS%"=="0" (echo [QuestPlus] Termine avec succes.) else (echo [QuestPlus] Echec - code %STATUS%.)
if /I not "%~1"=="--nopause" pause
exit /b %STATUS%
