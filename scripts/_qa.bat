@echo off
setlocal EnableExtensions DisableDelayedExpansion
for %%I in ("%~dp0..") do set "ROOT=%%~fI"
set "ACTION=%~1"
if /I "%ACTION%"=="doctor" goto DOCTOR
if /I "%ACTION%"=="setup" goto SETUP
if /I "%ACTION%"=="studio-dev" goto STUDIO_DEV
if /I "%ACTION%"=="studio-test" goto STUDIO_TEST
if /I "%ACTION%"=="studio-build" goto STUDIO_BUILD
if /I "%ACTION%"=="studio-preview" goto STUDIO_PREVIEW
if /I "%ACTION%"=="studio-ci" goto STUDIO_CI
if /I "%ACTION%"=="api-test" goto API_TEST
if /I "%ACTION%"=="api-run" goto API_RUN
if /I "%ACTION%"=="api-health" goto API_HEALTH
if /I "%ACTION%"=="web-all" goto WEB_ALL
if /I "%ACTION%"=="java-compile" goto JAVA_COMPILE
if /I "%ACTION%"=="java-test" goto JAVA_TEST
if /I "%ACTION%"=="java-build" goto JAVA_BUILD
if /I "%ACTION%"=="server-test" goto SERVER_TEST
if /I "%ACTION%"=="client-test" goto CLIENT_TEST
if /I "%ACTION%"=="client-assets" goto CLIENT_ASSETS
if /I "%ACTION%"=="docker-build" goto DOCKER_BUILD
if /I "%ACTION%"=="all" goto ALL
if /I "%ACTION%"=="all-gui" goto ALL_GUI
if /I "%ACTION%"=="reports" goto REPORTS
echo [QuestPlus] Unknown action. Run dev.bat for available commands.
exit /b 2

:CHECK_NODE
where node.exe >nul 2>&1
if errorlevel 1 (
 echo [ERROR] Node.js 22+ not found. https://nodejs.org/
 exit /b 1
)
where npm.cmd >nul 2>&1
if errorlevel 1 (
 echo [ERROR] npm not found.
 exit /b 1
)
set "NODE_MAJOR="
for /f "delims=" %%V in ('node -p "process.versions.node.split('.')[0]" 2^>nul') do set "NODE_MAJOR=%%V"
if not defined NODE_MAJOR (
 echo [ERROR] Unable to detect the Node.js version.
 exit /b 1
)
if %NODE_MAJOR% LSS 22 (
 echo [ERROR] Node.js %NODE_MAJOR% detected. Version 22 or newer is required.
 exit /b 1
)
exit /b 0

:EDITOR_INSTALL
if exist "%ROOT%\web\editor\node_modules\.bin\vite.cmd" exit /b 0
echo [QuestPlus Web] Installing React dependencies...
pushd "%ROOT%\web\editor" || exit /b 1
call npm ci --no-audit --no-fund
set "RESULT=%ERRORLEVEL%"
popd
exit /b %RESULT%

:EDITOR_TASK
call :CHECK_NODE
if errorlevel 1 exit /b 1
call :EDITOR_INSTALL
if errorlevel 1 exit /b 1
pushd "%ROOT%\web\editor" || exit /b 1
call npm run %~1
set "RESULT=%ERRORLEVEL%"
popd
exit /b %RESULT%

:API_INSTALL
if exist "%ROOT%\web\api\node_modules\json5\package.json" exit /b 0
pushd "%ROOT%\web\api" || exit /b 1
call npm ci --no-audit --no-fund
set "RESULT=%ERRORLEVEL%"
popd
exit /b %RESULT%

:API_TASK
call :CHECK_NODE
if errorlevel 1 exit /b 1
call :API_INSTALL
if errorlevel 1 exit /b 1
pushd "%ROOT%\web\api" || exit /b 1
call npm run %~1
set "RESULT=%ERRORLEVEL%"
popd
exit /b %RESULT%

:JAVA_COMPILE
echo [Minecraft] Compiling Java and Fabric resources...
call "%ROOT%\scripts\_gradle.bat" compileJava processResources
exit /b %ERRORLEVEL%

:JAVA_TEST
echo [Minecraft] JUnit - unit tests only...
call "%ROOT%\scripts\_gradle.bat" test
exit /b %ERRORLEVEL%

:JAVA_BUILD
echo [Minecraft] Full build, JUnit tests and JAR...
call "%ROOT%\scripts\_gradle.bat" build
exit /b %ERRORLEVEL%

:SERVER_TEST
echo [Minecraft] Server GameTests and save verification...
call "%ROOT%\scripts\_gradle.bat" integrationTest
exit /b %ERRORLEVEL%

