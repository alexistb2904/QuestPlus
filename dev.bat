@echo off
setlocal EnableExtensions DisableDelayedExpansion
cd /d "%~dp0"
if not "%~1"=="" goto DISPATCH
:MENU
cls
echo QuestPlus development
echo.
echo  1. Run Minecraft client
echo  2. Run Minecraft server
echo  3. Run Quest Studio
echo  4. Run local web API
echo  5. Build mod
echo  6. Run all automated checks
echo  7. Run server GameTests
echo  8. Run client GameTests
echo  9. Check prerequisites
echo  0. Exit
echo.
choice /c 1234567890 /n /m "Select an option: "
if errorlevel 10 exit /b 0
if errorlevel 9 (set "COMMAND=doctor" & goto RUN_MENU)
if errorlevel 8 (set "COMMAND=client-test" & goto RUN_MENU)
if errorlevel 7 (set "COMMAND=server-test" & goto RUN_MENU)
if errorlevel 6 (set "COMMAND=test" & goto RUN_MENU)
if errorlevel 5 (set "COMMAND=build" & goto RUN_MENU)
if errorlevel 4 (set "COMMAND=api" & goto RUN_MENU)
if errorlevel 3 (set "COMMAND=studio" & goto RUN_MENU)
if errorlevel 2 (set "COMMAND=server" & goto RUN_MENU)
if errorlevel 1 (set "COMMAND=client" & goto RUN_MENU)
exit /b 1
:RUN_MENU
call "%~f0" %COMMAND%
echo.
echo Exit code: %ERRORLEVEL%
pause
goto MENU
:DISPATCH
if /I "%~1"=="client" (call "%~dp0scripts\dev-client.bat" --nopause & goto RETURN)
if /I "%~1"=="server" (call "%~dp0scripts\dev-server.bat" --nopause & goto RETURN)
if /I "%~1"=="studio" (call "%~dp0scripts\_qa.bat" studio-dev & goto RETURN)
if /I "%~1"=="api" (call "%~dp0scripts\_qa.bat" api-run & goto RETURN)
if /I "%~1"=="build" (call "%~dp0scripts\_qa.bat" java-build & goto RETURN)
if /I "%~1"=="test" (call "%~dp0scripts\_qa.bat" all & goto RETURN)
if /I "%~1"=="server-test" (call "%~dp0scripts\_qa.bat" server-test & goto RETURN)
if /I "%~1"=="client-test" (call "%~dp0scripts\_qa.bat" client-test & goto RETURN)
if /I "%~1"=="doctor" (call "%~dp0scripts\_qa.bat" doctor & goto RETURN)
if /I "%~1"=="setup" (call "%~dp0scripts\_qa.bat" setup & goto RETURN)
if /I "%~1"=="web-test" (call "%~dp0scripts\_qa.bat" web-all & goto RETURN)
if /I "%~1"=="stack" (call "%~dp0scripts\dev-stack.bat" --nopause & goto RETURN)
echo Usage: dev.bat [client^|server^|studio^|api^|build^|test^|server-test^|client-test^|doctor^|setup^|web-test^|stack]
exit /b 2
:RETURN
exit /b %ERRORLEVEL%
