# Phase 32: Physical Vision & Hardware Configuration

**Status:** Complete
**Date:** 2026-05-17

## Changes
- Created `FtcLimelightIO` to interface with physical Limelight 3A cameras and parse `botpose` data arrays into `Pose3d` standard instances.
- Created `FtcVisionPortalIO` to parse custom multi-tag coordinates from the standard FTC `AprilTagProcessor` SDK inside a clean abstract loop.
- Built a unified `RobotConfig` dependency registry decoupling physical hardware mapping initialization from high-level robot loops.
- Added mock vision portal, AprilTag, and Limelight stubs in the `:core` project so desktop simulator runs compile successfully.
