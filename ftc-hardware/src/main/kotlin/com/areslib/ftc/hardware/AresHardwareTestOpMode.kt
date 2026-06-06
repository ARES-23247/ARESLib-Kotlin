package com.areslib.ftc.hardware

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode
import com.qualcomm.robotcore.eventloop.opmode.TeleOp
import com.areslib.ftc.FtcAresRobot

/**
 * Standard TeleOp demonstrating how novice students write code using ARESLib.
 * All complex Redux dispatches, state tracking, and raw hardware polling
 * are entirely encapsulated behind clean, intuitive subsystem facades.
 */
@TeleOp(name = "ARES: Hardware Integration Test", group = "ARES")
class AresHardwareTestOpMode : LinearOpMode() {

    companion object {
        /** Target loop period in milliseconds (50 Hz = 20ms) */
        private const val TARGET_LOOP_MS = 20L
        /** Threshold above which we log a loop overrun warning */
        private const val OVERRUN_THRESHOLD_MS = 30L
    }

    override fun runOpMode() {
        telemetry.addData("Status", "Initializing Robot Facade...")
        telemetry.update()

        // Centrally initialize the robot container and all subsystem facades
        val robot = FtcAresRobot(hardwareMap)

        telemetry.addData("Status", "Initialized. Ready for match!")
        telemetry.update()
        
        waitForStart()

        var loopCount = 0L
        var overrunCount = 0L

        try {
            while (opModeIsActive()) {
                val loopStartMs = com.areslib.util.RobotClock.currentTimeMillis()

                // 1. Coordinates sensor reading, Redux updates, and motor command execution in the background
                robot.update()

                // 2. Simple student-level drive control
                robot.drive.joystickDrive(
                    x = gamepad1.left_stick_x.toDouble(),
                    y = gamepad1.left_stick_y.toDouble(),
                    rot = gamepad1.right_stick_x.toDouble()
                )

                // 3. High-level subsystem interactions
                if (gamepad1.a) {
                    robot.shooter.spinUp(3500.0) // Automatic state and target RPM updates
                } else if (gamepad1.b) {
                    robot.shooter.stop()
                }

                if (gamepad1.x) {
                    robot.intake.deploy()
                } else if (gamepad1.y) {
                    robot.intake.retract()
                }

                // 4. Loop time watchdog
                val loopElapsedMs = com.areslib.util.RobotClock.currentTimeMillis() - loopStartMs
                loopCount++
                if (loopElapsedMs > OVERRUN_THRESHOLD_MS) {
                    overrunCount++
                }

                // 5. Stream automatically processed telemetry values
                telemetry.addData("Robot Mode", robot.shooter.mode)
                telemetry.addData("Flywheel RPM", robot.shooter.flywheelRPM)
                telemetry.addData("Intake Deployed", robot.intake.isDeployed)
                telemetry.addData("Odometry X Pose", robot.drive.odometryX)
                telemetry.addData("Loop ms", loopElapsedMs)
                telemetry.addData("Overruns", "$overrunCount / $loopCount")
                telemetry.update()
            }
        } catch (e: Exception) {
            // Failsafe: disable all outputs and log instead of crashing
            try {
                robot.drive.joystickDrive(0.0, 0.0, 0.0)
            } catch (_: Exception) { /* best-effort shutoff */ }
            telemetry.addData("CRASH", e.message ?: "Unknown error")
            telemetry.update()
        }
    }
}

