# Plan - Phase 46: Pose Disambiguation and Outlier Filtering

Implement an outlier rejection filter to gate vision measurements before they can affect localization.

## Proposed Changes

### Core Subsystem

#### [NEW] [VisionOutlierFilter.kt](file:///c:/Users/david/dev/robotics/ftc/ARESLib-Kotlin/core/src/main/kotlin/com/areslib/hardware/vision/VisionOutlierFilter.kt)
- Create `VisionFilterConfig` data class for filter thresholds.
- Create `VisionOutlierFilter` class with validation methods.
- Implement distance check, ambiguity check, and yaw rotation deviation check relative to current robot heading.

### Testing

#### [NEW] [VisionOutlierFilterTest.kt](file:///c:/Users/david/dev/robotics/ftc/ARESLib-Kotlin/core/src/test/kotlin/com/areslib/hardware/vision/VisionOutlierFilterTest.kt)
- Test standard valid measurements.
- Test rejection of far-away measurements.
- Test rejection of high-ambiguity measurements.
- Test rejection of measurements with mismatched headings.

## Verification Plan

### Automated Tests
- Run unit tests:
  ```bash
  ./gradlew.bat :core:test --tests "com.areslib.hardware.vision.VisionOutlierFilterTest"
  ```
