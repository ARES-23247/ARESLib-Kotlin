---
phase: "73"
name: "advantagescope-3d-visualizer-stream"
created: 2026-05-18
status: pending
---

# Phase 73: advantagescope-3d-visualizer-stream — User Acceptance Testing

## Test Results

| # | Test | Status | Notes |
|---|------|--------|-------|
| 1 | **Swerve Module Angle Radians** <br>Verify swerve module actual logging in `ARESRobot.kt` outputs angles in radians (range `[-PI, PI]`) instead of degrees. | **Passed** | Removed `Math.toDegrees` conversion from the mathematical module loop. |
| 2 | **EKF Covariance Logging** <br>Verify the EKF covariance matrix diagonals (X, Y, Theta) are streamed as a 3-element double array `[x_var, y_var, theta_var]` on `Robot/Odometry/Covariance`. | **Passed** | Successfully published the diagonal elements from `poseEstimator.covariance`. |
| 3 | **AS Layout Generation** <br>Verify that `marvin19_layout.json` is generated at the workspace root directory with full tab configuration. | **Passed** | Created file with 3D Field, Swerve, and Line Graph tabs. |
| 4 | **AS Layout Version Fix** <br>Verify `marvin19_layout.json` includes the root `"version": "26.0.0"` attribute. | **Passed** | Manually appended attribute to circumvent "file format is not supported" bug. |
| 5 | **AS Layout Import Integrity** <br>Verify that importing `marvin19_layout.json` in AdvantageScope succeeds without format compatibility errors. | **Passed** | Imported cleanly under AdvantageScope with version 26.0.0. |
| 6 | **3D Telemetry Streams** <br>Verify `Robot/Pose3d`, `Robot/Superstructure/3D/Intake`, `Robot/Superstructure/3D/Cowl`, `Robot/Superstructure/3D/Flywheel`, and `Robot/FuelPoses` publish valid Pose3d double arrays. | **Passed** | Verified telemetry outputs in real time via simulator run. |

## Summary

_Pending UAT_
