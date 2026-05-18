package com.areslib.ftc

import com.qualcomm.robotcore.hardware.DcMotorEx
import com.qualcomm.robotcore.hardware.AnalogInput
import com.areslib.hardware.drive.SwerveModuleIO
import com.areslib.hardware.drive.SwerveModuleInputs

class SwerveModuleIOFtc(
    private val driveMotor: DcMotorEx,
    private val steerMotor: DcMotorEx,
    private val analogEncoder: AnalogInput
) : SwerveModuleIO {

    override fun updateInputs(inputs: SwerveModuleInputs) {
        // FTC has no synchronous CAN block like Phoenix 6, so we poll individually.
        inputs.drivePositionRads = driveMotor.currentPosition * 2.0 * Math.PI / 2048.0
        inputs.driveVelocityRadsPerSec = driveMotor.velocity * 2.0 * Math.PI / 2048.0
        inputs.steerAbsolutePositionRads = analogEncoder.voltage / 3.3 * 2.0 * Math.PI
        inputs.timestampMs = System.currentTimeMillis() // System clock fallback
    }
}
