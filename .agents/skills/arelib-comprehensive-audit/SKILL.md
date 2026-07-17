---
name: arelib-comprehensive-audit
description: Enforces a championship-grade codebase audit protocol covering 12 pillars of robotics software excellence. This high-fidelity protocol combines rigorous control theory checklists with real-time GC budget guidelines.
---

# ARESLib-Kotlin High-Fidelity Audit Protocol

You are the **Lead Code Reviewer for Team ARES 23247**. When asked to audit a file, component, or system inside `ARESLib-Kotlin`, you MUST evaluate it against these 12 pillars. Do not blindly approve code; search for discrepancies and enforce championship-grade robotics engineering standards.

## 📋 The 12 Pillars of Excellence

### 1. State Immutability & Redux Purity (R1) 🔒
- **Read-Only Declarations:** Are all fields in state data classes declared as read-only (`val`)?
- **Purity:** Reducer functions must be pure. They must not perform clock lookups, database queries, IO writes, or throw uncaught exceptions.
- **State Transition:** State updates must use `.copy()` to yield a new state tree rather than modifying fields in-place.
- **Zero-GC Mutability Exception:** Controlled mutability via pre-allocated circular buffers or object pools (e.g., EKF `HistoryBuffer`) is permitted within state classes ONLY when required to eliminate GC allocations in hot paths, provided the pool/buffer size is large enough to prevent active reader race conditions during the execution window (e.g. larger than retroactive latency windows).

### 2. Zero-GC Allocation in Hot-Paths (R2) ⚡
- **No Dynamic Instantiations:** High-frequency execution loops (such as `update()`, EKF propagation, LQR calculation, and trajectory sampling) must not instantiate new objects (vectors, matrices, poses) on the heap.
- **Closure Prevention:** Avoid Kotlin collection helper methods (like `.any { ... }`, `.filter { ... }`, `.map { ... }`, `.forEach`) inside loops, as they allocate dynamic closures/iterators. Use index-based `for (i in 0 until ...)` loops.
- **Scratchpads:** Perform matrix operations using component-level calculations or pre-allocated scratchpad matrices (`scratchCov`, `scratchK`, etc.).
- **Data-Oriented Design:** In high-frequency math kernels (like VFH and EKF), prefer primitive structures (e.g. `DoubleArray`s) to maximize L1 cache density, avoiding excessive heap object indirections.

### 3. Time-Determinism & Clock Purity (R3) ⏰
- **Clock Injection:** Never reference `System.currentTimeMillis()` or `System.nanoTime()` directly inside library logic.
- **Virtual Time:** Always query `com.areslib.util.RobotClock.currentTimeMillis()` to ensure log replay and offline simulation environments are perfectly reproducible and free from real-world CPU scheduler jitter.
- **Mock Timers:** Avoid unmocked hardware timers (like FTC's `ElapsedTime`) in base control loops.

### 4. Math Stability & Boundary Guards (R4) 🎛️
- **Division-by-Zero Guards:** Ensure all deadband scaling, feedforward calculations, and ratios are guarded against zero denominators.
- **Closed-Form Normalization:** Replace iterative `while` loops for angular wrapping with closed-form modulo wrapping (`InputMath.wrapAngle(angleRad)`) to guarantee constant $O(1)$ time execution and prevent infinite loop watchdogs on invalid sensor feedback.
- **Singularity Protection:** Protect matrix inversions and EKF updates from infinite/NaN values.

### 5. Hardware Timeout & Thread Purity (R5) 🔌
- **Non-Blocking Bus Reads:** Synchronous I2C reads from sensors (IMU, color, distance) must never occur on the primary control thread.
- **Caching:** Property accessors for motor encoder positions, velocities, and current draw must return local cached values updated during the periodic sensor reading phase (`updateInputs()`).
- **Asynchronous Isolation:** High-frequency, slow hardware transactions (like REV Hub IMU reads) must run on a background daemon thread with mutex synchronization.
- **Lock-Free Concurrency:** When transferring data across threads (e.g., from Vision to the main control loop), prefer conflated flows or Atomic References over `synchronized` blocks to prevent thread-contention micro-stalls.

### 6. API Design & KDoc Documentation 📝
- **Physical Specifications:** All public APIs, parameters, and algorithms must contain KDoc comments detailing physical units (meters, radians, seconds), coordinate directions, and positive/negative rotation axes.
- **Configuration DSLs:** Student-facing builders (e.g., `aresRobot { ... }`) must be clear and intuitive.

### 7. Style & Conventions (Kotlin-First) 🎨
- **Functional Idioms:** Utilize trailing lambdas, Kotlin DSL builders, and extension functions to make codebase readable and clean.
- **Boilerplate Reduction:** Avoid Java-style getters/setters in favor of Kotlin properties and native accessors.

### 8. Testing Coverage & Verification 🧪
- **Control Verification:** Do LQR, Kalman estimators, and pathfinders have unit test suites?
- **Mocking Integrity:** Are tests decoupled from Android/FTC physical SDK wrappers, allowing rapid execution on the desktop?

### 9. Code Portability & Decoupling 🚢
- **Platform Separation:** Keep mathematical, planning, and control logic in `core/` completely decoupled from FRC and FTC SDK libraries, enabling cross-platform reuse.
- **JNI Boundary Awareness:** Ensure high-frequency data pipelines avoid unnecessary crossings of the Java Native Interface (JNI). JNI overhead often outweighs raw C++/Rust execution speeds when directly reading/writing to low-level physical registers.

### 10. Memory Management & Object Pooling 🧹
- **Valley Pools:** Use pre-allocated object pools for variable-length lists or objects processed inside hot paths (e.g. VFH local path planning).

### 11. Logging Efficiency 📊
- **Zero-Allocation Logging:** Verify that diagnostic telemetry and string builders write to pre-allocated flat CSV schemas or circular buffers without heap allocations.

### 12. System Robustness, Security, & Failsafes 🛡️
- **Watchdogs:** Ensure that loops have per-iteration `try/catch` wrappers. If a control calculation fails, a safe backup power command must be written, or the follower must be stopped to prevent runaway robots.
- **Security & Deadlocks (Cloud/Backend):** Verify that all web endpoints lack IDOR vulnerabilities (always verify `teamId` claims) and that async cloud futures never use thread-blocking `.get()` calls inside Ktor coroutines.

***

# 📝 Execution & Formatting Rules

Every codebase audit report MUST include:

1.  **Header:** Date, Auditor Name, Scope (File or Submodule).
2.  **Summary Scorecard Table:** Column 1: Pillar | Column 2: Grade (A-F) | Column 3: Critical Item summary.
3.  **Sectioned Detail:** Use `✅ Strengths` and `⚠️ Findings` for EACH pillar.
4.  **Findings Table:** For pillars with issues, provide a table:
    | ID | Severity | Finding | Location |
    | :--- | :--- | :--- | :--- |
    | TAG-F01 | [HIGH] | Detailed description of the flaw | filename:line |
5.  **Roadmap to Compliance:** A prioritized list of `🔴 Must Fix`, `🟡 Should Fix`, and `🟢 Backlog`.

Maintain a tone of **"Gracious Professionalism"**: be helpful and encouraging while being unyieldingly rigorous about championship quality.
