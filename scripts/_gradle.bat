@echo off
setlocal EnableExtensions
set "ROOT=%~dp0.."
if not exist "%ROOT%\gradlew.bat" (
    echo [Quest Plus] gradlew.bat not found. Keep the scripts inside the repository.
    exit /b 1
)
if defined JAVA_HOME (
    if not exist "%JAVA_HOME%\bin\java.exe" (
        echo [Quest Plus] JAVA_HOME does not point to a valid JDK: %JAVA_HOME%
        exit /b 1
    )
    "%JAVA_HOME%\bin\java.exe" -version
) else (
    where java >nul 2>&1
    if errorlevel 1 (
        echo [Quest Plus] Java not found. Install JDK 25 and set JAVA_HOME or PATH.
        exit /b 1
    )
    java -version
)
echo.
echo [Quest Plus] Required: JDK 25, Minecraft 26.3 / Fabric.
echo [Quest Plus] Gradle task(s): %*
cd /d "%ROOT%"
call "%ROOT%\gradlew.bat" --console=plain %*
set "RESULT=%ERRORLEVEL%"
if not "%RESULT%"=="0" (
    echo.
    echo [Quest Plus] Gradle failed with exit code %RESULT%.
    echo [Quest Plus] Check Java 25 and the first-run dependency downloads.
)
exit /b %RESULT%
