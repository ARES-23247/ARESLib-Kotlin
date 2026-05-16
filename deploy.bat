@echo off
echo ==============================================
echo ARESLib-Kotlin Deploy Script
echo ==============================================
echo.

cd ftc-app
echo Compiling and pushing to Control Hub...
call gradlew.bat installDebug

if %ERRORLEVEL% EQU 0 (
    echo.
    echo [SUCCESS] APK pushed to the robot.
    echo Open the Driver Station, select "ARES Mecanum Drive", and run!
) else (
    echo.
    echo [FAILED] Failed to build or deploy. Make sure your laptop is connected to the Control Hub's Wi-Fi.
)
cd ..
