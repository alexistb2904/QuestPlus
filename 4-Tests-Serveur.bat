@echo off
setlocal EnableExtensions
cd /d "%~dp0"
echo ===================================================
echo  Quest Plus - Build + dedicated server GameTests
echo ===================================================
echo.
call "%~dp0scripts\_gradle.bat" build integrationTest
set "STATUS=%ERRORLEVEL%"
echo.
if not "%STATUS%"=="0" echo [Quest Plus] Operation failed. Check the logs above.
if "%STATUS%"=="0" echo [Quest Plus] Operation completed.
echo.
pause
exit /b %STATUS%
