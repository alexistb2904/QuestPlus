@echo off
setlocal EnableExtensions DisableDelayedExpansion
for %%I in ("%~dp0..") do set "ROOT=%%~fI"
cd /d "%ROOT%"
set "ROLE=%~1"
if /I "%ROLE%"=="web" goto WEB
if /I "%ROLE%"=="server" goto SERVER
if /I "%ROLE%"=="client" goto CLIENT
echo [QuestPlus] Unknown component : %ROLE%
exit /b 2

:WEB
title QuestPlus - React and API
echo [WEB] Building React and starting the API on localhost:8787.
set "QUESTPLUS_NO_BROWSER=1"
call "%ROOT%\scripts\_qa.bat" api-run
goto DONE

:SERVER
title QuestPlus - Minecraft Fabric server
echo [MINECRAFT SERVER] The server will be available on localhost:25565.
call "%ROOT%\scripts\dev-server.bat" --nopause
goto DONE

:CLIENT
title QuestPlus - Minecraft Fabric client
echo [MINECRAFT CLIENT] Starting the development client.
call "%ROOT%\scripts\dev-client.bat" --nopause
goto DONE

:DONE
set "RESULT=%ERRORLEVEL%"
echo.
if not "%RESULT%"=="0" echo [QuestPlus] Component exited with error : %RESULT%
if "%RESULT%"=="0" echo [QuestPlus] Component stopped.
echo Close this window after checking the logs.
exit /b %RESULT%
