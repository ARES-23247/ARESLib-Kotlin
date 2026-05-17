#!/bin/bash
echo "=============================================="
echo "ARESLib-Kotlin Deploy Script"
echo "=============================================="
echo ""

cd ftc-app || exit
echo "Compiling and pushing to Control Hub..."
./gradlew installDebug

if [ $? -eq 0 ]; then
    echo ""
    echo "[SUCCESS] APK pushed to the robot."
    echo "Open the Driver Station, select 'ARES Mecanum', and run!"
else
    echo ""
    echo "[FAILED] Failed to build or deploy. Make sure your laptop is connected to the Control Hub's Wi-Fi."
fi
cd ..
