@echo off
setlocal EnableExtensions DisableDelayedExpansion
for %%I in ("%~dp0..") do set "ROOT=%%~fI"
cd /d "%ROOT%"
set "ROLE=%~1"
if /I "%ROLE%"=="web" goto WEB
if /I "%ROLE%"=="server" goto SERVER
if /I "%ROLE%"=="client" goto CLIENT
echo [QuestPlus] Composant inconnu : %ROLE%
exit /b 2

:WEB
title QuestPlus - React et API
echo [WEB] Compilation de React et demarrage de l'API sur localhost:8787.
set "QUESTPLUS_NO_BROWSER=1"
call "%ROOT%\scripts\_qa.bat" api-run
goto DONE

:SERVER
title QuestPlus - Serveur Minecraft Fabric
echo [MINECRAFT SERVER] Le serveur sera accessible sur localhost:25565.
call "%ROOT%\2-Lancer-Serveur.bat" --nopause
goto DONE

:CLIENT
title QuestPlus - Client Minecraft Fabric
echo [MINECRAFT CLIENT] Lancement de l'instance de developpement.
call "%ROOT%\1-Lancer-Client.bat" --nopause
goto DONE

:DONE
set "RESULT=%ERRORLEVEL%"
echo.
if not "%RESULT%"=="0" echo [QuestPlus] Composant termine avec une erreur : %RESULT%
if "%RESULT%"=="0" echo [QuestPlus] Composant arrete.
echo Fermez cette fenetre lorsque vous avez termine d'inspecter les logs.
exit /b %RESULT%
