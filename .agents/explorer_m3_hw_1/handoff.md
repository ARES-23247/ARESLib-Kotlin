# Milestone 3: HardwareFaultTolerance Handoff Report

## Observation
1. **Target Files Analyzed:**
   - `core/src/main/kotlin/com/areslib/hardware/MotorIO.kt`: Defines the base hardware abstraction. Currently lacks a periodic `update()` method.
   - `ftc-hardware/src/main/kotlin/com/areslib/ftc/hardware/PinpointOdometryIO.kt`: Contains `driver.update()`, a blocking I2C call that updates odometry state.
   - `ftc-hardware/src/main/kotlin/com/areslib/ftc/hardware/FtcRevHubIO.kt`: Contains `FtcImu` with blocking calls to `imu.getRobotYawPitchRollAngles()`, and `FtcMotor` which applies power directly but lacks safety checks.
2. **Current State:**
   - I2C calls in `PinpointOdometryIO` and `FtcImu` run synchronously on the main thread. If a sensor disconnects or the I2C bus hangs, the entire robot loop freezes.
   - `FtcMotor` allows unbounded current draw and can stall out (power applied but velocity is 0), leading to burnt motors or blown fuses.

## Logic Chain
1. **I2C / UART Timeouts:**
   - **Problem**: Hardware calls like `driver.update()` and `imu.getRobotYawPitchRollAngles()` can stall the main loop indefinitely.
   - **Solution**: Wrap these calls using `java.util.concurrent.Executors.newSingleThreadExecutor()`. By submitting the read as a `Callable` and awaiting the `Future` with a timeout (e.g., `.get(5, TimeUnit.MILLISECONDS)`), we cap the maximum wait time. 
   - **Fallback**: If a `TimeoutException` is caught, we cancel the Future and leave the previous sensor values intact. This safely degrades performance without crashing or freezing the robot.

2. **Motor Stall & Current Spike Detection:**
   - **Problem**: Motors drawing too much current or being physically blocked (stalled) while powered can cause hardware damage.
   - **Solution**: 
     - Add an empty `fun update() {}` method to the `MotorIO` interface so systems can trigger hardware checks periodically.
     - Override `update()` in `FtcMotor`.
     - **Spike Detection**: If `this.currentAmps > 8.0` (configurable), aggressively scale back power via `powerScale = 0.5`.
     - **Stall Detection**: If the user commands significant power (`Math.abs(targetPower) > 0.05`) but the motor isn't moving (`Math.abs(this.velocity) < 10.0`), start a timer. If this persists for > 500ms, mark as stalled and drop `powerScale = 0.0`.
     - **Recovery**: If the user stops commanding power (`Math.abs(targetPower) < 0.05`), clear the stall timer, reset `isStalled = false`, and restore `powerScale = 1.0`.

## Caveats
- **Executor Timeout Queueing**: If a native I2C call hard-hangs, the `Future.cancel(true)` might not interrupt the underlying native thread. Subsequent calls will be queued and immediately time out after 5ms. This adds a consistent 5ms delay per loop per dead sensor, which is acceptable compared to a total freeze.
- **I2C Overhead in Motor Updates**: Accessing `currentAmps` in FTC often triggers a synchronous I2C transaction because it may not be included in standard bulk cache reads. Calling `update()` on 4+ drive motors every loop could add ~4-8ms of overhead. If performance becomes an issue, current polling should be rate-limited.

## Conclusion
The implementer agent should take the following actions:
1. **In `MotorIO.kt`:** Add `fun update() {}` with a default empty implementation.
2. **In `FtcMotor` (`FtcRevHubIO.kt`):** 
   - Add state variables: `isStalled = false`, `stallTimeMs = 0L`.
   - Add configuration properties: `spikeThresholdAmps = 8.0`, `stallVelocityThreshold = 10.0`, `stallTimeoutMs = 500L`.
   - Implement `override fun update()` with the logic defined above to manage `powerScale`.
3. **In `PinpointOdometryIO.kt`:** 
   - Instantiate a `SingleThreadExecutor`.
   - Wrap `driver.update()` in an executor `.submit { ... }`.
   - Enforce a 5ms timeout using `Future.get(5, TimeUnit.MILLISECONDS)`. Catch `TimeoutException` and `Exception` to cancel the Future and prevent `inputs` updates on failure.
4. **In `FtcImu` (`FtcRevHubIO.kt`):**
   - Apply the exact same Executor & Future timeout pattern to `imu.getRobotYawPitchRollAngles()` and `imu.getRobotAngularVelocity()`.

## Verification Method
- **Motor Checks**: Command power to an `FtcMotor`, artificially mock its velocity to `0.0`, and assert that `powerScale` drops to `0.0` after 500ms. Command `0.0` power and assert recovery.
- **Timeout Checks**: Mock the `PinpointDriverProxy` and `IMU` to sleep for `1000ms`. Verify that `updateInputs` completes in ~5ms and retains old values without throwing an unhandled exception. Run the standard build command (`./gradlew build` or `gradlew.bat build`) to ensure Kotlin compilation passes.