:CLIENT_TEST
echo [Minecraft] Client GameTests require a graphical window.
call "%ROOT%\scripts\_gradle.bat" runClientGameTest
exit /b %ERRORLEVEL%

:CLIENT_ASSETS
call :CHECK_NODE
if errorlevel 1 exit /b 1
echo [QuestPlus] Updating test shaders and mods (26.3)...
node "%ROOT%\scripts\install-client-test-assets.mjs" --update
exit /b %ERRORLEVEL%

:STUDIO_DEV
call :CHECK_NODE
if errorlevel 1 exit /b 1
call :EDITOR_INSTALL
if errorlevel 1 exit /b 1
echo [Quest Studio] Vite / HMR : http://localhost:5173/
pushd "%ROOT%\web\editor" || exit /b 1
start "" "http://localhost:5173/"
call npm run dev
set "RESULT=%ERRORLEVEL%"
popd
exit /b %RESULT%

:STUDIO_TEST
echo [Quest Studio] Node test suite...
call :EDITOR_TASK test
exit /b %ERRORLEVEL%

:STUDIO_BUILD
echo [Quest Studio] TypeScript check...
call :EDITOR_TASK typecheck
if errorlevel 1 exit /b 1
echo [Quest Studio] Vite production build...
call :EDITOR_TASK build
exit /b %ERRORLEVEL%

:STUDIO_PREVIEW
call :STUDIO_BUILD
if errorlevel 1 exit /b 1
echo [Quest Studio] Production preview : http://localhost:4173/
pushd "%ROOT%\web\editor" || exit /b 1
start "" "http://localhost:4173/"
call npm run preview -- --host 127.0.0.1
set "RESULT=%ERRORLEVEL%"
popd
exit /b %RESULT%

:STUDIO_CI
call :CHECK_NODE
if errorlevel 1 exit /b 1
pushd "%ROOT%\web\editor" || exit /b 1
echo [Quest Studio] Reinstalling node_modules with npm ci...
call npm ci --no-audit --no-fund
set "RESULT=%ERRORLEVEL%"
popd
exit /b %RESULT%

:API_TEST
echo [QuestPlus Web] API, security, session and architecture tests...
call :API_TASK test
exit /b %ERRORLEVEL%

:SETUP
call :CHECK_NODE
if errorlevel 1 exit /b 1
echo [QuestPlus Web] Setting up localhost. No public server is configured.
node "%ROOT%\web\api\tools\setup-local.mjs" "%ROOT%"
exit /b %ERRORLEVEL%

:API_RUN
call :API_INSTALL
if errorlevel 1 exit /b 1
call :STUDIO_BUILD
if errorlevel 1 exit /b 1
call :SETUP
if errorlevel 1 exit /b 1
set "HUB_PUBLIC_URL=http://localhost:8787"
set "STUDIO_ORIGIN=http://localhost:8787"
set "STUDIO_URL=http://localhost:8787/"
set "HUB_BIND=127.0.0.1"
set "HUB_DATA_DIR=%ROOT%\run\questplus-web-packs"
set "WEB_STATIC_DIR=%ROOT%\web\editor\dist"
echo [QuestPlus Web] Local React + API server : http://localhost:8787/
echo [QuestPlus Web] Press Ctrl+C to stop.
pushd "%ROOT%\web\api" || exit /b 1
if not "%QUESTPLUS_NO_BROWSER%"=="1" start "" "http://localhost:8787/"
node server.mjs
set "RESULT=%ERRORLEVEL%"
popd
exit /b %RESULT%

:API_HEALTH
call :CHECK_NODE
if errorlevel 1 exit /b 1
echo [QuestPlus Web] GET /health on 127.0.0.1:8787
node -e "fetch('http://127.0.0.1:8787/health').then(async r=>{console.log('HTTP '+r.status,await r.text());if(!r.ok)process.exitCode=1}).catch(e=>{console.error('API unavailable: '+e.message);process.exitCode=1})"
exit /b %ERRORLEVEL%

