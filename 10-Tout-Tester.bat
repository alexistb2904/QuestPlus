@echo off
setlocal EnableExtensions DisableDelayedExpansion
cd /d "%~dp0"
echo ==============================================================
echo    QuestPlus - Validation complete de l'architecture
echo ==============================================================
echo.
if /I "%~1"=="gui" (
 echo [QuestPlus] Mode avec tests client GUI active.
 call "%~dp0scripts\_qa.bat" all-gui
) else (
 echo [QuestPlus] Mode automatique sans tests GUI.
 call "%~dp0scripts\_qa.bat" all
)
set "STATUS=%ERRORLEVEL%"
echo.
if "%STATUS%"=="0" (echo [QuestPlus] SUITE OK) else (echo [QuestPlus] SUITE EN ECHEC - consultez run\questplus-test-summary.txt)
if /I not "%~1"=="--nopause" pause
exit /b %STATUS%
