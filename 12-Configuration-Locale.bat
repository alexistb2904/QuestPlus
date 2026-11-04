@echo off
setlocal EnableExtensions DisableDelayedExpansion
cd /d "%~dp0"
echo ==============================================================
echo   QuestPlus - Configuration locale Minecraft + API
echo ==============================================================
echo.
call "%~dp0scripts\_qa.bat" setup
set "STATUS=%ERRORLEVEL%"
echo.
if "%STATUS%"=="0" (echo [QuestPlus] Termine avec succes.) else (echo [QuestPlus] Echec - code %STATUS%.)
if /I not "%~1"=="--nopause" pause
exit /b %STATUS%
