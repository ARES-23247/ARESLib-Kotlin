package com.areslib.ftc

import com.qualcomm.robotcore.hardware.HardwareMap
import com.qualcomm.robotcore.hardware.DcMotorEx
import com.qualcomm.robotcore.hardware.IMU
import com.areslib.ftc.hardware.FtcMotor
import com.areslib.ftc.hardware.FtcImu
import com.areslib.hardware.ImuInputs
import com.areslib.action.RobotAction

/**
 * Concrete single-motor testing testbed facade.
 * Extends FtcBaseRobot and uses null sensors for AprilTag and pinpoint localization.
 */
class FtcAresRobot(hardwareMap: HardwareMap) : FtcBaseRobot(hardwareMap, pinpointName = null, limelightName = null) {
    
    // 1. Concrete FTC Hardware wrappers
    val motor = FtcMotor(hardwareMap.get(DcMotorEx::class.java, "revMotor"))
    val imu = FtcImu(hardwareMap.get(IMU::class.java, "imu"))
    
    private val imuInputs = ImuInputs()

    init {
        // Register the test motor with the power manager for current budgeting
        powerManager.registerMotors(listOf(motor))
    }

    override fun updateHardwareInputs() {
        val timestamp = com.areslib.util.RobotClock.currentTimeMillis()
        
        // 1. Read hardware inputs
        imu.updateInputs(imuInputs)

        // 2. Dispatch to the underlying Redux store
        store.dispatch(RobotAction.DriveHardwareUpdate(
            xVelocity = imuInputs.yawVelocityRadPerSec,
            yVelocity = 0.0,
            angularVelocity = imuInputs.yawVelocityRadPerSec,
            deltaX = 0.0,
            deltaY = 0.0,
            deltaHeading = 0.0,
            timestampMs = timestamp,
            pitchDegrees = Math.toDegrees(imuInputs.pitchRadians),
            rollDegrees = Math.toDegrees(imuInputs.rollRadians)
        ))
    }

    override fun updateSubsystems(dtSeconds: Double, batteryVoltage: Double, powerScale: Double) {
        // Write outputs to motors based on computed Redux state
        val targetPower = store.state.drive.odometryX * 0.1

        motor.powerScale = powerScale
        motor.power = targetPower
    }

    override fun publishRobotTelemetry(timestamp: Long) {
        telemetryManager.dataLoggingTelemetry.putNumber("test_motor_current", motor.currentAmps)
    }

    override fun safeHardware() {
        try {
            motor.power = 0.0
        } catch (_: Throwable) {}
    }
}
