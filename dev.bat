@echo off
setlocal EnableExtensions DisableDelayedExpansion
cd /d "%~dp0"
if not "%~1"=="" goto DISPATCH

:: --- Activation des couleurs ANSI (Windows 10+) -------------------
for /F %%a in ('echo prompt $E ^| cmd') do set "ESC=%%a"
set "CYAN=%ESC%[96m"
set "GREEN=%ESC%[92m"
set "YEL=%ESC%[93m"
set "RED=%ESC%[91m"
set "DIM=%ESC%[90m"
set "RST=%ESC%[0m"

:MENU
cls
echo.
echo  %CYAN%+--------------------------------------+%RST%
echo  %CYAN%^|      QuestPlus development           ^|%RST%
echo  %CYAN%+--------------------------------------+%RST%
echo.
echo   %GREEN%[1]%RST%  Run Minecraft client
echo   %GREEN%[2]%RST%  Run Minecraft server
echo   %GREEN%[3]%RST%  Run Quest Studio
echo   %GREEN%[4]%RST%  Run local web API
echo   %GREEN%[5]%RST%  Build mod
echo   %GREEN%[6]%RST%  Run all automated checks
echo   %GREEN%[7]%RST%  Run server GameTests
echo   %GREEN%[8]%RST%  Run client GameTests
echo   %GREEN%[9]%RST%  Check prerequisites
echo   %RED%[0]%RST%  Exit
echo.
set "CHOICE="
set /p "CHOICE=  %YEL%Select an option:%RST% "
if not defined CHOICE goto MENU

if "%CHOICE%"=="0" exit /b 0
if "%CHOICE%"=="1" (set "COMMAND=client"      & goto RUN_MENU)
if "%CHOICE%"=="2" (set "COMMAND=server"      & goto RUN_MENU)
if "%CHOICE%"=="3" (set "COMMAND=studio"      & goto RUN_MENU)
if "%CHOICE%"=="4" (set "COMMAND=api"         & goto RUN_MENU)
if "%CHOICE%"=="5" (set "COMMAND=build"       & goto RUN_MENU)
if "%CHOICE%"=="6" (set "COMMAND=test"        & goto RUN_MENU)
if "%CHOICE%"=="7" (set "COMMAND=server-test" & goto RUN_MENU)
if "%CHOICE%"=="8" (set "COMMAND=client-test" & goto RUN_MENU)
if "%CHOICE%"=="9" (set "COMMAND=doctor"      & goto RUN_MENU)

echo.
echo  %RED%Choix invalide : "%CHOICE%"%RST%
timeout /t 2 >nul
goto MENU

:RUN_MENU
call "%~f0" %COMMAND%
echo.
echo  Exit code: %ERRORLEVEL%
pause
goto MENU

:DISPATCH
if /I "%~1"=="client"      (call "%~dp0scripts\dev-client.bat" --nopause & goto RETURN)
if /I "%~1"=="server"      (call "%~dp0scripts\dev-server.bat" --nopause & goto RETURN)
if /I "%~1"=="studio"      (call "%~dp0scripts\_qa.bat" studio-dev   & goto RETURN)
if /I "%~1"=="api"         (call "%~dp0scripts\_qa.bat" api-run      & goto RETURN)
if /I "%~1"=="build"       (call "%~dp0scripts\_qa.bat" java-build   & goto RETURN)
if /I "%~1"=="test"        (call "%~dp0scripts\_qa.bat" all          & goto RETURN)
if /I "%~1"=="server-test" (call "%~dp0scripts\_qa.bat" server-test  & goto RETURN)
if /I "%~1"=="client-test" (call "%~dp0scripts\_qa.bat" client-test  & goto RETURN)
if /I "%~1"=="doctor"      (call "%~dp0scripts\_qa.bat" doctor       & goto RETURN)
if /I "%~1"=="setup"       (call "%~dp0scripts\_qa.bat" setup        & goto RETURN)
if /I "%~1"=="web-test"    (call "%~dp0scripts\_qa.bat" web-all      & goto RETURN)
if /I "%~1"=="stack"       (call "%~dp0scripts\dev-stack.bat" --nopause & goto RETURN)
echo Usage: dev.bat [client^|server^|studio^|api^|build^|test^|server-test^|client-test^|doctor^|setup^|web-test^|stack]
exit /b 2

:RETURN
exit /b %ERRORLEVEL%
