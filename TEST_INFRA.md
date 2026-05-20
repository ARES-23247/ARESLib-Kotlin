# E2E Test Infra: ARESLib-Kotlin Fault Tolerance

## Test Philosophy
- Opaque-box, requirement-driven. No dependency on implementation design.
- Methodology: Category-Partition + BVA + Pairwise + Workload Testing.

## Feature Inventory
| # | Feature | Source (requirement) | Tier 1 | Tier 2 | Tier 3 |
|---|---------|---------------------|:------:|:------:|:------:|
| 1 | Math Bounds Checking | ORIGINAL_REQUEST R1 | 5 | 5 | ✓ |
| 2 | Hot-path GC Avoidance | ORIGINAL_REQUEST R1 | 5 | 5 | ✓ |
| 3 | PID Output Clamping | ORIGINAL_REQUEST R1 | 5 | 5 | ✓ |
| 4 | State Immutability | ORIGINAL_REQUEST R2 | 5 | 5 | ✓ |
| 5 | Safe Action Reduction | ORIGINAL_REQUEST R2 | 5 | 5 | ✓ |
| 6 | I2C/UART Read Timeouts | ORIGINAL_REQUEST R3 | 5 | 5 | ✓ |
| 7 | Motor Stall Detection | ORIGINAL_REQUEST R3 | 5 | 5 | ✓ |
| 8 | OpMode Loop Failsafe | ORIGINAL_REQUEST R4 | 5 | 5 | ✓ |
| 9 | Loop Time Watchdog | ORIGINAL_REQUEST R4 | 5 | 5 | ✓ |

## Test Architecture
- Test runner: `./gradlew test --tests "com.areslib.e2e.*"`
- Test case format: JUnit 5 test classes under `core/src/test/kotlin/com/areslib/e2e/`.
- Directory layout:
  - `core/src/test/kotlin/com/areslib/e2e/tier1/`
  - `core/src/test/kotlin/com/areslib/e2e/tier2/`
  - `core/src/test/kotlin/com/areslib/e2e/tier3/`
  - `core/src/test/kotlin/com/areslib/e2e/tier4/`

## Real-World Application Scenarios (Tier 4)
| # | Scenario | Features Exercised | Complexity |
|---|----------|--------------------|------------|
| 1 | Full Auto with Sensor Disconnect | 6, 8, 9 | High |
| 2 | Pathing through Singularities | 1, 3, 5 | Medium |
| 3 | Teleop with Motor Stall | 7, 8, 9 | High |
| 4 | Extreme Noise Inputs to EKF | 1, 4, 5 | Medium |
| 5 | High-Frequency Loop Overrun | 2, 8, 9 | High |

## Coverage Thresholds
- Tier 1: ≥5 per feature
- Tier 2: ≥5 per feature (where boundaries exist)
- Tier 3: pairwise coverage of major feature interactions
- Tier 4: ≥5 realistic application scenarios
