package com.areslib.ftc.telemetry

/**
 * Tracks nanosecond-accurate sub-section execution timings and loop overrun counts for FTC robot loops.
 */
class FtcLoopProfiler {
    var profBulkCacheMs = 0.0
        private set
    var profHardwareInputsMs = 0.0
        private set
    var profPinpointMs = 0.0
        private set
    var profVisionMs = 0.0
        private set

    private var loopOverrunCount = 0

    /**
     * recordSensorsProfiling declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun recordSensorsProfiling(bulkMs: Double, inputsMs: Double, pinpointMs: Double, visionMs: Double) {
        profBulkCacheMs = bulkMs
        profHardwareInputsMs = inputsMs
        profPinpointMs = pinpointMs
        profVisionMs = visionMs
    }

    /**
     * publishSensorsProfiling declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun publishSensorsProfiling(telemetryManager: FtcTelemetryManager) {
        val dl = telemetryManager.dataLoggingTelemetry
        dl.putNumber("Profiling/BulkCacheClear_ms", profBulkCacheMs)
        dl.putNumber("Profiling/HardwareInputs_ms", profHardwareInputsMs)
        dl.putNumber("Profiling/Pinpoint_ms", profPinpointMs)
        dl.putNumber("Profiling/Vision_ms", profVisionMs)
    }

    /**
     * recordAndPublishLoopDiagnostics declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun recordAndPublishLoopDiagnostics(
        telemetryManager: FtcTelemetryManager,
        t0: Long,
        t1: Long,
        t2: Long,
        t3: Long,
        t4: Long
    ) {
        val dl = telemetryManager.dataLoggingTelemetry
        val totalTimeMs = (t4 - t0) / 1_000_000.0
        if (totalTimeMs > 25.0) {
            loopOverrunCount++
        }
        dl.putNumber("Diagnostics/LoopOverruns", loopOverrunCount.toDouble())
        dl.putNumber("Profiling/ReadSensors_ms", (t1 - t0) / 1_000_000.0)
        dl.putNumber("Profiling/PowerManager_ms", (t2 - t1) / 1_000_000.0)
        dl.putNumber("Profiling/Subsystems_ms", (t3 - t2) / 1_000_000.0)
        dl.putNumber("Profiling/Telemetry_ms", (t4 - t3) / 1_000_000.0)
        dl.putNumber("Profiling/Total_ms", totalTimeMs)
    }
}
