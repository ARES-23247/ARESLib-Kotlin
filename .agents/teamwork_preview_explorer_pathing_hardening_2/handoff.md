# Handoff Report: Milestone 1.3 Pathing Hardening

## 1. Observation
- `ThetaStarPlanner.kt:51-54` computes grid coordinate variables by dividing by `costmap.resolutionMeters`. There is no check if `resolutionMeters` is `0.0`, `NaN`, or `Infinity`.
- The `start` and `end` coordinate values (`Translation2d` properties) are directly used in calculations without checking for `NaN` or `Infinity`.
- `ThetaStarPlanner.kt:62-64` instantiates a `PriorityQueue<Node>`, `HashSet<Int>`, and `HashMap<Int, Node>` on every call to `plan()`.
- `ThetaStarPlanner.kt:66-69` lazily creates `Node(x, y)` objects inside the `getOrPut` block, causing object allocations per explored grid cell.
- Kotlin's `HashSet<Int>` and `HashMap<Int, Node>` implicitly box the primitive `Int` keys into `java.lang.Integer` objects, generating massive GC pressure during dense path sampling.
- `ThetaStarPlanner` is an `object` (Singleton).

## 2. Logic Chain
1. **Bounds Checking:** If `costmap.resolutionMeters` is `0.0`, `start.x / 0.0` evaluates to `Infinity`. Calling `.roundToInt()` on `Infinity` or `NaN` Double values can either throw exceptions or silently evaluate to `0`, causing the planner to calculate incorrect trajectories or crash unexpectedly. Strict preconditions will fail-fast and explicitly block math propagation errors.
2. **GC Allocations in Trajectory Sampling:** A large grid expansion will perform thousands of inserts into `HashSet<Int>` and `HashMap<Int, Node>`. Because Java/Kotlin collections do not support unboxed primitive generics, each insert allocates at least one `Integer` object and one `Map.Entry`/Node object.
3. **Primitive Pools:** To eliminate these allocations, the node metadata (g-cost, h-cost, parent-pointer) should be flattened into parallel primitive arrays (`DoubleArray`, `IntArray`), and the `closedSet` should become a `BooleanArray` or `BitSet`. The `PriorityQueue` should be replaced by a primitive integer heap mapping to grid keys.
4. **Thread Safety:** Because `ThetaStarPlanner` is a singleton, changing local variables to pre-allocated class fields makes it thread-unsafe. A safe pattern is wrapping these arrays in a state object and holding it in a `ThreadLocal`, or dynamically pulling from a simple object pool.

## 3. Caveats
- Using primitive array buffers requires knowing the maximum grid bounds (width * height) in advance, or implementing a capacity-check-and-resize mechanism.
- The `ThreadLocal` or pooling approach is required to keep the singleton safe for concurrent path requests.
- While the internal node objects are eliminated, `reconstructPath()` still allocates `Translation2d` waypoints for the final output path, which is typically acceptable since it's orders of magnitude fewer allocations than state space exploration.

## 4. Conclusion
To satisfy M1.3:
1. **Numerical Constraints:** Inject `require(costmap.resolutionMeters > 0.0 && !costmap.resolutionMeters.isInfinite())` and `require(start.isFinite() && end.isFinite())` (assuming an `isFinite()` helper for `Translation2d`) at the very top of `plan()`.
2. **Buffer Pools:** Refactor `ThetaStarPlanner` to eliminate `Node`, `HashSet<Int>`, and `HashMap<Int, Node>`. Replace them with parallel primitive arrays (`gCosts`, `hCosts`, `parents`, `closedSet`) bounded by `costmap.widthCells * costmap.heightCells`. Use a primitive integer heap for the open set. Store these in a reusable `PlannerState` container fetched via `ThreadLocal` to ensure zero allocations without breaking concurrency.

## 5. Verification Method
- **Code Inspection:** Verify `core/src/main/kotlin/com/areslib/pathing/ThetaStarPlanner.kt` has explicit `require` guards. Ensure no `HashSet`, `HashMap`, or `Node` instantiations exist in the `plan` method loop.
- **Unit Testing:** Run the FTC pathing test suite (e.g. `./gradlew test` or Android equivalent) to ensure logical equivalency of the refactored Theta* implementation.
- **Memory Profiling:** Run an integration test with Android Studio's Memory Profiler or VisualVM. Call `plan()` repeatedly on a large map; the allocation graph should show zero new GC pressure during the `plan()` execution phase compared to the previous baseline.
