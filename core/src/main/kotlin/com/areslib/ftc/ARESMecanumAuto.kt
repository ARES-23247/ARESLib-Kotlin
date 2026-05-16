package com.areslib.ftc

import com.qualcomm.robotcore.hardware.Autonomous
import com.qualcomm.robotcore.hardware.LinearOpMode
import com.areslib.control.HolonomicDriveController
import com.areslib.control.PIDController
import com.areslib.math.Pose2d
import com.areslib.math.Rotation2d
import com.areslib.pathing.PathPlannerParser
import com.areslib.pathing.Path
import com.qualcomm.robotcore.hardware.ElapsedTime
import com.areslib.kinematics.MecanumKinematics

@Autonomous(name = "ARES Mecanum Auto", group = "ARES")
class ARESMecanumAuto : LinearOpMode() {

    // Configuration
    private val maxVelocity = 1.5 // m/s
    private val maxAcceleration = 1.0 // m/s^2

    override fun runOpMode() {
        // 1. Initialize Hardware (via abstractions)
        val driveIO = MecanumHardwareIO(hardwareMap)
        val kinematics = MecanumKinematics(trackWidthMeters = 0.45, wheelBaseMeters = 0.45)
        
        // 2. Initialize Controllers
        val xController = PIDController(p = 1.0, i = 0.0, d = 0.1)
        val yController = PIDController(p = 1.0, i = 0.0, d = 0.1)
        val thetaController = PIDController(p = 2.0, i = 0.0, d = 0.0)
        thetaController.enableContinuousInput(-Math.PI, Math.PI)

        val driveController = HolonomicDriveController(xController, yController, thetaController)

        // 3. Parse Trajectory
        // For testing/mocking, we provide a valid JSON structure representing a simple path
        val mockJson = """
            {
              "waypoints": [
                {"anchor": {"x": 0.0, "y": 0.0}},
                {"anchor": {"x": 1.0, "y": 0.0}},
                {"anchor": {"x": 1.0, "y": 1.0}}
              ],
              "rotationTargets": [
                {"waypointRelativePos": 0, "rotationDegrees": 0.0},
                {"waypointRelativePos": 1, "rotationDegrees": 90.0}
              ]
            }
        """.trimIndent()
        
        val path: Path = try {
            PathPlannerParser.parsePath(mockJson)
        } catch (e: Exception) {
            telemetry.addData("Error", "Failed to parse path: ${e.message}")
            telemetry.update()
            return
        }

        telemetry.addData("Status", "Initialized. Path loaded with ${path.points.size} points.")
        telemetry.update()

        waitForStart()
        
        val timer = ElapsedTime()
        timer.reset()

        // Dummy localization tracking for the test OpMode loop
        var currentDistance = 0.0
        var lastTime = 0.0

        val totalLength = if (path.points.isNotEmpty()) path.points.last().distanceMeters else 0.0

        // 4. Autonomous Loop
        while (opModeIsActive() && currentDistance < totalLength) {
            val currentTime = timer.seconds()
            val dt = if (currentTime > lastTime) currentTime - lastTime else 0.02
            lastTime = currentTime
            
            // Assume robot moves forward perfectly at 0.5 m/s for this mock
            currentDistance = currentTime * 0.5
            
            // Get target state from path
            val targetState = path.sampleAtDistance(currentDistance)

            // Get current state from odometry (mocked here)
            val currentPose = Pose2d(currentDistance, 0.0, Rotation2d.fromDegrees(0.0))

            // Calculate speeds
            val chassisSpeeds = driveController.calculate(
                currentPose,
                targetState.pose,
                targetVelocityMps = 0.5,
                targetHeading = Rotation2d.fromDegrees(0.0), // Assume heading is 0 for mock
                dtSeconds = dt
            )

            // Convert to wheel speeds and apply
            val wheelSpeeds = kinematics.toWheelSpeeds(chassisSpeeds)
            driveIO.apply(wheelSpeeds.normalize(1.0))

            telemetry.addData("Target X", targetState.pose.x)
            telemetry.addData("Target Y", targetState.pose.y)
            telemetry.addData("Chassis Vx", chassisSpeeds.vxMetersPerSecond)
            telemetry.update()
        }

        // Stop robot
        driveIO.apply(com.areslib.kinematics.MecanumWheelSpeeds(0.0, 0.0, 0.0, 0.0))
    }
}
