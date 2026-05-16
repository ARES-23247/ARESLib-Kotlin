package com.areslib.ftc

import com.qualcomm.robotcore.hardware.LinearOpMode
import com.qualcomm.robotcore.hardware.DcMotorEx
import com.qualcomm.robotcore.hardware.AnalogInput
import com.areslib.state.RobotState
import com.areslib.reducer.rootReducer
import com.areslib.action.RobotAction

class ARESLibOpMode : LinearOpMode() {

    override fun runOpMode() {
        // 1. Hardware Map integration
        val driveMotor = hardwareMap.get(DcMotorEx::class.java, "driveMotor")
        val steerMotor = hardwareMap.get(DcMotorEx::class.java, "steerMotor")
        val analogEncoder = hardwareMap.get(AnalogInput::class.java, "analogEncoder")
        
        val swerveIO = SwerveModuleIOFtc(driveMotor, steerMotor, analogEncoder)
        val swerveInputs = com.areslib.frc.SwerveInputs()
        
        // 2. Initialize pure state
        var currentState = RobotState()

        // Wait for start...
        
        while (opModeIsActive()) {
            // Read hardware
            swerveIO.updateInputs(swerveInputs)
            
            // Generate immutable action
            val action = RobotAction.DriveHardwareUpdate(
                xVelocity = swerveInputs.driveVelocityRadsPerSec, // Simplify mapping
                yVelocity = 0.0,
                angularVelocity = 0.0,
                deltaX = 0.0,
                deltaY = 0.0,
                deltaHeading = swerveInputs.steerAbsolutePositionRads,
                timestampMs = swerveInputs.timestampMs
            )
            
            // Dispatch to pure reducer
            currentState = rootReducer(currentState, action)
            
            // Calculate pure logic / output commands (kinematics - Phase 4)
            val outputVoltage = currentState.drive.odometryX * 0.1 // stub logic
            
            // Write to hardware
            driveMotor.setPower(outputVoltage)
        }
    }
}
