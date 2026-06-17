@echo off
title ARES Simulator Launcher
echo ==============================================
echo   ARES 23247 - Simulator Launcher
echo ==============================================
echo.

:: Configure JDK 17 environment if present (matches daemon settings)
if exist "C:\Program Files\Java\jdk-17" (
    set "JAVA_HOME=C:\Program Files\Java\jdk-17"
    set "PATH=C:\Program Files\Java\jdk-17\bin;%PATH%"
    echo [Launcher] Configured local JDK 17 environment.
)

echo Starting ARES Desktop Simulation...
cd /d "%~dp0"
call gradlew.bat :simulator:run
echo.
echo Simulator closed.
pause
