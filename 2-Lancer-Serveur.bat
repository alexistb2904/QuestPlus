@echo off
setlocal EnableExtensions
cd /d "%~dp0"
echo ===================================================
echo  Quest Plus - Dedicated local test server
echo ===================================================
echo.
if not exist "run\server" mkdir "run\server"
if not exist "run\server" (
    echo [Quest Plus] Cannot create run\server.
    pause
    exit /b 1
)
if exist "run\server\eula.txt" (
    findstr /I /X /C:"eula=true" "run\server\eula.txt" >nul 2>&1
    if not errorlevel 1 goto EULA_ACCEPTED
)
echo Minecraft EULA: https://aka.ms/MinecraftEULA
echo You must read and accept the EULA to start the dedicated server.
set "EULA_ANSWER="
set /p "EULA_ANSWER=Type YES to accept (anything else cancels): "
if /I not "%EULA_ANSWER%"=="YES" (
    echo [Quest Plus] EULA not accepted. Server not started.
    pause
    exit /b 1
)
> "run\server\eula.txt" echo eula=true
:EULA_ACCEPTED
if not exist "run\server\server.properties" (
    >"run\server\server.properties" (
        echo server-ip=127.0.0.1
        echo server-port=25565
        echo online-mode=false
        echo motd=Quest Plus local development server
    )
    echo [Quest Plus] Created localhost-only offline test configuration.
)
echo [Quest Plus] Connect to localhost:25565 from the development client.
echo [Quest Plus] Never expose this offline-mode server to the Internet.
echo.
rem Fabric Loom dev runs use the rebuilt project classpath, not a stale JAR in mods.
if exist "run\server\mods\*questplus*.jar" (
 echo [ERREUR] Ancien QuestPlus JAR trouve dans run\server\mods.
 echo [ERREUR] Retirez le JAR manuellement pour eviter le chargement en double.
 dir /b "run\server\mods\*questplus*.jar"
 if /I not "%~1"=="--nopause" pause
 exit /b 1
)
echo [QuestPlus] Recompilation forcee du mod depuis les sources actuelles...
call "%~dp0scripts\_gradle.bat" --rerun-tasks jar
if errorlevel 1 (
 echo [ERREUR] Build echoue. Le serveur ne sera pas lance avec un ancien mod.
 if /I not "%~1"=="--nopause" pause
 exit /b 1
)
echo [QuestPlus] JAR reconstruit. Lancement Gradle avec les classes actuelles.
echo [INFO] Le monde conserve ses anciennes definitions. Publiez de nouvelles quetes si necessaire.
call "%~dp0scripts\_gradle.bat" runServer
set "STATUS=%ERRORLEVEL%"
echo.
if not "%STATUS%"=="0" echo [Quest Plus] Server stopped with an error. Check the logs above.
if "%STATUS%"=="0" echo [Quest Plus] Server stopped.
echo.
if /I not "%~1"=="--nopause" pause
exit /b %STATUS%
