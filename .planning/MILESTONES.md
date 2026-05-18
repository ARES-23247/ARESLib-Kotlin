# Milestones

## v2.4 FRC/FTC Vision & Multi-Sensor Kalman Filter Integration (Shipped: 2026-05-18)

**Phases completed:** 4 phases, 4 plans

**Key accomplishments:**
- Built a thread-safe, chronological sorting buffer (`VisionMeasurementBuffer`) to process asynchronous, latency-delayed vision measurements.
- Implemented standard-deviation-driven multi-sensor Kalman Filter pose fusion supporting retroactive history rewinds/replays.
- Created a robust outlier disambiguation filter (`VisionOutlierFilter`) to reject noisy, high-ambiguity or coordinate-divergent visual updates.
- Modeled physical AprilTag targets in a high-fidelity simulator (`VisionSimulator`) to verify tracking accuracy and noise tolerance under Gaussian disturbances.

---

## v2.3 FRC Autonomous Trajectory Following (Shipped: 2026-05-18)

**Phases completed:** 2 phases, 2 plans

**Key accomplishments:**
- Thread-safe loading and parsing of PathPlanner JSON trajectory assets inside `frc-app` module.
- Smooth start coordinate snapping/alignment for both Redux state and dyn4j physics bodies.
- Feedforward/feedback `HolonomicDriveController` loop integration inside FRC `autonomousPeriodic()` loops.
- Structured streaming of target poses and active deviations to AdvantageScope.

---

## v2.2 FRC Physics Simulation (Shipped: 2026-05-18)

**Phases completed:** 2 phases, 2 plans

**Key accomplishments:**
- Implemented high-fidelity dyn4j rigid-body 2D physics swerve simulator core.
- Configured dynamic timestep updates, floor friction damping, and collision dynamics (wall and hub).
- Designed flywheel physics with angular momentum (MOI/torque) and speed regulation.
- Automated simulation telemetry streaming to AdvantageScope.

---

## v2.1 FRC CTRE Swerve Integration (Shipped: 2026-05-18)

**Phases completed:** 3 phases, 3 plans

**Key accomplishments:**
- Decoupled physical motor and sensor wrappers into platform-agnostic `SwerveModuleIO` structures.
- Implemented thin airlock for CTRE CANivore (Phoenix 6 `waitForUpdate`) thread-safe bus sync.
- Constructed FRC `ARESRobot` central framework loop routing telemetry to WPILog.

---

## v2.0 Real Robot Deployment (Shipped: 2026-05-18)

**Phases completed:** 4 phases, 4 plans

**Key accomplishments:**
- Unified multi-module gradle build chain supporting pure JVM/Android project builds.
- Isolated mock SDK classes completely from physical production modules.
- Deployed TeleOp LinearOpMode with hardware maps for motor, pinpoint, and IMU configurations.
- Hardened wireless ADB developer deployment capabilities.

---

## v1.10 Match-Ready Telemetry & Hardware Integration (Shipped: 2026-05-17)

**Phases completed:** 4 phases, 4 plans

**Key accomplishments:**
- Developed unified `ARESController` edge-detection trigger logic.
- Embedded a lightweight, pure-Kotlin NT4 WebSocket server.
- Built physical hardware vision wrappers for Limelight 3A and AprilTags.
- Implemented high-performance asynchronous thread-safe local CSV data logging.

---

## v1.9 Core Hardware IO Interfaces (Shipped: 2026-05-17)

**Phases completed:** 4 phases, 4 plans

**Key accomplishments:**
- Implemented concrete `FtcRevHubIO`, `PinpointOdometryIO`, and auxiliary `I2C Octoquad` wrappers.
- Created robust absolute PWM and analog encoder wrappers for continuous CRServos.

---

## v1.8 Vision & Localization (Shipped: 2026-05-17)

**Phases completed:** 4 phases, 4 plans

**Key accomplishments:**
- Built platform-agnostic 3D geometry matrix classes (`Pose3d`, `Translation3d`, `Rotation3d`).
- Implemented immutable array-backed Kalman Filter pose estimator.
- Added pure `VisionState` processing and pose disambiguation structures.

---

## v1.6 Advanced Path Generation (Shipped: 2026-05-16)

**Phases completed:** 3 phases, 3 plans

**Key accomplishments:**

- Implemented Quintic Hermite Spline calculation from JSON waypoints.
- Added Motion Profiling (Trapezoidal velocity profiles) for smooth time-parameterized curves.
- Integrated PathPlanner Event Markers as `RobotAction` triggers inside the state machine.
- Validated smooth curved path following in the desktop simulator with AdvantageScope visualization.

---

## v1.2 Deployable Mecanum Base (Shipped: 2026-05-16)

**Phases completed:** 3 phases, 3 plans

**Key accomplishments:**

- Successfully deployed the functional core onto FTC hardware.
- The `MecanumKinematics` translates abstract speeds to wheel vectors.
- `MecanumHardwareIO` connects abstract wheel vectors to Rev `DcMotorEx` hardware map objects.
- `ARESMecanumTeleOp` ties gamepad inputs, sensors, hardware, and state together via Redux dispatch.

---
## v1.1 Driveable Base, Hardware Odometry & Telemetry (Shipped: 2026-05-16)

**Phases completed:** 9 phases, 9 plans, 0 tasks

**Key accomplishments:**

- (none recorded)

---

## v1.0 MVP (Shipped: 2026-05-16)

**Phases completed:** 5 phases, 5 plans, 0 tasks

**Key accomplishments:**

- (none recorded)

---
