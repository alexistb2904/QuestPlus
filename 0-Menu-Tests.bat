@echo off
setlocal EnableExtensions
cd /d "%~dp0"
:MENU
cls
echo ===================================================
echo              QUEST PLUS - TEST MENU
echo ===================================================
echo  1. Launch Minecraft client with Quest Plus
echo  2. Launch local dedicated server
echo  3. Build mod
echo  4. Run server integration tests
echo  5. Run client GameTests
echo  Q. Exit
echo ===================================================
choice /C 12345Q /N /M "Select an option: "
if errorlevel 6 goto END
if errorlevel 5 goto CLIENT_TEST
if errorlevel 4 goto SERVER_TEST
if errorlevel 3 goto BUILD
if errorlevel 2 goto SERVER
if errorlevel 1 goto CLIENT
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
:END
exit /b 0
