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
echo [QuestPlus] Launching the client from the current Gradle sources.
call "%ROOT%\scripts\_gradle.bat" runClient
set "STATUS=%ERRORLEVEL%"
echo.
if not "%STATUS%"=="0" echo [Quest Plus] Failed: check Minecraft and Gradle logs.
if "%STATUS%"=="0" echo [Quest Plus] Client stopped normally.
echo.
if /I not "%~1"=="--nopause" pause
exit /b %STATUS%
