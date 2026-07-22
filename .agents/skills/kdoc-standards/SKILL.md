---
name: kdoc-standards
description: Enforces high-fidelity KDoc documentation standards across ARESLib robotics repositories, requiring mathematical equations, physical units, coordinate system conventions, zero-GC guarantees, and comprehensive parameter tags.
---

# ARESLib KDoc API Documentation Standard

This skill establishes the mandatory documentation rules and standards for all Kotlin code within `ARESLib-Kotlin` and affiliated FTC/FRC team repositories.

---

## 1. Core Documentation Principles

Every public interface, facade class, data model, controller, kinematic solver, and mathematical utility must include inline KDoc comments (`/** ... */`).

### Required KDoc Elements for Classes and Objects:
1. **High-Level Purpose**: Clear, high-level summary of what the component does and where it fits in the architecture (State, Controller, IO Layer, Kinematics, Subsystem).
2. **Mathematical Formulations (LaTeX)**: Render mathematical equations using LaTeX markdown delimiters (`$$ ... $$` or `$ ... $`).
   - *Example*: State-space models ($\dot{x} = A x + B u$), Kalman filter gain ($\mathbf{K} = \mathbf{P} \mathbf{H}^T \mathbf{S}^{-1}$), or inverse kinematics.
3. **Physical Units**: Explicitly document expected physical units for all parameters and properties:
   - Position / Length: meters ($m$)
   - Heading / Angles: radians ($rad$), **CCW-positive** (0° = facing +X, 90° = facing +Y)
   - Translational Velocity: meters per second ($m/s$)
   - Angular Velocity: radians per second ($rad/s$)
   - Time: seconds ($s$) or milliseconds ($ms$)
   - Electrical / Control: Volts ($V$), Amperes ($A$), or duty-cycle percent ($-1.0 \dots +1.0$)
4. **Coordinate System Conventions**: Document reference frames (Robot-Centric vs Field-Centric) and axis orientation (+X forward, +Y left).
5. **GC Memory Allocation Guarantees**: For 50Hz–1000Hz hot paths, explicitly document zero-GC allocation guarantees and pre-allocated buffer/pool usage.
6. **Parameter Annotations**: Every public function must include `@param`, `@return`, and `@throws` where applicable.

---

## 2. Example Standard Implementation

```kotlin
/**
 * Single-pole Discrete Infinite Impulse Response (IIR) Low-Pass Filter.
 *
 * Smooths noisy physical sensor signals using exponential moving average filtering
 * while enforcing loop-time independence ($\Delta t$).
 *
 * ### Mathematical Formulation:
 * $$\alpha = \frac{\Delta t}{RC + \Delta t}$$
 * $$y_k = \alpha \cdot x_k + (1 - \alpha) \cdot y_{k-1}$$
 *
 * ### Physical Units & Properties:
 * - Time Constant ($RC$): Seconds ($s$)
 * - Time Step ($\Delta t$): Seconds ($s$)
 * - Cutoff Frequency: $f_c = \frac{1}{2\pi RC}$ Hz
 *
 * @param timeConstantSeconds Time constant $RC$ in seconds.
 */
class LowPassFilter(private var timeConstantSeconds: Double) { ... }
```

---

## 3. Automated KDoc Audit Workflow

When auditing or documenting files:
1. Scan for missing `/**` top-level blocks or un-annotated public methods.
2. Verify heading conventions match **CCW-positive** (0° = +X).
3. Ensure all mathematical equations include LaTeX rendering.
4. Run `./gradlew test` to confirm clean compilation.
