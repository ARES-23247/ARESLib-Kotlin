---
phase: "74"
name: "ftc-mock-isolation-deployment-hardening"
created: 2026-05-18
---

# Phase 74: ftc-mock-isolation-deployment-hardening — Context

## Decisions

### 1. Modern Universal IMU Migration
* **Issue**: Legacy `BNO055IMU` causes configuration crashes on newer REV Control Hubs using `BHI260AP` sensors.
* **Decision**: Migrate `FtcImu` and test OpModes to the modern unified `IMU` interface from the FTC SDK. Add necessary mocks (`IMU`, `IMU.Parameters`, `YawPitchRollAngles`, `RevHubOrientationOnRobot`) to `:ftc-mocks` to ensure local compilation.

### 2. LinearOpMode Lifecycle hard yield
* **Issue**: `ARESLibOpMode.kt` enters the loop immediately without calling `waitForStart()`, causing instant termination when initialized.
* **Decision**: Insert `waitForStart()` before entering the loop to ensure clean OpMode lifecycle management on real hardware.

### 3. Redux Joystick Drive Integration
* **Issue**: `JoystickDriveIntent` is dispatched but ignored in `DriveReducer.kt`, resulting in zero velocity outputs.
* **Decision**: Add a match block to `DriveReducer.kt` for `JoystickDriveIntent` to reduce the active gamepad inputs into EKF pose velocities.

### 4. Zero-Latency Software Encoder Offsets
* **Issue**: Calling `STOP_AND_RESET_ENCODER` synchronously blocks the active update loop for >20ms, introducing major latency spikes.
* **Decision**: Implement software encoder offsets in `FtcMotor` and `FtcEncoder`. Virtualize `resetEncoder()` by capturing the raw position as an offset, preserving seamless I2C execution speeds.

## Discretion Areas
* The exact orientation of the Control Hub in mock configuration parameters can default to logo up / USB forward.
* Software offsets can be reset automatically on OpMode initialization.

## Deferred Ideas
* Novice-friendly fail-safe hardware mapping is deferred to Milestone 3 (Phase 76).
