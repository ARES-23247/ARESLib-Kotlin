package com.areslib.ftc

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode
import com.qualcomm.robotcore.hardware.DcMotorEx
import com.qualcomm.robotcore.hardware.AnalogInput
import com.areslib.state.RobotState
import com.areslib.reducer.rootReducer
import com.areslib.action.RobotAction
import com.areslib.hardware.drive.SwerveModuleInputs

class ARESLibOpMode : LinearOpMode() {

    override fun runOpMode() {
        // 1. Hardware Map integration
        val driveMotor = hardwareMap.get(DcMotorEx::class.java, "driveMotor")
        val steerMotor = hardwareMap.get(DcMotorEx::class.java, "steerMotor")
        val analogEncoder = hardwareMap.get(AnalogInput::class.java, "analogEncoder")
        
        val swerveIO = SwerveModuleIOFtc(driveMotor, steerMotor, analogEncoder)
        val swerveInputs = SwerveModuleInputs()
        
        // 2. Initialize pure state and action logger
        var currentState = RobotState()
        val actionLogger = com.areslib.action.ActionLogger()

        waitForStart()

        try {
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
                
                // Log action to JSONL
                actionLogger.logAction(action)
                
                // Calculate pure logic / output commands (kinematics - Phase 4)
                val outputVoltage = currentState.drive.odometryX * 0.1 // stub logic
                
                // Write to hardware
                driveMotor.power = outputVoltage
            }
        } finally {
            actionLogger.stop()
        }
    }
}
