# Phase 2 Hardware Audit Report

**Date:** 2026-07-04
**Auditor Name:** Lead Code Reviewer (Team ARES 23247)
**Scope:** `ARESLib-Kotlin/ftc-hardware` and `ARESLib-Kotlin/ftc-mocks` Modules (Focus: Hardware Timeout & Thread Purity - Pillar 5)

## 📊 Summary Scorecard

| Pillar | Grade | Critical Item Summary |
| :--- | :--- | :--- |
| 5. Hardware Timeout & Thread Purity | C+ | Most IO interfaces correctly use daemon threads for polling. However, a critical blocking I2C read exists in `MecanumHardwareIO`'s `EstimateMotorIO` during the main loop update. |

## 🔍 Sectioned Detail

### Pillar 5: Hardware Timeout & Thread Purity (R5) 🔌

**✅ Strengths**
- Subsystem IOs such as `FtcRevColorSensorV3`, `FtcVL53L5CX`, `OctoQuadFWv3`, `FtcAbsoluteAnalogEncoder`, and `FtcImu` all correctly instantiate asynchronous daemon threads to poll slow I2C/analog hardware, ensuring pure non-blocking access for the main control thread.
- `FtcMotor` properly handles high-latency `getCurrent()` polling by routing it through the background `ARES-MotorCurrent-Thread`.
- Motor encoder position and velocity caching operates cleanly and efficiently for `FtcMotor`, relying on the FTC SDK's bulk read caching mechanism without generating redundant I2C traffic.
- `PinpointIO` utilizes a dedicated `ARES-Pinpoint-Thread` to fetch odometry readings, synchronizing them safely behind a lock so the main loop can retrieve them instantaneously.

**⚠️ Findings**

| ID | Severity | Finding | Location |
| :--- | :--- | :--- | :--- |
| TAG-F01 | [HIGH] | The lightweight `EstimateMotorIO` wrapper synchronously calls `motor.getCurrent(...)` inside its `updateInputs()` method. Even though throttled to 10Hz, this forces a blocking I2C transaction on the main thread during `MecanumHardwareIO.refresh()`, inducing loop jitter and violating Pillar 5. | `MecanumHardwareIO.kt:251-253` |
| TAG-F02 | [LOW] | `PinpointIO.initialize()` executes synchronous `driver.resetPosAndIMU()` or `driver.update()` commands directly on the caller thread. While typically run during init, this could trigger watchdog timeouts if called mid-match for recalibration. | `PinpointIO.kt:98-107` |

## 🗺️ Roadmap to Compliance

*   **🔴 Must Fix:**
    *   Refactor `EstimateMotorIO` in `MecanumHardwareIO.kt` to either utilize the existing `ARES-MotorCurrent-Thread` from `FtcRevHubIO` or establish its own background polling thread for `motor.getCurrent()`. It must not perform blocking reads in `updateInputs()`.
*   **🟡 Should Fix:**
    *   Modify `PinpointIO.initialize()` to delegate the I2C reset/update commands to the `ARES-Pinpoint-Thread` via a state flag, preventing the caller thread from blocking during mid-match odometry recalibrations.
*   **🟢 Backlog:**
    *   Review all remaining `try/catch` blocks in background threads to ensure exceptions don't silently mask persistent I2C hardware disconnects, potentially adding a telemetry-visible reconnect/error state.
