@echo off
setlocal EnableExtensions DisableDelayedExpansion
cd /d "%~dp0"

:MENU
cls
echo ====================================================================
echo                 QUESTPLUS - DEVELOPPEMENT ET TESTS
echo ====================================================================
echo.
echo  [ MINECRAFT / FABRIC 26.3 ]
echo   1. Lancer le client Minecraft (dev)
echo   2. Lancer le serveur dedie local (dev)
echo   3. Compiler le JAR + tests JUnit
echo   4. GameTests serveur + verification des sauvegardes
echo   5. GameTests client (fenetre graphique)
echo   6. Installer Java JDK 25 (confirmation)
echo.
echo  [ QUEST STUDIO / REACT ]
echo   7. Lancer Quest Studio (Vite / HMR)
echo   8. Tester toute la partie web (React + API)
echo   9. Lancer le web integre (React + API, localhost)
echo.
echo  [ SUITES / DIAGNOSTIC ]
echo  10. Tout tester automatiquement (sans tests client GUI)
echo  11. Diagnostiquer Java 25, Node 22 et fichiers requis
echo  12. Configurer les secrets de test LOCAL (sans ecraser)
echo  13. Typecheck TypeScript + build React
echo  14. Tests unitaires React
echo  15. Tests API, authentification, imports et publication
echo  16. Tester la sante de l'API locale (GET /health)
echo  17. Tests unitaires Java (JUnit)
echo  18. Compiler uniquement Java + ressources
echo  19. Apercu du build de production React
echo  20. Construire le Docker web unifie (sans deployer)
echo  21. Afficher les rapports de tests
echo  22. Tout tester + GameTests client avec interface graphique
echo  23. Reinstaller les dependances React (npm ci)
echo.
echo   Q. Quitter
echo ====================================================================
echo.
set "SELECTION="
set /p "SELECTION=Choix [1-23 / Q] : "
if errorlevel 1 goto INPUT_ERROR
if /I "%SELECTION%"=="Q" exit /b 0
if "%SELECTION%"=="1" goto CLIENT
if "%SELECTION%"=="2" goto SERVER
if "%SELECTION%"=="3" goto BUILD
if "%SELECTION%"=="4" goto SERVER_GAME
if "%SELECTION%"=="5" goto CLIENT_GAME
if "%SELECTION%"=="6" goto JAVA25
if "%SELECTION%"=="7" goto STUDIO
if "%SELECTION%"=="8" goto WEB_TEST
if "%SELECTION%"=="9" goto API_RUN
if "%SELECTION%"=="10" goto ALL
if "%SELECTION%"=="11" set "TASK=doctor"&goto RUN_TASK
if "%SELECTION%"=="12" goto SETUP_LOCAL
if "%SELECTION%"=="13" set "TASK=studio-build"&goto RUN_TASK
if "%SELECTION%"=="14" set "TASK=studio-test"&goto RUN_TASK
if "%SELECTION%"=="15" set "TASK=api-test"&goto RUN_TASK
if "%SELECTION%"=="16" set "TASK=api-health"&goto RUN_TASK
if "%SELECTION%"=="17" set "TASK=java-test"&goto RUN_TASK
if "%SELECTION%"=="18" set "TASK=java-compile"&goto RUN_TASK
if "%SELECTION%"=="19" set "TASK=studio-preview"&goto RUN_TASK
if "%SELECTION%"=="20" set "TASK=docker-build"&goto RUN_TASK
if "%SELECTION%"=="21" set "TASK=reports"&goto RUN_TASK
if "%SELECTION%"=="22" goto ALL_GUI
if "%SELECTION%"=="23" set "TASK=studio-ci"&goto RUN_TASK
echo.
echo [QuestPlus] Choix invalide : entrez un numero entre 1 et 23 ou Q.
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
:SERVER_GAME
call "%~dp04-Tests-Serveur.bat"
goto MENU
:CLIENT_GAME
call "%~dp05-Tests-Client.bat"
goto MENU
:JAVA25
call "%~dp06-Installer-Java-25.bat"
goto MENU
:STUDIO
call "%~dp07-QuestStudio.bat"
goto MENU
:WEB_TEST
call "%~dp08-Tests-Web.bat"
goto MENU
:API_RUN
call "%~dp09-Lancer-Web-API.bat"
goto MENU
:ALL
call "%~dp010-Tout-Tester.bat"
goto MENU
:SETUP_LOCAL
call "%~dp012-Configuration-Locale.bat"
goto MENU
:ALL_GUI
call "%~dp010-Tout-Tester.bat" gui
goto MENU

:RUN_TASK
echo.
call "%~dp0scripts\_qa.bat" "%TASK%"
set "STATUS=%ERRORLEVEL%"
echo.
if "%STATUS%"=="0" (echo [OK] %TASK%) else (echo [ECHEC] %TASK% : code %STATUS%)
pause
goto MENU

:INPUT_ERROR
echo.
echo [QuestPlus] Lecture de la saisie impossible.
echo Lancez ce menu depuis l'Explorateur Windows.
pause
exit /b 1
