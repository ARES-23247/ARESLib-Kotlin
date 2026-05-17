# Phase 37: Physical Hardware Smoke Test & Diagnostics - Plan

## 1. ADB Deployment Automation
- Create `deploy.bat` containing:
  ```bat
  @echo off
  echo Building and deploying to REV Control Hub...
  call gradlew.bat :TeamCode:installDebug
  echo Deployment complete!
  ```
- Create `deploy.sh` containing:
  ```sh
  #!/bin/bash
  echo "Building and deploying to REV Control Hub..."
  ./gradlew :TeamCode:installDebug
  echo "Deployment complete!"
  ```

## 2. Final Build Validation
- Run `./gradlew test assembleDebug` to assert that:
  - All unit tests pass across the math, state, and simulation core.
  - The Android SDK compiles into an unsigned debug APK payload successfully.

## 3. Conclude Milestone v2.0
- Run `gsd-complete-milestone` to close out v2.0.
