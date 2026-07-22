package com.areslib.ftc.drivetrain

import com.areslib.Store
import com.areslib.ftc.calibration.FtcMecanumCalibrationController
import com.areslib.ftc.telemetry.FtcTelemetryManager
import com.areslib.kinematics.MecanumKinematics
import com.areslib.state.TuningState
import com.areslib.subsystem.DriveSubsystem
import com.areslib.subsystem.MecanumDriveFacade

/**
 * Class implementation for Mecanum Kinematics Controller.
 *
 * Hardware IO abstraction layer bridging physical robot sensors and actuators into immutable Redux state representations.
 */
class MecanumKinematicsController(
    val mecanumIO: MecanumHardwareIO,
    private val drive: DriveSubsystem,
    private val mecanumDrive: MecanumDriveFacade,
    private val calibrationController: FtcMecanumCalibrationController
) {
    var kinematics = MecanumKinematics(0.45, 0.45)
        private set

    /**
     * updateTuning declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun updateTuning(currentTuning: TuningState) {
        kinematics = MecanumKinematics(currentTuning.trackWidthMeters, currentTuning.wheelBaseMeters)
        mecanumIO.kS = currentTuning.driveFeedforward.kS
        mecanumIO.slewRateLimit = currentTuning.driveSlewRateLimit
        mecanumIO.ticksPerMeter = currentTuning.ticksPerMeter
        if (currentTuning.driveFeedforward.kV > 1e-4) {
            mecanumIO.maxWheelSpeedMetersPerSecond = 1.0 / currentTuning.driveFeedforward.kV
        }
        val gains = currentTuning.motorGains
        if (gains != null) {
            mecanumIO.updateMotorGains(gains.kP, gains.kI, gains.kD)
        }

        val maxSpeed = mecanumIO.maxWheelSpeedMetersPerSecond
        val maxAngularSpeed = maxSpeed / kinematics.k
        drive.maxSpeedMps = maxSpeed
        mecanumDrive.maxSpeedMps = maxSpeed
        mecanumDrive.maxAngularSpeedRps = maxAngularSpeed
    }

    /**
     * updateSubsystems declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun updateSubsystems(
        store: Store,
        batteryVoltage: Double,
        dtSeconds: Double,
        telemetryManager: FtcTelemetryManager,
        onResetTuning: () -> Unit
    ) {
        val isCalibrationHandlingDrive = calibrationController.updateSubsystems(
            store = store,
            batteryVoltage = batteryVoltage,
            mecanumIO = mecanumIO,
            telemetryManager = telemetryManager,
            onResetTuning = onResetTuning
        )

        if (!isCalibrationHandlingDrive) {
            mecanumIO.drive(store.state.drive, kinematics, batteryVoltage, dtSeconds)
        }
    }
}
