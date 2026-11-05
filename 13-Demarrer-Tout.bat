@echo off
setlocal EnableExtensions DisableDelayedExpansion
cd /d "%~dp0"
set "ROOT=%CD%"
echo ==============================================================
echo  QUESTPLUS - ENVIRONNEMENT LOCAL COMPLET
echo  WEB React + API / Serveur Fabric / Client Minecraft
echo ==============================================================
echo.
echo [1/6] Verification des prerequis...
call "%ROOT%\scripts\_qa.bat" doctor
if errorlevel 1 goto FAIL
echo.
echo [2/6] Creation / verification de la configuration locale...
call "%ROOT%\scripts\_qa.bat" setup
if errorlevel 1 goto FAIL
node "%ROOT%\scripts\local-stack.mjs" preflight "%ROOT%"
if errorlevel 1 goto FAIL
echo.
echo [3/6] Demarrage du WEB (React + API) dans une nouvelle console...
start "QuestPlus WEB" /D "%ROOT%" "%ComSpec%" /k call "%ROOT%\scripts\_stack-component.bat" web
node "%ROOT%\scripts\local-stack.mjs" wait-web "%ROOT%"
if errorlevel 1 goto FAIL_WEB
echo.
echo [4/6] Demarrage du serveur Minecraft dans une nouvelle console...
echo Si la Minecraft EULA n'a pas encore ete acceptee, suivez les
echo instructions dans la fenetre du SERVEUR.
start "QuestPlus Minecraft SERVER" /D "%ROOT%" "%ComSpec%" /k call "%ROOT%\scripts\_stack-component.bat" server
node "%ROOT%\scripts\local-stack.mjs" wait-minecraft "%ROOT%"
if errorlevel 1 goto FAIL_SERVER
echo.
echo [5/6] Lancement d'une instance Minecraft avec QuestPlus...
start "QuestPlus Minecraft CLIENT" /D "%ROOT%" "%ComSpec%" /k call "%ROOT%\scripts\_stack-component.bat" client
echo.
echo [6/6] Ouverture de Quest Studio...
start "" "http://localhost:8787/"
echo.
echo ==============================================================
echo  ENVIRONNEMENT DEMARRE (CLIENT EN COURS D'OUVERTURE)
echo ==============================================================
echo  Web / API      : http://localhost:8787/
echo  Minecraft      : localhost:25565
echo  Commande OP    : /questplus editor
echo.
echo Les 3 processus tournent dans des consoles distinctes.
echo Pour arreter : fermez le CLIENT, faites "stop" dans le SERVEUR,
echo puis CTRL+C dans la console WEB. Ne fermez pas le serveur brutalement.
echo ==============================================================
echo.
if /I not "%~1"=="--nopause" pause
exit /b 0

:FAIL_WEB
echo [ERREUR] Le web n'est pas pret. Consultez la console QuestPlus WEB.
goto FAIL
:FAIL_SERVER
echo [ERREUR] Le serveur Minecraft n'est pas pret.
echo Verifiez la EULA, les logs Fabric et le port local 25565.
goto FAIL
:FAIL
echo.
echo [QuestPlus] Lancement interrompu. Les composants deja demarres
echo peuvent encore tourner dans leurs propres consoles.
if /I not "%~1"=="--nopause" pause
exit /b 1
