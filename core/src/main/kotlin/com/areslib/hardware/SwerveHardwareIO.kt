package com.areslib.hardware

import com.areslib.state.DriveState
import com.areslib.telemetry.ITelemetry

/**
 * Interface representing the hardware input/output for the swerve drivetrain.
 *
 * This allows clean decoupling of the swerve drivetrain logic from CTRE/REV hardware,
 * facilitating unit testing, simulation, and future cross-platform (FTC/FRC) swerve support.
 */
interface SwerveHardwareIO : SubsystemIO {
    override fun logTelemetry(telemetry: ITelemetry, prefix: String) {
        val curr = DoubleArray(4)
        val enc = DoubleArray(4)
        getCurrents(curr)
        getEncoderPositions(enc)
        telemetry.putDoubleArray("$prefix/Currents", curr)
        telemetry.putDoubleArray("$prefix/EncoderPositions", enc)
    }

    /** Refreshes cached status signals from the hardware. */
    override fun refresh() {}

    /** Reads the drive state from the hardware. */
    fun read(): DriveState

    /** Writes target speeds back to the hardware. */
    fun write(driveState: DriveState)

    /** Gets measured motor supply currents. */
    fun getCurrents(out: DoubleArray) {}

    /** Gets measured absolute encoder positions. */
    fun getEncoderPositions(out: DoubleArray) {}

    /** Gets gyro absolute pitch degrees. */
    val pitchDegrees: Double
        get() = 0.0

    /** Gets gyro absolute roll degrees. */
    val rollDegrees: Double
        get() = 0.0

    /** Gets measured module linear velocities. */
    fun getModuleSpeeds(out: DoubleArray) {}

    /** Feeds AprilTag measurements into drivetrain's pose estimator. */
    fun addVisionMeasurement(pose: com.areslib.math.Pose2d, timestampSeconds: Double) {}

    /** Resets/seeds the underlying pose estimator. */
    fun seedPose(pose: com.areslib.math.Pose2d) {}

    /** Gets any active motor fault codes (bitfields). */
    fun getFaults(out: IntArray) {}

    /** Gets the signal latency in milliseconds of the swerve sensors. */
    val signalLatencyMs: Double
        get() = 0.0
}
