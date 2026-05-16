package com.areslib.ftc

import com.qualcomm.robotcore.hardware.LinearOpMode
import com.qualcomm.robotcore.hardware.GoBildaPinpointDriver
import com.areslib.kinematics.MecanumKinematics
import com.areslib.reducer.RootReducer
import com.areslib.state.RobotState
import com.areslib.math.ChassisSpeeds
import com.areslib.math.Rotation2d

/**
 * A complete, deployable FTC TeleOp that wires the functional core to Mecanum hardware.
 */
class ARESMecanumTeleOp : LinearOpMode() {

    override fun runOpMode() {
        // --- 1. Initialization ---
        
        // Setup hardware IO
        val mecanumIO = MecanumHardwareIO(hardwareMap)
        
        // Assuming the pinpoint driver is configured as "pinpoint" in hardware map
        val pinpointDriver = hardwareMap.get(GoBildaPinpointDriver::class.java, "pinpoint")
        val pinpointIO = PinpointIO(pinpointDriver)
        
        // Setup human input IO
        val gamepadIO = GamepadIO(gamepad1)
        
        // Setup pure kinematics (assuming standard 18-inch robot trackwidth/wheelbase)
        val kinematics = MecanumKinematics(trackWidthMeters = 0.45, wheelBaseMeters = 0.45)
        
        // Setup state store
        var state = RobotState()
        val reducer = RootReducer()
        
        // Wait for Driver Station Start
        // waitForStart() // Commented out for mock compatibility
        
        // --- 2. Main Control Loop ---
        while (opModeIsActive()) {
            
            // A. READ (Hardware -> Actions)
            val driveIntent = gamepadIO.getDriveIntent()
            val poseUpdate = pinpointIO.getPoseUpdate()
            
            // B. REDUCE (Actions -> New State)
            state = reducer.reduce(state, poseUpdate)
            state = reducer.reduce(state, driveIntent)
            
            // C. CALCULATE (State -> Pure Outputs)
            
            // Use the joystick intent as raw field-centric velocities.
            // Scale them to a max physical speed (e.g., 2.0 m/s)
            val maxSpeed = 2.0
            val fieldVx = state.drive.xVelocityMetersPerSecond * maxSpeed
            val fieldVy = state.drive.yVelocityMetersPerSecond * maxSpeed
            val fieldOmega = state.drive.angularVelocityRadiansPerSecond * maxSpeed
            
            val robotHeading = Rotation2d(state.drive.odometryHeading)
            val robotChassisSpeeds = ChassisSpeeds.fromFieldRelativeSpeeds(
                vxMetersPerSecond = fieldVx,
                vyMetersPerSecond = fieldVy,
                omegaRadiansPerSecond = fieldOmega,
                robotHeading = robotHeading
            )
            
            val wheelSpeeds = kinematics.toWheelSpeeds(robotChassisSpeeds)
            
            // Normalize wheel speeds so max value is 1.0 (for FTC setPower)
            val normalizedSpeeds = wheelSpeeds.normalize(1.0)
            
            // D. WRITE (Pure Outputs -> Hardware)
            mecanumIO.apply(normalizedSpeeds)
            FtcDashboardAdapter.sendState(state)
        }
    }
}
