@echo off
setlocal EnableExtensions DisableDelayedExpansion
for %%I in ("%~dp0..") do set "ROOT=%%~fI"
if not exist "%ROOT%\mod\gradlew.bat" (
    echo [Quest Plus] Gradle wrapper not found in mod/.
    exit /b 1
)

where powershell.exe >nul 2>&1
if errorlevel 1 (
    echo [Quest Plus] Windows PowerShell is required to detect Java 25.
    exit /b 1
)

set "JDK25_HOME="
for /f "usebackq delims=" %%J in (`powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%ROOT%\scripts\Find-Java25.ps1"`) do (
    if not defined JDK25_HOME set "JDK25_HOME=%%J"
)

if not defined JDK25_HOME (
    echo.
    echo [Quest Plus] ERROR: JDK 25 not found.
    echo [Quest Plus] Minecraft 26.3 requires Java 25.
    echo [Quest Plus] Install a Java 25 JDK,
    echo [Quest Plus] or install it with:
    echo     winget install -e --id EclipseAdoptium.Temurin.25.JDK
    echo.
    echo [Quest Plus] If Java 25 is installed elsewhere,
    echo [Quest Plus] set QUESTPLUS_JAVA_HOME to the JDK directory.
    echo [Quest Plus] Your global JAVA_HOME remains unchanged.
    exit /b 1
)

rem Override Java ONLY for this script and the Gradle child process.
set "JAVA_HOME=%JDK25_HOME%"
set "PATH=%JAVA_HOME%\bin;%PATH%"
echo [Quest Plus] JDK 25 found : %JAVA_HOME%
"%JAVA_HOME%\bin\java.exe" -version
"%JAVA_HOME%\bin\javac.exe" -version
echo.
echo [Quest Plus] Minecraft 26.3 / Fabric - Gradle tasks : %*
cd /d "%ROOT%\mod"
call "%ROOT%\mod\gradlew.bat" --console=plain %*
set "RESULT=%ERRORLEVEL%"
if not "%RESULT%"=="0" (
    echo.
    echo [Quest Plus] Gradle failed with exit code %RESULT%.
    echo [Quest Plus] See the errors above.
)
exit /b %RESULT%
