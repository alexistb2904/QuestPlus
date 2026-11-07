@echo off
setlocal EnableExtensions DisableDelayedExpansion
for %%I in ("%~dp0..\..") do set "ROOT=%%~fI"
echo ==============================================================
echo   Quest Studio - React / TypeScript / Vite
echo ==============================================================
call "%ROOT%\scripts\_qa.bat" studio-dev
set "STATUS=%ERRORLEVEL%"
if /I not "%~1"=="--nopause" pause
exit /b %STATUS%
