package com.areslib.ftc

import com.qualcomm.robotcore.eventloop.opmode.Autonomous
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode
import com.areslib.control.HolonomicDriveController
import com.areslib.control.PIDController
import com.areslib.pathing.Path
import com.areslib.pathing.DynamicPathLoader
import com.qualcomm.robotcore.hardware.ElapsedTime

/**
 * A simplified, clean autonomous OpMode using the FtcMecanumRobot facade.
 * Coordinates EKF fused state updating and path-following with high-level facade calls.
 */
@Autonomous(name = "ARES Mecanum Auto", group = "ARES")
open class ARESMecanumAuto : LinearOpMode() {

    companion object {
        /** Threshold above which we log a loop overrun warning (50 Hz = 20ms) */
        private const val OVERRUN_THRESHOLD_MS = 30L
    }

    override fun runOpMode() {
        // --- 1. Initialization ---
        val robot = FtcMecanumRobot(
            hardwareMap = hardwareMap,
            blName = "rl",
            brName = "rr",
            pinpointName = null,
            limelightName = null
        )

        // Setup controllers for path following
        val xController = PIDController(p = 1.0, i = 0.0, d = 0.1)
        val yController = PIDController(p = 1.0, i = 0.0, d = 0.1)
        val thetaController = PIDController(p = 2.0, i = 0.0, d = 0.0).apply {
            enableContinuousInput(-Math.PI, Math.PI)
        }
        val driveController = HolonomicDriveController(xController, yController, thetaController)

        // Parse trajectory spline path
        val path: Path = try {
            DynamicPathLoader.loadPath("example_path")
        } catch (e: Exception) {
            telemetry.addData("Error", "Failed to load dynamic path: ${e.message}")
            telemetry.update()
            return
        }

        telemetry.addData("Status", "Initialized. Path loaded.")
        telemetry.update()

        try {
            waitForStart()

            val timer = ElapsedTime()
            var lastTime = 0.0
            val totalLength = if (path.points.isNotEmpty()) path.points.last().distanceMeters else 0.0
            var loopCount = 0L
            var overrunCount = 0L

            // --- 2. Autonomous Loop ---
            while (opModeIsActive()) {
                val loopStartMs = com.areslib.util.RobotClock.currentTimeMillis()

                try {
                    val currentTime = timer.seconds()
                    val dt = if (currentTime > lastTime) currentTime - lastTime else 0.02
                    lastTime = currentTime

                    // A. Polls pinpoint/limelight, updates Redux EKF, and runs loop under the hood
                    robot.update()

                    // Fetch current fused EKF pose estimate via clean facade
                    val currentPose = robot.drive.odometryPose
                    val currentDistance = currentTime * 0.5 // Progress at 0.5 m/s

                    if (currentDistance >= totalLength) {
                        break
                    }

                    // Get nominal target pose from Path spline
                    val targetState = path.sampleAtDistance(currentDistance)

                    // B. Calculate path follower speeds relative to EKF pose
                    val chassisSpeeds = driveController.calculate(
                        currentPose = currentPose,
                        targetPose = targetState.pose,
                        targetVelocityMps = targetState.velocityMps,
                        targetHeading = targetState.pose.heading,
                        dtSeconds = dt
                    )

                    // C. Command drive facade relative to the calculated velocities
                    // scaling factor 2.0 maps max nominal velocity
                    robot.drive.joystickDrive(
                        x = chassisSpeeds.vxMetersPerSecond / 2.0,
                        y = chassisSpeeds.vyMetersPerSecond / 2.0,
                        rot = chassisSpeeds.omegaRadiansPerSecond / 2.0
                    )
                } catch (e: Exception) {
                    // Per-iteration failsafe: disable outputs if a single iteration fails
                    try {
                        robot.drive.joystickDrive(0.0, 0.0, 0.0)
                    } catch (_: Exception) { /* best-effort */ }
                    telemetry.addData("LOOP_ERROR", e.message ?: "Unknown error")
                }

                // Loop time watchdog
                val loopElapsedMs = com.areslib.util.RobotClock.currentTimeMillis() - loopStartMs
                loopCount++
                if (loopElapsedMs > OVERRUN_THRESHOLD_MS) {
                    overrunCount++
                }

                telemetry.addData("EKF Pose X", robot.drive.odometryPose.x)
                telemetry.addData("EKF Pose Y", robot.drive.odometryPose.y)
                telemetry.addData("Loop ms", loopElapsedMs)
                telemetry.addData("Overruns", "$overrunCount / $loopCount")
                telemetry.update()
            }

            // Clean stop at target
            robot.drive.joystickDrive(0.0, 0.0, 0.0)
        } catch (e: Exception) {
            // Top-level failsafe: disable all outputs and log
            try {
                robot.drive.joystickDrive(0.0, 0.0, 0.0)
            } catch (_: Exception) { /* best-effort shutoff */ }
            telemetry.addData("CRASH", e.message ?: "Unknown error")
            telemetry.update()
        } finally {
            robot.close()
        }
    }
}

