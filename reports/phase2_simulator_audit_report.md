# Phase 2 Simulator Audit Report

**Date:** July 4, 2026  
**Auditor:** Antigravity (Lead Code Reviewer, Team ARES 23247)  
**Scope:** `ARESLib-Kotlin/simulator` Module (Focus: Physics Stability & Logging Efficiency)

Thank you for your incredible hard work on the desktop simulator! The ability to run full HIL-style FTC code in a simulated environment is a massive achievement. However, we have some critical matrix solver and memory allocation issues to resolve to ensure championship-grade reliability.

## 📋 Summary Scorecard

| Pillar | Grade | Critical Item summary |
| :--- | :--- | :--- |
| 1. State Immutability (R1) | A | State transitions use pure `.copy()` correctly. |
| 2. Zero-GC Allocation (R2) | D | Telemetry array elements instantiate in hot loop. |
| 3. Time-Determinism (R3) | A | Utilizes `RobotClock.currentTimeMillis()` globally. |
| 4. Math Stability (R4) | F | Dyn4j solver matrix instability due to direct kinematic overrides. |
| 5. Hardware Timeout (R5) | N/A | Mock hardware operations are inherently non-blocking. |
| 6. API Design (R6) | A | Standard KDoc practices are maintained. |
| 7. Style & Conventions (R7) | A | Good use of Kotlin properties and functional idioms. |
| 8. Testing Coverage (R8) | N/A | External testing infrastructure out of scope. |
| 9. Code Portability (R9) | A | Excellent decoupling from Android/FTC SDK constraints. |
| 10. Memory Management (R10) | B | Basic array reuse for AdvScope, but missing pools for game pieces. |
| 11. Logging Efficiency (R11) | F | Reflection and primitive boxing in 50Hz telemetry pipeline. |
| 12. System Robustness (R12) | B | Simulation loop runs inside a global try/catch watchdog. |

---

## 🔍 Sectioned Detail

### 1. State Immutability & Redux Purity (R1) 🔒
✅ **Strengths:** Simulated state updates utilize `.copy()` accurately before publishing to NetworkTables.

### 2. Zero-GC Allocation in Hot-Paths (R2) ⚡
⚠️ **Findings:** The simulation loop dynamically allocates new object instances rather than utilizing index-based pre-allocated arrays.
| ID | Severity | Finding | Location |
| :--- | :--- | :--- | :--- |
| R2-F01 | [HIGH] | `DynamicElementPose` allocated inside the 50Hz `gamePieces` iteration loop. | `DesktopSimLauncher.kt:788` |

### 3. Time-Determinism & Clock Purity (R3) ⏰
✅ **Strengths:** Simulation loop timing safely implements `RobotClock.currentTimeMillis()`.

### 4. Math Stability & Boundary Guards (R4) 🎛️
⚠️ **Findings:** The Dyn4j physics constraint solver is being inadvertently overridden. Explicitly assigning `.linearVelocity` and `.angularVelocity` while also calculating proportional `applyForce` and `applyTorque` on the same timestep completely circumvents the Dyn4j LCP matrix collision solver, causing objects to phase through walls and rendering edge-case collision impulses useless.
| ID | Severity | Finding | Location |
| :--- | :--- | :--- | :--- |
| R4-F01 | [CRITICAL] | Direct kinematic overriding of `robotBody.linearVelocity` and `robotBody.angularVelocity` alongside applied forces destabilizes the Dyn4j solver matrix. | `DesktopSimLauncher.kt:722-723` |

### 5. Hardware Timeout & Thread Purity (R5) 🔌
✅ **Strengths:** Simulated hardware responses execute synchronously and securely within the physics thread logic.

### 6. API Design & KDoc Documentation 📝
✅ **Strengths:** Configuration parameters and loop semantics are clear and documented.

### 7. Style & Conventions (Kotlin-First) 🎨
✅ **Strengths:** Code utilizes clean Kotlin mapping loops.

### 8. Testing Coverage & Verification 🧪
✅ **Strengths:** Desktop execution is fully portable.

### 9. Code Portability & Decoupling 🚢
✅ **Strengths:** Simulator module decouples native libraries properly for headless cloud runs.

### 10. Memory Management & Object Pooling 🧹
✅ **Strengths:** `gamePieceDataBuffer` primitive array utilizes `.size` boundary checks to prevent unnecessary re-allocation.

### 11. Logging Efficiency 📊
⚠️ **Findings:** NetworkTables serialization is heavily dependent on `Gson.toJson()` which triggers deep reflection and severe string allocation on the Java Heap every loop iteration. Furthermore, `.hashCode()` on `Double` values initiates implicit primitive boxing to `java.lang.Double`, destroying the zero-allocation GC budget within a high-frequency telemetry publisher.
| ID | Severity | Finding | Location |
| :--- | :--- | :--- | :--- |
| R11-F01 | [HIGH] | Primitive `Double` boxing via `.hashCode()` computation inside a hot array traversal loop. | `DesktopSimLauncher.kt:762-763` |
| R11-F02 | [HIGH] | `Gson.toJson()` utilized for NT4 JSON serialization, producing extreme string allocation and reflection garbage. | `NT4FieldPublisher.kt:37` |

### 12. System Robustness & Failsafes 🛡️
✅ **Strengths:** Physics loop is protected by a global error handler preventing silent crashes.

---

## 🗺️ Roadmap to Compliance

### 🔴 Must Fix
1. **Remove Direct Kinematic Setters (R4):** Remove the explicit `robotBody.linearVelocity = velocity` overrides in `DesktopSimLauncher`. Manipulate the physical robot body purely through `applyForce()` and `applyTorque()` controllers so that Dyn4j impulses can accurately resolve edge-case matrix collisions.
2. **Eliminate Gson Reflection in NT4 Logging (R11):** Replace `Gson.toJson(elements)` in `NT4FieldPublisher` with a WPILib `StructPublisher` or a raw circular byte buffer to maintain a zero-allocation pipeline.

### 🟡 Should Fix
1. **Avoid Double Boxing (R11):** Modify the `currentBallsHash` delta calculation. Use `java.lang.Double.doubleToRawLongBits()` or raw coordinate tolerance bounds rather than `.hashCode()` to stop boxing primitives.
2. **Object Pooling for Poses (R2):** Implement a local object pool for `DynamicElementPose` instead of instantiating new instances within the 50Hz game loop.

### 🟢 Backlog
- Refactor WPILib `DoubleArrayTopic` serialization into unified structs to reduce individual NT4 entry bandwidth overhead.

---
*Keep up the stellar work! Together we'll forge the most mathematically stable and highest-performing simulator in FTC.*
