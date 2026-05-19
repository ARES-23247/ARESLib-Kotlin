package com.areslib.ftc

import com.qualcomm.robotcore.hardware.HardwareMap
import com.qualcomm.robotcore.hardware.DcMotorEx
import com.qualcomm.robotcore.hardware.IMU
import com.areslib.subsystem.AresRobot
import com.areslib.ftc.hardware.FtcMotor
import com.areslib.ftc.hardware.FtcImu
import com.areslib.hardware.ImuInputs
import com.areslib.action.RobotAction

class FtcAresRobot(hardwareMap: HardwareMap) : AresRobot() {
    
    // 1. Concrete FTC Hardware wrappers
    val motor = FtcMotor(hardwareMap.get(DcMotorEx::class.java, "revMotor"))
    val imu = FtcImu(hardwareMap.get(IMU::class.java, "imu"))
    
    private val imuInputs = ImuInputs()

    /**
     * Coordinated update frame:
     * 1. Read hardware sensors (IMU, Encoders).
     * 2. Dispatch hardware update actions to the central Redux store.
     * 3. Run the math controllers and apply calculated voltages to motor outputs.
     * 4. Perform background telemetry logging (student does not write any logging boilerplate).
     */
    fun update() {
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

        store.dispatch(RobotAction.SuperstructureSensorUpdate(
            flywheelRpm = 0.0,
            cowlAngle = 0.0,
            intakeAngle = 0.0,
            pieceDetected = false,
            timestampMs = timestamp
        ))

        // 3. Write outputs to motors based on computed Redux state
        // (Use odometry X target voltage compensation as simple loop feedback)
        val targetPower = store.state.drive.odometryX * 0.1
        motor.power = targetPower
    }
}
