@echo off
setlocal EnableExtensions DisableDelayedExpansion
cd /d "%~dp0"
echo ===================================================
echo   Quest Plus - Installer Eclipse Temurin JDK 25
echo ===================================================
echo.
echo Ceci installe un JDK sur Windows via le gestionnaire officiel winget.
echo Editeur : Eclipse Adoptium
echo Paquet : EclipseAdoptium.Temurin.25.JDK
echo Plus d'informations : https://adoptium.net/installation/
echo.
where winget.exe >nul 2>&1
if errorlevel 1 (
    echo [Quest Plus] winget est introuvable. Installez le JDK 25 depuis :
    echo https://adoptium.net/installation/
    echo Puis relancez le menu de test.
    pause
    exit /b 1
)
set "CONFIRM="
set /p "CONFIRM=Installer Java 25 maintenant ? Tapez O puis Entree : "
if /I not "%CONFIRM%"=="O" (
    echo Installation annulee.
    pause
    exit /b 0
)
winget install --exact --id EclipseAdoptium.Temurin.25.JDK --source winget --accept-source-agreements --accept-package-agreements
set "STATUS=%ERRORLEVEL%"
echo.
if "%STATUS%"=="0" (
    echo [Quest Plus] Installation terminee.
    echo [Quest Plus] Relancez 1-Lancer-Client.bat.
    echo [Quest Plus] Le lanceur detectera Java 25 sans changer Java 17 globalement.
) else (
    echo [Quest Plus] Installation non terminee (code %STATUS%).
    echo [Quest Plus] Consultez les messages de winget ci-dessus.
)
echo.
pause
exit /b %STATUS%
