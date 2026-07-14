---
name: subsystem-generator
description: Generates boilerplate files for a new robot subsystem using the Redux state and decoupled IO layers pattern in ARESLib-Kotlin
---

# Subsystem Generation Protocol

Use this skill when the developer requests to create or add a new subsystem (e.g. Climber, Intake, Shooter, Arm, Claw, Turret). You must generate the complete 6-file suite adhering strictly to the architecture guidelines:

## Architectural Requirements
1. **Core Package (`com.areslib.state`)**:
   - `[SubsystemName]State.kt`: Immutable data class containing configuration and current state metrics.
   - `[SubsystemName]Action.kt`: Sealed class of transition intents (e.g. `SetTarget`, `UpdateSensors`).
   - `[SubsystemName]Reducer.kt`: Pure, side-effect-free reducer mapping actions to state updates via `.copy()`.
2. **Hardware Abstraction (`com.areslib.hardware`)**:
   - `[SubsystemName]IO.kt`: A thin, clean interface extending `AutoCloseable` or `SubsystemIO` with `writeTarget(...)` and `read()` methods.
3. **FTC Implementation (`com.areslib.ftc`)**:
   - `Ftc[SubsystemName]IO.kt`: Wraps FTC SDK DcMotorEx/Servos/Sensors. Uses throttled telemetry reads for current draw to satisfy the Zero-GC budget.
4. **Mock Implementation (`com.areslib.ftc`)**:
   - `Mock[SubsystemName]IO.kt`: In-memory physics simulation. Instead of flat mocks, integrate standard mathematical physics models from `com.areslib.sim` to simulate dynamics:
     - **For velocity-based subsystems (Shooter, Flywheel):** Integrate `FlywheelSim`.
     - **For angular gravity-loaded subsystems (Intake pivot, Hinge arm):** Integrate `IntakePivotSim`.
     - **For linear translation subsystems (Lift, Elevator):** Integrate basic linear mass-spring-damper math.
     - **Time Step calculation:** Track the last refresh timestamp and compute the elapsed time step using `RobotClock.currentTimeMillis()` to ensure the physics simulation is perfectly deterministic.
5. **Controller Layer (`com.areslib.control`)**:
   - `[SubsystemName]Controller.kt`: Periodic loop reading target inputs, applying closed-loop (PID/feedforward) output voltage calculations, writing to `IO`, and dispatching telemetry updates.

## Memory Budget Invariant
- **Zero Heap Allocations**: All update loops must recycle pre-allocated observation dataclasses (like `ClimberInputData`). Never instantiate new buffers or wrappers inside the hot path.

## Mock Physics Templates

When writing `Mock[SubsystemName]IO.kt`, use one of the following integration patterns based on the subsystem type:

### A. Rotational Flywheel/Shooter Subsystem Template
```kotlin
package com.areslib.ftc

import com.areslib.hardware.SubsystemIO
import com.areslib.sim.FlywheelSim
import com.areslib.util.RobotClock

class MockShooterIO : ShooterIO {
    private val sim = FlywheelSim(
        momentOfInertia = 0.003,
        resistance = 0.05,
        frictionCoeff = 0.0008
    )
    private var appliedVoltage = 0.0
    private var lastTimeMs = RobotClock.currentTimeMillis()

    override fun setVoltage(volts: Double) {
        this.appliedVoltage = volts
    }

    override fun getVelocityRpm(): Double {
        return sim.velocityRpm
    }

    override fun refresh() {
        val now = RobotClock.currentTimeMillis()
        val dt = (now - lastTimeMs) / 1000.0
        lastTimeMs = now
        
        // Guard against initial large time steps
        if (dt > 0.0 && dt < 0.1) {
            sim.update(appliedVoltage, dt)
        }
    }
}
```

### B. Angular Pivot/Arm Subsystem Template
```kotlin
package com.areslib.ftc

import com.areslib.hardware.SubsystemIO
import com.areslib.sim.IntakePivotSim
import com.areslib.util.RobotClock

class MockArmIO : ArmIO {
    private val sim = IntakePivotSim(
        armMassKg = 2.5,
        lengthToComMeters = 0.3,
        gearRatio = 60.0
    )
    private var appliedVoltage = 0.0
    private var lastTimeMs = RobotClock.currentTimeMillis()

    override fun setVoltage(volts: Double) {
        this.appliedVoltage = volts
    }

    override fun getAngleDegrees(): Double {
        return sim.angleDegrees
    }

    override fun refresh() {
        val now = RobotClock.currentTimeMillis()
        val dt = (now - lastTimeMs) / 1000.0
        lastTimeMs = now
        
        if (dt > 0.0 && dt < 0.1) {
            sim.update(appliedVoltage, dt)
        }
    }
}
```

## Auto-Registration Template
Remind the developer to register the new subsystem state in the root state constructor:
```kotlin
val state = RobotState(
    superstructure = SuperstructureState(
        states = mapOf("[subsystem_key]" to [SubsystemName]State())
    )
)
```
