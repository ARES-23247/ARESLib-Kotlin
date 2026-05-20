# Scope: Milestone 3 - HardwareFaultTolerance

## Architecture
- Hardware I/O wrapper classes in `:ftc-hardware` (e.g. `FtcRevHubIO.kt`, `PinpointOdometryIO.kt`, `FtcMotor` inside `FtcRevHubIO.kt`).
- Fallback logic to avoid indefinite blocking during `I2C`/`UART` communication.
- Automated motor current spike limits (via `powerScale`) and stall detection using `currentAmps`, `velocity`, and commanded `power` / `setVoltage`.

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| 1 | HardwareFaultTolerance | Add read timeouts and fallback logic for I2C/UART sensors (Pinpoint, Gyro) to prevent loop hangs. Add automated motor current spike limits and stall detection (comparing encoder velocities against applied voltage). | none | PLANNED |

## Interface Contracts
### Application ↔ Hardware
- Hardware reads must not block indefinitely; they must return a known fallback value or exception on timeout.
- Motor stall limits should dynamically adjust `powerScale` in `MotorIO` implementation.

## Code Layout
- Hardware I/O: `ftc-hardware/src/main/kotlin/com/areslib/ftc/hardware/`
