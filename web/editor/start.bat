@echo off
setlocal
cd /d "%~dp0..\.."
echo ===================================================
echo Quest Studio - Local Web Editor
echo ===================================================
echo Open http://localhost:5173/web/editor/ in a browser.
echo The editor stays on your computer. No account is needed.
echo.
start "" "http://localhost:5173/web/editor/"
where py.exe >nul 2>&1
if not errorlevel 1 (
  py -3 -m http.server 5173 --bind 127.0.0.1
) else (
  python -m http.server 5173 --bind 127.0.0.1
)
if errorlevel 1 (
  echo [Quest Studio] Python 3 is required to start the local server.
  pause
)
