@echo off
setlocal EnableExtensions
cd /d "%~dp0"
echo ===================================================
echo  Quest Plus - Minecraft dev client (mod loaded)
echo ===================================================
echo.
if /I "%QUESTPLUS_SKIP_TEST_ASSETS%"=="1" goto RUN_CLIENT
where node.exe >nul 2>&1
if errorlevel 1 (
 echo [ERREUR] Node.js 22+ necessaire pour les mods et shaders de test.
 echo [INFO] Pour ignorer les packs : set QUESTPLUS_SKIP_TEST_ASSETS=1
 if /I not "%~1"=="--nopause" pause
 exit /b 1
)
echo [QuestPlus] Verification des shaders, Punchy et animations de test...
rem The script locates the repository from its own location.
rem Never pass quoted %%~dp0 (trailing backslash escapes the closing quote in Node on Windows).
node "%~dp0scripts\install-client-test-assets.mjs"
if errorlevel 1 (
 echo [ERREUR] Certains assets de test n'ont pas pu etre installes.
 echo [INFO] Pour lancer sans ces assets : set QUESTPLUS_SKIP_TEST_ASSETS=1
 if /I not "%~1"=="--nopause" pause
 exit /b 1
)
:RUN_CLIENT
echo [QuestPlus] Lancement du client depuis les sources Gradle actuelles.
call "%~dp0scripts\_gradle.bat" runClient
set "STATUS=%ERRORLEVEL%"
echo.
if not "%STATUS%"=="0" echo [Quest Plus] Echec : consulter les logs Minecraft / Gradle.
if "%STATUS%"=="0" echo [Quest Plus] Client arrete normalement.
echo.
if /I not "%~1"=="--nopause" pause
exit /b %STATUS%
