package com.areslib.ftc

import com.qualcomm.robotcore.eventloop.opmode.Autonomous
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode
import com.areslib.control.HolonomicDriveController
import com.areslib.control.PIDController
import com.areslib.pathing.Path
import com.areslib.pathing.DynamicPathLoader
import com.areslib.util.RobotClock

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
            flName = "fl",
            frName = "fr",
            blName = "rl",
            brName = "rr",
            pinpointName = "pinpoint",
            limelightName = null,
            flDirection = com.qualcomm.robotcore.hardware.DcMotorSimple.Direction.FORWARD,
            blDirection = com.qualcomm.robotcore.hardware.DcMotorSimple.Direction.FORWARD,
            frDirection = com.qualcomm.robotcore.hardware.DcMotorSimple.Direction.REVERSE,
            brDirection = com.qualcomm.robotcore.hardware.DcMotorSimple.Direction.REVERSE
        )

        // Calibrate static friction feedforward (kS) to overcome physical drivetrain deadband
        robot.mecanumIO.kS = 0.05

        // Setup unified path follower helper
        val pathFollower = com.areslib.ftc.control.FtcMecanumPathFollower(robot)

        // Parse trajectory spline path
        var path: Path? = null
        var pathLoadError: String? = null
        try {
            path = DynamicPathLoader.loadPath("Example Path")
        } catch (e: Exception) {
            pathLoadError = e.message ?: "Unknown error"
        }

        if (pathLoadError != null) {
            telemetry.addData("Error", "Failed to load dynamic path: $pathLoadError")
            telemetry.addData("Status", "Initialization Failed!")
            telemetry.update()
        } else {
            telemetry.addData("Status", "Initialized. Path loaded.")
            telemetry.update()
        }

        try {
            waitForStart()

            if (path == null) {
                telemetry.addData("CRASH", "Aborting: Path not loaded. Error: $pathLoadError")
                telemetry.update()
                sleep(2000L)
                return
            }

            val startMs = RobotClock.currentTimeMillis()
            var lastTime = 0.0
            val totalLength = if (path.points.isNotEmpty()) path.points.last().distanceMeters else 0.0
            var loopCount = 0L
            var overrunCount = 0L

            // --- 2. Autonomous Loop ---
            while (opModeIsActive()) {
                val loopStartMs = com.areslib.util.RobotClock.currentTimeMillis()

                try {
                    val currentTime = (loopStartMs - startMs) / 1000.0
                    val dt = if (currentTime > lastTime) currentTime - lastTime else 0.02
                    lastTime = currentTime

                    // A. Polls pinpoint/limelight, updates Redux EKF, and runs loop under the hood
                    robot.update()

                    val currentDistance = currentTime * 0.5 // Progress at 0.5 m/s

                    if (currentDistance >= totalLength) {
                        break
                    }

                    // Get nominal target pose from Path spline
                    val targetState = path.sampleAtDistance(currentDistance)

                    // B. Calculate speeds and update motor commands via the follower
                    pathFollower.update(targetState, dt)
                } catch (e: Exception) {
                    // Per-iteration failsafe: disable outputs if a single iteration fails
                    try {
                        pathFollower.stop()
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
                telemetry.addData("Pinpoint X", robot.drive.odometryX)
                telemetry.addData("Pinpoint Y", robot.drive.odometryY)
                telemetry.addData("Loop ms", loopElapsedMs)
                telemetry.addData("Overruns", "$overrunCount / $loopCount")
                telemetry.update()
            }

            // Clean stop at target
            pathFollower.stop()
        } catch (e: Exception) {
            // Top-level failsafe: disable all outputs and log
            try {
                pathFollower.stop()
            } catch (_: Exception) { /* best-effort shutoff */ }
            telemetry.addData("CRASH", e.message ?: "Unknown error")
            telemetry.update()
        } finally {
            robot.close()
        }

    }
}

