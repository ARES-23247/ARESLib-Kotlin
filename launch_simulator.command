#!/bin/bash
# Move to the directory containing this script
cd "$(dirname "$0")"
echo "=============================================="
echo "  ARES 23247 - macOS Simulator Launcher"
echo "=============================================="
echo ""
echo "Starting ARES Desktop Simulation..."
chmod +x ./gradlew
./gradlew :simulator:run
echo ""
echo "Simulator closed. Press Enter to exit..."
read
