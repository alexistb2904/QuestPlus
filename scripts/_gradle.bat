@echo off
setlocal EnableExtensions DisableDelayedExpansion
for %%I in ("%~dp0..") do set "ROOT=%%~fI"
if not exist "%ROOT%\gradlew.bat" (
    echo [Quest Plus] gradlew.bat introuvable. Conservez les scripts dans le depot.
    exit /b 1
)

where powershell.exe >nul 2>&1
if errorlevel 1 (
    echo [Quest Plus] Windows PowerShell est necessaire pour detecter Java 25.
    exit /b 1
)

set "JDK25_HOME="
for /f "usebackq delims=" %%J in (`powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%ROOT%\scripts\Find-Java25.ps1"`) do (
    if not defined JDK25_HOME set "JDK25_HOME=%%J"
)

if not defined JDK25_HOME (
    echo.
    echo [Quest Plus] ERREUR : JDK 25 introuvable.
    echo [Quest Plus] Le Java 17 de votre systeme ne suffit pas pour Minecraft 26.3.
    echo [Quest Plus] Lancez 6-Installer-Java-25.bat depuis le menu principal,
    echo [Quest Plus] ou installez JDK 25 avec :
    echo     winget install -e --id EclipseAdoptium.Temurin.25.JDK
    echo.
    echo [Quest Plus] Si Java 25 est deja installe dans un chemin particulier,
    echo [Quest Plus] configurez QUESTPLUS_JAVA_HOME avec le dossier du JDK.
    echo [Quest Plus] Votre JAVA_HOME et votre Java par defaut restent inchanges.
    exit /b 1
)

rem Override Java ONLY for this script and the Gradle child process.
set "JAVA_HOME=%JDK25_HOME%"
set "PATH=%JAVA_HOME%\bin;%PATH%"
echo [Quest Plus] JDK 25 trouve : %JAVA_HOME%
"%JAVA_HOME%\bin\java.exe" -version
"%JAVA_HOME%\bin\javac.exe" -version
echo.
echo [Quest Plus] Minecraft 26.3 / Fabric - taches Gradle : %*
cd /d "%ROOT%"
call "%ROOT%\gradlew.bat" --console=plain %*
set "RESULT=%ERRORLEVEL%"
if not "%RESULT%"=="0" (
    echo.
    echo [Quest Plus] Gradle a echoue avec le code %RESULT%.
    echo [Quest Plus] Consultez les erreurs ci-dessus.
)
exit /b %RESULT%
