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

    open val pathName: String = "Example Path"

    companion object {
        /** Threshold above which we log a loop overrun warning (50 Hz = 20ms) */
        private const val OVERRUN_THRESHOLD_MS = 30L
    }

    override fun runOpMode() {
        com.areslib.telemetry.RobotStatusTracker.opModeInstance = this
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
        robot.mecanumIO.kS = if (robot.driveKs > 0.0) robot.driveKs else 0.05

        // Setup unified path follower helper
        val pathFollower = com.areslib.ftc.control.FtcMecanumPathFollower(
            robot,
            xController = com.areslib.control.PIDController(robot.pathTranslationKp, robot.pathTranslationKi, robot.pathTranslationKd),
            yController = com.areslib.control.PIDController(robot.pathTranslationKp, robot.pathTranslationKi, robot.pathTranslationKd),
            thetaController = com.areslib.control.PIDController(robot.pathRotationKp, robot.pathRotationKi, robot.pathRotationKd).apply {
                enableContinuousInput(-Math.PI, Math.PI)
            }
        )

        // Parse trajectory spline path
        var path: Path? = null
        var pathLoadError: String? = null
        try {
            path = DynamicPathLoader.loadPath(pathName)
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

            // Spawn the robot at the starting waypoint
            if (path.points.isNotEmpty()) {
                val startState = path.points.first()
                robot.store.dispatch(
                    com.areslib.action.RobotAction.PoseUpdate(
                        xMeters = startState.pose.x,
                        yMeters = startState.pose.y,
                        headingRadians = startState.pose.heading.radians,
                        timestampMs = RobotClock.currentTimeMillis(),
                        isReset = true
                    )
                )
            }

            val startMs = RobotClock.currentTimeMillis()
            var lastTime = 0.0
            val totalLength = if (path.points.isNotEmpty()) path.points.last().distanceMeters else 0.0
            var loopCount = 0L
            var overrunCount = 0L
            var currentDistance = 0.0
            val scratchPoint = com.areslib.pathing.MutablePathPoint()
            val targetState = com.areslib.pathing.PathPoint(com.areslib.math.Pose2d(0.0, 0.0, com.areslib.math.Rotation2d(0.0)), 0.0, 0.0, 0.0, 0.0)

            // --- 2. Autonomous Loop ---
            while (opModeIsActive()) {
                val loopStartMs = com.areslib.util.RobotClock.currentTimeMillis()

                try {
                    val currentTime = (loopStartMs - startMs) / 1000.0
                    val dt = if (currentTime > lastTime) currentTime - lastTime else 0.02
                    lastTime = currentTime

                    // A. Polls pinpoint/limelight, updates Redux EKF, and runs loop under the hood
                    robot.update()

                    // Find closest point on path to robot's actual position
                    val robotPose = robot.drive.odometryPose
                    var bestDist = Double.MAX_VALUE
                    var bestIdx = 0
                    for (i in path.points.indices) {
                        val pp = path.points[i]
                        val dx = pp.pose.x - robotPose.x
                        val dy = pp.pose.y - robotPose.y
                        val d = dx * dx + dy * dy
                        // Only search forward from our current progress to avoid going backwards
                        if (d < bestDist && pp.distanceMeters >= currentDistance - 0.3) {
                            bestDist = d
                            bestIdx = i
                        }
                    }

                    // Advance currentDistance to the closest point, plus a small lookahead
                    val closestDist = path.points[bestIdx].distanceMeters
                    val lookahead = path.points[bestIdx].velocityMps * dt * 2.0 + 0.05
                    currentDistance = kotlin.math.min(closestDist + lookahead, totalLength)

                    if (currentDistance >= totalLength) {
                        break
                    }

                    // Get nominal target pose from Path spline without intermediate allocations
                    path.sampleAtDistance(currentDistance, scratchPoint)
                    scratchPoint.copyInto(targetState)

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
                telemetry.addData("Path Distance", "%.2f / %.2f".format(currentDistance, totalLength))
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