:DOCTOR
echo ==============================================================
echo   QUESTPLUS - DEVELOPMENT PREREQUISITES
echo ==============================================================
set "FAILURES=0"
if exist "%ROOT%\mod\gradlew.bat" (echo [OK] Gradle Wrapper) else (echo [ERROR] Gradle wrapper missing&set /a FAILURES+=1)
if exist "%ROOT%\mod\client\src\main\resources\questplus.client.mixins.json" (echo [OK] Client sources) else (echo [ERROR] Client sources missing&set /a FAILURES+=1)
if exist "%ROOT%\mod\server\src\main\resources\fabric.mod.json" (echo [OK] Server sources) else (echo [ERROR] Server sources missing&set /a FAILURES+=1)
if exist "%ROOT%\web\editor\package-lock.json" (echo [OK] React / lockfile) else (echo [ERROR] React missing&set /a FAILURES+=1)
if exist "%ROOT%\web\api\server.mjs" (echo [OK] API web) else (echo [ERROR] Web API missing&set /a FAILURES+=1)
call :CHECK_NODE
if errorlevel 1 (set /a FAILURES+=1) else (
 node --version
 call npm --version
)
where powershell.exe >nul 2>&1
if errorlevel 1 (
 echo [ERROR] Windows PowerShell missing.
 set /a FAILURES+=1
) else (
 set "JDK25_HOME="
 for /f "usebackq delims=" %%J in (`powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%ROOT%\scripts\Find-Java25.ps1"`) do if not defined JDK25_HOME set "JDK25_HOME=%%J"
 if defined JDK25_HOME (echo [OK] Java 25 detected) else (echo [ERROR] JDK 25 not found&set /a FAILURES+=1)
)
where docker.exe >nul 2>&1
if errorlevel 1 (echo [INFO] Docker missing - optional) else (echo [OK] Docker available)
echo.
if "%FAILURES%"=="0" (echo [OK] All required prerequisites are available.) else (echo [ERROR] %FAILURES% prerequisites missing.)
exit /b %FAILURES%

:WEB_ALL
call :STUDIO_TEST
if errorlevel 1 exit /b 1
call :STUDIO_BUILD
if errorlevel 1 exit /b 1
call :API_TEST
exit /b %ERRORLEVEL%

:ALL
set "INCLUDE_GUI=0"
goto SUITE
:ALL_GUI
set "INCLUDE_GUI=1"
goto SUITE
:SUITE
if not exist "%ROOT%\run" mkdir "%ROOT%\run"
set "SUMMARY=%ROOT%\run\questplus-test-summary.txt"
> "%SUMMARY%" echo QuestPlus automated validation
>>"%SUMMARY%" echo %DATE% %TIME%
set "FAILED=0"
echo.
echo [SUITE] Running automated checks...
call :STEP "Java compile + JUnit + JAR" JAVA_BUILD
call :STEP "Server GameTests" SERVER_TEST
call :STEP "React tests" STUDIO_TEST
call :STEP "Typecheck + build React" STUDIO_BUILD
call :STEP "API / architecture" API_TEST
if "%INCLUDE_GUI%"=="1" call :STEP "Minecraft client GameTests (GUI)" CLIENT_TEST
echo.
type "%SUMMARY%"
echo.
if "%INCLUDE_GUI%"=="0" echo [INFO] Client GUI GameTests are excluded; use client-test to run them.
if "%FAILED%"=="0" (
 echo [OK] All selected checks passed.
 exit /b 0
)
echo [ERROR] %FAILED% check(s) failed. See logs above.
exit /b 1

:STEP
echo.
echo --------------------------------------------------------------
echo [SUITE] %~1
echo --------------------------------------------------------------
call :%~2
set "STEP_STATUS=%ERRORLEVEL%"
if "%STEP_STATUS%"=="0" (
 echo [OK] %~1
 >>"%SUMMARY%" echo PASS - %~1
) else (
 echo [FAIL %STEP_STATUS%] %~1
 >>"%SUMMARY%" echo FAIL %STEP_STATUS% - %~1
 set /a FAILED+=1 >nul
)
exit /b 0

:DOCKER_BUILD
where docker.exe >nul 2>&1
if errorlevel 1 (
 echo [ERROR] Docker Desktop not found.
 exit /b 1
)
call :SETUP
if errorlevel 1 exit /b 1
set "HUB_PUBLIC_URL=http://localhost:8787"
set "STUDIO_ORIGIN=http://localhost:8787"
set "STUDIO_URL=http://localhost:8787/"
echo [Docker] Building the web image locally.
call docker compose -f "%ROOT%\web\api\compose.yml" build
exit /b %ERRORLEVEL%

:REPORTS
echo Available reports :
if exist "%ROOT%\mod\build\reports\tests\test\index.html" (
 echo - Java JUnit : build\reports\tests\test\index.html
 start "" "%ROOT%\mod\build\reports\tests\test\index.html"
)
if exist "%ROOT%\mod\build\gametest-results.xml" echo - GameTests : build\gametest-results.xml
if exist "%ROOT%\run\questplus-test-summary.txt" (
 echo - Summary : run\questplus-test-summary.txt
 start "" notepad.exe "%ROOT%\run\questplus-test-summary.txt"
)
if exist "%ROOT%\mod\build\reports" (
 start "" explorer.exe "%ROOT%\mod\build\reports"
) else (
 echo [INFO] No Gradle reports available yet.
)
exit /b 0
