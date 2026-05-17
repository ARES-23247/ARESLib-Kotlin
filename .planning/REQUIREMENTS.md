# Requirements: ARESLib-Kotlin

**Defined:** 2026-05-17
**Core Value:** 100% pure, immutable, and testable control logic completely isolated from hardware SDKs, allowing the exact same mathematical core to run flawlessly on both FTC Control Hubs and FRC RoboRIOs.

## v2.0 Requirements

Requirements for real robot deployment. Each maps to roadmap phases.

### Build Chain

- [ ] **BUILD-01**: Project compiles successfully with `gradlew.bat :TeamCode:installDebug` from root directory
- [ ] **BUILD-02**: `:core` and `:ftc-hardware` modules produce artifacts consumable by the Android build pipeline without class conflicts
- [ ] **BUILD-03**: Simulator module continues to compile and run independently on desktop JVM

### Mock Isolation

- [ ] **MOCK-01**: FTC SDK mock/stub classes are excluded from Android production builds so they don't conflict with real SDK classes
- [ ] **MOCK-02**: Desktop simulation and unit tests continue to pass using the mock stubs
- [ ] **MOCK-03**: No `com.qualcomm.*` or `org.firstinspires.*` stub classes exist in the `:core` module's Android-bound output

### OpMode Registration

- [ ] **OPMODE-01**: A `@TeleOp`-annotated OpMode appears in the Driver Station OpMode list when deployed to Control Hub
- [ ] **OPMODE-02**: The registered OpMode wires FtcGamepadAdapter, MecanumHardwareIO, and PinpointIO to the Redux state loop
- [ ] **OPMODE-03**: `waitForStart()` is called before the control loop begins

### Hardware Configuration

- [ ] **HW-01**: Motor config names (`fl`, `fr`, `bl`, `br`) are documented and configurable
- [ ] **HW-02**: Motor directions are correctly set for standard mecanum (right-side reversed)
- [ ] **HW-03**: Pinpoint odometry device name is documented and configurable

### Telemetry Resilience

- [ ] **TEL-01**: NT4 telemetry handles missing WebSocket clients gracefully (no crash if no laptop connected)
- [ ] **TEL-02**: Data logging works on Control Hub SD card storage path
- [ ] **TEL-03**: Telemetry failures do not block the main control loop

### Deployment

- [ ] **DEPLOY-01**: `deploy.bat` successfully builds and pushes APK to Control Hub over Wi-Fi
- [ ] **DEPLOY-02**: Deployed APK runs on REV Control Hub without runtime crashes
- [ ] **DEPLOY-03**: All 4 mecanum wheels respond to gamepad input with correct direction

## v2.1 Requirements

Deferred to future release.

### Autonomous Deployment

- **AUTO-01**: Autonomous OpMode deploys and follows PathPlanner paths on real hardware
- **AUTO-02**: Odometry-corrected pose estimation works with physical Pinpoint sensor

### Closed-Loop Control

- **LOOP-01**: PID velocity control on drive motors using encoder feedback
- **LOOP-02**: Heading stabilization via IMU feedback during TeleOp

## Out of Scope

| Feature | Reason |
|---------|--------|
| FRC RoboRIO deployment | FTC-first — FRC deployment is a separate future milestone |
| Swerve hardware wiring | No swerve hardware available; mecanum is the target drivetrain |
| Vision pipeline on real hardware | Vision IO exists but field-testing is deferred to post-deployment |
| Android Studio GUI workflow | Command-line Gradle deployment is sufficient; no IDE integration needed |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| BUILD-01 | Phase 34 | Pending |
| BUILD-02 | Phase 34 | Pending |
| BUILD-03 | Phase 34 | Pending |
| MOCK-01 | Phase 34 | Pending |
| MOCK-02 | Phase 34 | Pending |
| MOCK-03 | Phase 34 | Pending |
| OPMODE-01 | Phase 35 | Pending |
| OPMODE-02 | Phase 35 | Pending |
| OPMODE-03 | Phase 35 | Pending |
| HW-01 | Phase 35 | Pending |
| HW-02 | Phase 35 | Pending |
| HW-03 | Phase 35 | Pending |
| TEL-01 | Phase 36 | Pending |
| TEL-02 | Phase 36 | Pending |
| TEL-03 | Phase 36 | Pending |
| DEPLOY-01 | Phase 37 | Pending |
| DEPLOY-02 | Phase 37 | Pending |
| DEPLOY-03 | Phase 37 | Pending |

**Coverage:**
- v2.0 requirements: 18 total
- Mapped to phases: 18
- Unmapped: 0 ✓

---
*Requirements defined: 2026-05-17*
*Last updated: 2026-05-17 after initial definition*
