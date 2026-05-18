# Plan - Phase 45: Chronological Asynchronous Vision Measurement Buffer

Implement a thread-safe, chronological sliding window buffer for asynchronous vision measurements to prepare for Extended Kalman Filter fusion.

## Proposed Changes

### Core Subsystem

#### [NEW] [VisionMeasurementBuffer.kt](file:///c:/Users/david/dev/robotics/ftc/ARESLib-Kotlin/core/src/main/kotlin/com/areslib/hardware/vision/VisionMeasurementBuffer.kt)
- Create a `VisionMeasurementBuffer` class in `com.areslib.hardware.vision`.
- Use a thread-safe collections or synchronized blocks around an `ArrayList`/`SortedSet`.
- Implement `addMeasurement` with sliding-window eviction (e.g. 1.5 seconds).
- Implement retrieval and query utilities.

### Testing

#### [NEW] [VisionMeasurementBufferTest.kt](file:///c:/Users/david/dev/robotics/ftc/ARESLib-Kotlin/core/src/test/kotlin/com/areslib/hardware/vision/VisionMeasurementBufferTest.kt)
- Test chronological ordering of out-of-order measurements.
- Test time-based eviction of old measurements.
- Test thread-safety by concurrently writing from multiple threads and asserting consistent state.

## Verification Plan

### Automated Tests
- Run unit tests:
  ```bash
  ./gradlew.bat :core:test --tests "com.areslib.hardware.vision.VisionMeasurementBufferTest"
  ```
