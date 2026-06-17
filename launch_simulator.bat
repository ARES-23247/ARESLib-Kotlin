@echo off
title ARES Simulator Launcher
echo ==============================================
echo   ARES 23247 - Simulator Launcher
echo ==============================================
echo.
echo Starting ARES Desktop Simulation...
cd /d "%~dp0"
call gradlew.bat :simulator:run
echo.
echo Simulator closed.
pause
