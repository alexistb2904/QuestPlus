@echo off
setlocal EnableExtensions DisableDelayedExpansion
for %%I in ("%~dp0..") do set "ROOT=%%~fI"
cd /d "%ROOT%"
set "ROOT=%CD%"
echo ==============================================================
echo  QUESTPLUS - LOCAL DEVELOPMENT STACK
echo  React + API / Fabric server / Minecraft client
echo ==============================================================
echo.
echo [1/6] Checking prerequisites...
call "%ROOT%\scripts\_qa.bat" doctor
if errorlevel 1 goto FAIL
echo.
echo [2/6] Creating / checking local configuration...
call "%ROOT%\scripts\_qa.bat" setup
if errorlevel 1 goto FAIL
node "%ROOT%\scripts\local-stack.mjs" preflight "%ROOT%"
if errorlevel 1 goto FAIL
echo.
echo [3/6] Starting web (React + API) in a new console...
start "QuestPlus WEB" /D "%ROOT%" "%ComSpec%" /k call "%ROOT%\scripts\_stack-component.bat" web
node "%ROOT%\scripts\local-stack.mjs" wait-web "%ROOT%"
if errorlevel 1 goto FAIL_WEB
echo.
echo [4/6] Starting Minecraft server in a new console...
echo If the Minecraft EULA has not been accepted, follow the
echo instructions in the SERVER window.
start "QuestPlus Minecraft SERVER" /D "%ROOT%" "%ComSpec%" /k call "%ROOT%\scripts\_stack-component.bat" server
node "%ROOT%\scripts\local-stack.mjs" wait-minecraft "%ROOT%"
if errorlevel 1 goto FAIL_SERVER
echo.
echo [5/6] Starting the Minecraft client...
start "QuestPlus Minecraft CLIENT" /D "%ROOT%" "%ComSpec%" /k call "%ROOT%\scripts\_stack-component.bat" client
echo.
echo [6/6] Opening Quest Studio...
start "" "http://localhost:8787/"
echo.
echo ==============================================================
echo  STACK STARTED (CLIENT IS OPENING)
echo ==============================================================
echo  Web / API      : http://localhost:8787/
echo  Minecraft      : localhost:25565
echo  OP command    : /questplus editor
echo.
echo The three processes run in separate consoles.
echo To stop: close the client, type "stop" in the server,
echo then press Ctrl+C in the web console.
echo ==============================================================
echo.
if /I not "%~1"=="--nopause" pause
exit /b 0

:FAIL_WEB
echo [ERROR] Web is not ready. Check the QuestPlus WEB console.
goto FAIL
:FAIL_SERVER
echo [ERROR] Minecraft server is not ready.
echo Check the EULA, Fabric logs and local port 25565.
goto FAIL
:FAIL
echo.
echo [QuestPlus] Startup interrupted. Already started components
echo may still be running in their own consoles.
if /I not "%~1"=="--nopause" pause
exit /b 1
