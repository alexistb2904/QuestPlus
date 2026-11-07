@echo off
setlocal EnableExtensions
for %%I in ("%~dp0..") do set "ROOT=%%~fI"
cd /d "%ROOT%"
echo ===================================================
echo  Quest Plus - Minecraft dev client (mod loaded)
echo ===================================================
echo.
if /I "%QUESTPLUS_SKIP_TEST_ASSETS%"=="1" goto RUN_CLIENT
where node.exe >nul 2>&1
if errorlevel 1 (
 echo [ERROR] Node.js 22+ is required for test mods and shaders.
 echo [INFO] To skip test assets: set QUESTPLUS_SKIP_TEST_ASSETS=1
 if /I not "%~1"=="--nopause" pause
 exit /b 1
)
echo [QuestPlus] Checking test shaders, Punchy and animations...
rem The script locates the repository from its own location.
rem Never pass quoted %%~dp0 (trailing backslash escapes the closing quote in Node on Windows).
node "%ROOT%\scripts\install-client-test-assets.mjs"
if errorlevel 1 (
 echo [ERROR] Some test assets could not be installed.
 echo [INFO] To launch without test assets: set QUESTPLUS_SKIP_TEST_ASSETS=1
 if /I not "%~1"=="--nopause" pause
 exit /b 1
)
:RUN_CLIENT
call :CLIENT_WEB
if errorlevel 1 echo [WARNING] Quest Studio is unavailable. See run\questplus-client-web.log or run dev.bat api, then retry /questplus editor.
echo [QuestPlus] Launching the client from the current Gradle sources.
call "%ROOT%\scripts\_gradle.bat" runClient
set "STATUS=%ERRORLEVEL%"
echo.
if not "%STATUS%"=="0" echo [Quest Plus] Failed: check Minecraft and Gradle logs.
if "%STATUS%"=="0" echo [Quest Plus] Client stopped normally.
echo.
if /I not "%~1"=="--nopause" pause
exit /b %STATUS%

:CLIENT_WEB
where node.exe >nul 2>&1
if errorlevel 1 exit /b 1
call "%ROOT%\scripts\_qa.bat" setup
if errorlevel 1 exit /b 1
node "%ROOT%\scripts\local-stack.mjs" web-ready "%ROOT%"
if not errorlevel 1 exit /b 0
if errorlevel 2 exit /b 1
echo [QuestPlus] Starting Quest Studio for solo editing...
set "QUESTPLUS_NO_BROWSER=1"
start "" /b "%ComSpec%" /d /c call "%ROOT%\scripts\_qa.bat" api-run > "%ROOT%\run\questplus-client-web.log" 2>&1
node "%ROOT%\scripts\local-stack.mjs" wait-web "%ROOT%"
exit /b %ERRORLEVEL%
