package com.areslib.ftc

import com.qualcomm.robotcore.eventloop.opmode.Autonomous
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode
import com.areslib.control.HolonomicDriveController
import com.areslib.control.PIDController
import com.areslib.math.Pose2d
import com.areslib.math.Rotation2d
import com.areslib.pathing.Path
import com.areslib.pathing.DynamicPathLoader
import com.qualcomm.robotcore.hardware.ElapsedTime
import com.areslib.kinematics.MecanumKinematics
import com.qualcomm.hardware.gobilda.GoBildaPinpointDriver
import com.areslib.state.RobotState
import com.areslib.reducer.rootReducer

@Autonomous(name = "ARES Mecanum Auto", group = "ARES")
class ARESMecanumAuto : LinearOpMode() {

    // Configuration
    private val maxVelocity = 1.5 // m/s
    private val maxAcceleration = 1.0 // m/s^2

    override fun runOpMode() {
        // 1. Initialize Hardware (via abstractions)
        val driveIO = MecanumHardwareIO(hardwareMap)
        val kinematics = MecanumKinematics(trackWidthMeters = 0.45, wheelBaseMeters = 0.45)
        
        val pinpointDriver = hardwareMap.get(GoBildaPinpointDriver::class.java, "pinpoint")
        val pinpointIO = PinpointIO(pinpointDriver)

        // Initialize RobotState store
        var state = RobotState()

        // 2. Initialize Controllers
        val xController = PIDController(p = 1.0, i = 0.0, d = 0.1)
        val yController = PIDController(p = 1.0, i = 0.0, d = 0.1)
        val thetaController = PIDController(p = 2.0, i = 0.0, d = 0.0)
        thetaController.enableContinuousInput(-Math.PI, Math.PI)

        val driveController = HolonomicDriveController(xController, yController, thetaController)

        // 3. Parse Trajectory
        val pathName = "example_path"
        val path: Path = try {
            DynamicPathLoader.loadPath(pathName)
        } catch (e: Exception) {
            telemetry.addData("Error", "Failed to load dynamic path: ${e.message}")
            telemetry.update()
            return
        }

        telemetry.addData("Status", "Initialized. Path loaded with ${path.points.size} points.")
        telemetry.update()

        waitForStart()
        
        val timer = ElapsedTime()
        timer.reset()

        var lastTime = 0.0
        val totalLength = if (path.points.isNotEmpty()) path.points.last().distanceMeters else 0.0

        // 4. Autonomous Loop
        while (opModeIsActive()) {
            val currentTime = timer.seconds()
            val dt = if (currentTime > lastTime) currentTime - lastTime else 0.02
            lastTime = currentTime
            
            // Read raw pinpoint odometry relative updates and dispatch them to the EKF store
            val poseUpdate = pinpointIO.getPoseUpdate()
            state = rootReducer(state, poseUpdate)

            // Fetch current fused EKF pose estimate
            val currentPose = state.drive.poseEstimator.estimatedPose

            // Map time elapsed directly to nominal target distance progress
            val nominalVelocity = 0.5 // m/s
            val currentDistance = currentTime * nominalVelocity

            if (currentDistance >= totalLength) {
                break
            }
            
            // Get target state from PathPlanner spline
            val targetState = path.sampleAtDistance(currentDistance)

            // Calculate speeds driven by the fused EKF pose
            val chassisSpeeds = driveController.calculate(
                currentPose = currentPose,
                targetPose = targetState.pose,
                targetVelocityMps = targetState.velocityMps,
                targetHeading = targetState.pose.heading,
                dtSeconds = dt
            )

            // Convert to wheel speeds and apply
            val wheelSpeeds = kinematics.toWheelSpeeds(chassisSpeeds)
            
            // Fetch battery voltage for compensation
            val batteryVoltage = 12.0
            
            driveIO.apply(wheelSpeeds, batteryVoltage)

            telemetry.addData("EKF Pose X", currentPose.x)
            telemetry.addData("EKF Pose Y", currentPose.y)
            telemetry.addData("EKF Pose Heading", Math.toDegrees(currentPose.heading.radians))
            telemetry.addData("Target X", targetState.pose.x)
            telemetry.addData("Target Y", targetState.pose.y)
            telemetry.addData("Chassis Vx", chassisSpeeds.vxMetersPerSecond)
            telemetry.update()
        }

        // Stop robot
        driveIO.apply(com.areslib.kinematics.MecanumWheelSpeeds(0.0, 0.0, 0.0, 0.0), 12.0)
    }
}
