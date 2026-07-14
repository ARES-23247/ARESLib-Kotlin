package com.areslib.ftc

import com.qualcomm.robotcore.hardware.HardwareMap
import com.qualcomm.robotcore.hardware.DcMotorEx
import com.qualcomm.robotcore.hardware.IMU
import com.areslib.ftc.hardware.FtcMotor
import com.areslib.ftc.hardware.FtcImu
import com.areslib.hardware.sensor.ImuInputs
import com.areslib.action.RobotAction
import com.areslib.subsystem.DriveSubsystem

/**
 * Concrete single-motor testing testbed facade.
 * Extends FtcBaseRobot and uses null sensors for AprilTag and pinpoint localization.
 */
class FtcTestbedRobot(hardwareMap: HardwareMap) : FtcBaseRobot(hardwareMap, pinpointName = null, limelightName = null) {

    // Subsystem Facades
    val drive = DriveSubsystem(store)
    
    // 1. Concrete FTC Hardware wrappers
    val motor = FtcMotor(hardwareMap.get(DcMotorEx::class.java, "revMotor"))
    val imu = FtcImu(hardwareMap.get(IMU::class.java, "imu"))
    
    private val imuInputs = ImuInputs()

    init {
        com.areslib.hardware.HardwareRegistry.registerMotor("revMotor", motor)
        com.areslib.hardware.HardwareRegistry.registerDevice("IMU", imu)
    }

    override fun updateHardwareInputs() {
        val timestamp = com.areslib.util.RobotClock.currentTimeMillis()
        com.areslib.hardware.HardwareRegistry.refreshAll()
        imu.updateInputs(imuInputs)

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
        val targetVolts = (store.state.drive.odometryX * 0.1) * 12.0
        motor.setVoltage(targetVolts, batteryVoltage)
    }

    override fun publishRobotTelemetry(timestamp: Long) {}

    override fun safeHardware() {
        com.areslib.hardware.HardwareRegistry.safeAll()
    }
}

