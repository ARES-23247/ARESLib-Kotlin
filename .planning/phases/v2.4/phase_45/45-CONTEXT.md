# Phase 45: Chronological Asynchronous Vision Measurement Buffer - Context

**Gathered:** 2026-05-18
**Status:** Ready for planning
**Mode:** Auto-generated (Autonomous Smart Discuss)

<domain>
## Phase Boundary

Build a thread-safe, sorted sliding-window buffer for asynchronous vision measurements to support retroactive pose estimation updates.

</domain>

<decisions>
## Implementation Decisions

### 1. Chronological Sorting & Buffer Capacity
- Class: `com.areslib.hardware.vision.VisionMeasurementBuffer`
- Underlying Structure: A thread-safe `java.util.concurrent.ConcurrentSkipListSet` or a synchronized custom `ArrayList` sorted by `timestampMs`. Given our target is Java/Kotlin for robotics, `ConcurrentSkipListSet` with a comparator or a synchronized list is extremely robust. Let's use `ConcurrentSkipListSet<VisionMeasurement>` using a custom comparator.
- Capacity: Sliding window holding up to `1.5 seconds` of history or `100 entries`. Measurements older than 1.5 seconds are automatically evicted.

### 2. Thread-Safety
- Access to the buffer must be completely thread-safe as vision inputs come from separate Camera threads (e.g., Limelight or FTC VisionPortal async threads).
- Use `synchronized` or explicit locks (`ReentrantLock`) for atomic query-and-evict operations.

### 3. Key APIs
- `fun addMeasurement(measurement: VisionMeasurement)`: Adds a measurement, maintaining sorted order and evicting expired entries.
- `fun getMeasurementsBetween(startMs: Long, endMs: Long): List<VisionMeasurement>`: Retrieves all measurements in a closed interval.
- `fun clearBefore(timestampMs: Long)`: Deletes all entries older than `timestampMs`.
- `fun getAll(): List<VisionMeasurement>`: Returns a copy of the buffer's contents in ascending chronological order.

</decisions>

<code_context>
## Existing Code Insights
- `VisionMeasurement` is modeled in `com.areslib.state.VisionMeasurement`.
- Currently, `VisionState` holds a plain list of `measurements`. We will hook this buffer into the vision system to provide structured chronological queries.

</code_context>

<specifics>
## Specific Ideas
- Eviction strategy: Every time a measurement is added, check the timestamp of the newest measurement `T_max` and remove any entries with `timestampMs < T_max - maxHistoryMs`.

</specifics>

<deferred>
## Deferred Ideas
- Dynamic latency compensation based on variable network jitter (deferred to Kalman Filter tuning).

</deferred>
