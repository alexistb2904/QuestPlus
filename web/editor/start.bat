@echo off
setlocal
cd /d "%~dp0"
echo ===========================================
echo Quest Studio - React / TypeScript / Vite
echo ===========================================
where node >nul 2>&1
if errorlevel 1 (
 echo Node.js 22 LTS required: https://nodejs.org/
 pause
 exit /b 1
)
if not exist node_modules (
 echo Installing project dependencies...
 call npm install
 if errorlevel 1 goto :error
)
start "" "http://localhost:5173/"
call npm run dev
if errorlevel 1 goto :error
exit /b 0
:error
echo Quest Studio failed. Check the error above.
pause
exit /b 1
