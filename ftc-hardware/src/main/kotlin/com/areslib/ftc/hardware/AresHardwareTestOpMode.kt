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
    
    override fun runOpMode() {
        telemetry.addData("Status", "Initializing Robot Facade...")
        telemetry.update()

        // Centrally initialize the robot container and all subsystem facades
        val robot = FtcAresRobot(hardwareMap)

        telemetry.addData("Status", "Initialized. Ready for match!")
        telemetry.update()
        
        waitForStart()

        while (opModeIsActive()) {
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

            // 4. Stream automatically processed telemetry values
            telemetry.addData("Robot Mode", robot.shooter.mode)
            telemetry.addData("Flywheel RPM", robot.shooter.flywheelRPM)
            telemetry.addData("Cowl Hood Angle", robot.shooter.cowlAngleDegrees)
            telemetry.addData("Intake Deployed", robot.intake.isDeployed)
            telemetry.addData("Odometry X Pose", robot.drive.odometryX)
            telemetry.update()
        }
    }
}
