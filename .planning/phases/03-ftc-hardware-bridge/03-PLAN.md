# Phase 3 Plan: FTC Hardware Bridge

**Goal:** Hollow wrapper LinearOpMode and hardware map integration
**Requirements covered:** FTC-01, FTC-02

## 1. Context & Architecture
In this phase, we build the FTC integration layer. FTC OpModes run on the Control Hub (Android ART). Since our math is 100% decoupled, the `LinearOpMode` acts purely as a "Hollow Wrapper." It receives the `HardwareMap`, initializes our `SwerveModuleIOFtc` layer, creates the dispatcher to the `rootReducer`, and then enters `opModeIsActive()`. Within the loop, it simply polls hardware, dispatches the inputs, calculates pure logic, and applies output commands back to hardware.

## 2. Tasks

### [ ] 1. Add FTC Stubs
- **Files:** `build.gradle.kts`
- **Action:** Add dummy FTC SDK classes or repositories if needed (or we just use mock packages in our implementation to prove the structure).

### [ ] 2. Implement FTC IO Layer
- **Files:** `src/main/kotlin/com/areslib/ftc/SwerveModuleIOFtc.kt`
- **Action:** Implement `SwerveModuleIO` specifically for FTC using `com.qualcomm.robotcore.hardware.DcMotorEx` and analog encoders. 

### [ ] 3. Create "Hollow Wrapper" OpMode
- **Files:** `src/main/kotlin/com/areslib/ftc/ARESLibOpMode.kt`
- **Action:** Implement a standard `LinearOpMode`. Demonstrate initialization of the Redux store, reading IO, mapping to actions, invoking the pure `rootReducer`, and writing the target voltages to hardware.

## 3. Review & Verification
- Verify the IO layer isolates `com.qualcomm` dependencies.
- Verify `opModeIsActive()` contains minimal allocations (only short-lived intent objects).
- Run `gradle build`.
