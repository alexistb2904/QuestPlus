@echo off
setlocal EnableExtensions DisableDelayedExpansion
cd /d "%~dp0"

:MENU
cls
echo ===================================================
echo               QUEST PLUS - MENU TESTS
echo ===================================================
echo  1. Lancer Minecraft avec Quest Plus
echo  2. Lancer un serveur local de test
echo  3. Compiler le mod et lancer les tests unitaires
echo  4. Lancer les tests d'integration serveur
echo  5. Lancer les tests du client
echo  Q. Quitter
echo ===================================================
echo.
echo Tapez un chiffre puis appuyez sur Entree.
set "SELECTION="
set /p "SELECTION=Votre choix [1-5 ou Q] : "
if errorlevel 1 goto INPUT_ERROR
if /I "%SELECTION%"=="Q" goto END
if "%SELECTION%"=="1" goto CLIENT
if "%SELECTION%"=="2" goto SERVER
if "%SELECTION%"=="3" goto BUILD
if "%SELECTION%"=="4" goto SERVER_TEST
if "%SELECTION%"=="5" goto CLIENT_TEST
echo.
echo [Quest Plus] Choix invalide : choisissez 1, 2, 3, 4, 5 ou Q.
pause
goto MENU

:CLIENT
call "%~dp01-Lancer-Client.bat"
goto MENU

:SERVER
call "%~dp02-Lancer-Serveur.bat"
goto MENU

:BUILD
call "%~dp03-Compiler-Mod.bat"
goto MENU

:SERVER_TEST
call "%~dp04-Tests-Serveur.bat"
goto MENU

:CLIENT_TEST
call "%~dp05-Tests-Client.bat"
goto MENU

:INPUT_ERROR
echo.
echo [Quest Plus] Impossible de lire la saisie dans ce terminal.
echo Lancez ce fichier depuis l'Explorateur Windows, ou utilisez
echo directement 1-Lancer-Client.bat / 2-Lancer-Serveur.bat.
pause
exit /b 1

:END
exit /b 0
