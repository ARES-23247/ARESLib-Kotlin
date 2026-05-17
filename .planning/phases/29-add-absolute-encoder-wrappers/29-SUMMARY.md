# Phase 29: Add absolute encoder wrappers

**Status:** Complete
**Date:** 2026-05-17

## Changes
- Added RevEncoderVersion to MotorIO.kt for configuring pulse bounds and voltages.
- Implemented FtcAbsoluteAnalogEncoder in FtcRevHubIO.kt.
- Added REG_PULSE_WIDTH_0 mapping and eadChannelPulseWidth to OctoQuadFWv3, and implemented OctoQuadAbsolutePWMEncoder.
- Added eadPwmPulseWidth to SrsHubDriver and implemented SrsHubAbsoluteAnalogEncoder and SrsHubAbsolutePWMEncoder in SrsHubIO.kt.
- Verified build with ./ftc-app/gradlew.bat build.