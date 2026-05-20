package com.areslib.frc

import com.areslib.action.RobotAction
import com.areslib.control.HolonomicDriveController
import com.areslib.control.PIDController
import com.areslib.math.Pose2d
import com.areslib.math.Rotation2d
import com.areslib.pathing.Path
import com.areslib.reducer.rootReducer

import edu.wpi.first.wpilibj.TimedRobot
import edu.wpi.first.wpilibj.XboxController
import edu.wpi.first.wpilibj.RobotBase
import edu.wpi.first.wpilibj.Timer
import com.ctre.phoenix6.mechanisms.swerve.SwerveDrivetrain

/**
 * Main Robot lifecycle for the FRC CTRE Swerve Integration.
 *
 * This is now a thin shell that delegates all state management, hardware IO,
 * and telemetry to the [FrcSwerveRobot] facade — mirroring how FTC's
 * ARESMecanumTeleOp delegates to FtcMecanumRobot.
 */
class ARESRobot : TimedRobot() {

    private lateinit var robot: FrcSwerveRobot
    private lateinit var sim: Dyn4jSimulation
    private val controller = XboxController(0)

    // Edge detector for LB toggle
    private var prevLB = false
    private var intakeDeployed = false

    // Simulation timing
    private var lastSimTime = 0.0

    // Autonomous
    private var activePath: Path? = null
    private var autoStartTime = 0.0
    private var autoDistance = 0.0
    private val driveController = HolonomicDriveController(
        PIDController(4.0, 0.0, 0.1),
        PIDController(4.0, 0.0, 0.1),
        PIDController(3.0, 0.0, 0.0)
    )

    override fun robotInit() {
        sim = Dyn4jSimulation(seed = 42L)

        val isReal = RobotBase.isReal()
        robot = if (isReal) {
            try {
                val leaderFX = com.ctre.phoenix6.hardware.TalonFX(10)
                val followerFX = com.ctre.phoenix6.hardware.TalonFX(11)
                val cowlFX = com.ctre.phoenix6.hardware.TalonFX(12)
                val pivotFX = com.ctre.phoenix6.hardware.TalonFX(13)
                val rollerFX = com.ctre.phoenix6.hardware.TalonFX(14)
                val encoder = com.ctre.phoenix6.hardware.CANcoder(15)
                val feederFX = com.ctre.phoenix6.hardware.TalonFX(16)
                val beamBreak = edu.wpi.first.wpilibj.DigitalInput(0)

                FrcSwerveRobot(
                    swerveIO = null, // swerveIO = FRCSwerveHardwareIO(TunerConstants.DriveTrain)
                    flywheelIO = FRCFlywheelHardwareIO(leaderFX, followerFX),
                    cowlIO = FRCCowlHardwareIO(cowlFX),
                    intakeIO = FRCIntakeHardwareIO(pivotFX, rollerFX, encoder),
                    feederIO = FRCFeederHardwareIO(feederFX, beamBreak),
                    isSimulation = false
                )
            } catch (e: Exception) {
                println("Failed to initialize physical hardware: ${e.message}")
                // Fallback to sim IO
                FrcSwerveRobot(
                    flywheelIO = sim.flywheelIO,
                    cowlIO = sim.cowlIO,
                    intakeIO = sim.intakeIO,
                    feederIO = sim.feederIO,
                    isSimulation = true
                )
            }
        } else {
            FrcSwerveRobot(
                flywheelIO = sim.flywheelIO,
                cowlIO = sim.cowlIO,
                intakeIO = sim.intakeIO,
                feederIO = sim.feederIO,
                isSimulation = true
            )
        }

        lastSimTime = Timer.getFPGATimestamp()

        // Wire brownout guard to read live battery voltage from roboRIO
        robot.batteryVoltageSupplier = {
            try {
                edu.wpi.first.wpilibj.RobotController.getBatteryVoltage()
            } catch (_: Exception) {
                12.6 // Fallback for simulation environments
            }
        }
    }

    override fun robotPeriodic() {
        // Unified update: reads sensors, writes outputs, publishes telemetry + CSV
        robot.update(controller.toState())
    }

    // ── Teleop ──

    override fun teleopPeriodic() {
        val applyDeadband = { value: Double -> if (Math.abs(value) < 0.1) 0.0 else value }
        val forward = applyDeadband(-controller.leftY) * 4.5
        val strafe = applyDeadband(-controller.leftX) * 4.5
        val rotation = applyDeadband(-controller.rightX) * Math.PI

        robot.drive.joystickDrive(forward, strafe, rotation)

        // RB: Flywheel
        if (controller.rightBumper) {
            robot.shooter.spinUp(4000.0)
        } else {
            robot.shooter.stop()
        }

        // RT: Transfer/Shoot
        val rtPressed = controller.rightTriggerAxis > 0.5
        if (rtPressed) robot.shooter.shoot()

        // LB: Toggle intake deploy
        val currLB = controller.leftBumper
        if (currLB && !prevLB) {
            intakeDeployed = !intakeDeployed
            if (intakeDeployed) robot.intake.deploy() else robot.intake.retract()
        }
        prevLB = currLB

        // LT: Intake rollers
        val ltPressed = controller.leftTriggerAxis > 0.5
        robot.intake.setRollerSpeed(if (ltPressed) 10.0 else 0.0)

        // DPad: Cowl angle
        if (controller.pov == 0) robot.shooter.setCowlAngle(45.0)
        else if (controller.pov == 180) robot.shooter.setCowlAngle(15.0)
    }

    // ── Autonomous ──

    override fun autonomousInit() {
        try {
            activePath = PathLoader.loadPath("SimPath")
            val startPoint = activePath?.points?.firstOrNull()
            if (startPoint != null) {
                sim.resetPose(startPoint.pose.x, startPoint.pose.y, startPoint.pose.heading.radians)
                robot.store.dispatch(RobotAction.PoseUpdate(
                    xMeters = startPoint.pose.x,
                    yMeters = startPoint.pose.y,
                    headingRadians = startPoint.pose.heading.radians,
                    timestampMs = com.areslib.util.RobotClock.currentTimeMillis()
                ))
            }
        } catch (e: Exception) {
            println("ERROR: Failed to load autonomous path SimPath: ${e.message}")
            activePath = null
        }
        autoStartTime = Timer.getFPGATimestamp()
        autoDistance = 0.0
    }

    override fun autonomousPeriodic() {
        val path = activePath ?: return
        val dt = 0.02

        val currentPose = Pose2d(
            robot.store.state.drive.odometryX,
            robot.store.state.drive.odometryY,
            Rotation2d(robot.store.state.drive.odometryHeading)
        )

        val targetPoint = path.sampleAtDistance(autoDistance)

        val speeds = driveController.calculate(
            currentPose = currentPose,
            targetPose = targetPoint.pose,
            targetVelocityMps = targetPoint.velocityMps,
            targetHeading = targetPoint.pose.heading,
            dtSeconds = dt
        )

        // Field-relative conversion
        val cos = currentPose.heading.cos
        val sin = currentPose.heading.sin
        val fieldX = speeds.vxMetersPerSecond * cos - speeds.vyMetersPerSecond * sin
        val fieldY = speeds.vxMetersPerSecond * sin + speeds.vyMetersPerSecond * cos

        robot.drive.joystickDrive(fieldX, fieldY, speeds.omegaRadiansPerSecond)

        // Event markers
        for (event in path.events) {
            val nextDistance = autoDistance + targetPoint.velocityMps * dt
            if (event.triggerDistanceMeters in autoDistance..nextDistance) {
                println("AUTO EVENT TRIGGERED: ${event.eventName} at ${event.triggerDistanceMeters}m")
                robot.telemetry.putString("Robot/ActiveEvent", event.eventName)
                when (event.eventName) {
                    "FlywheelOn" -> robot.shooter.spinUp(4000.0)
                    "IntakeDeploy" -> {
                        robot.intake.deploy()
                        robot.intake.setRollerSpeed(15.0)
                    }
                    "FeederShoot" -> robot.shooter.shoot()
                }
            }
        }

        // Trajectory telemetry
        robot.telemetry.putDoubleArray("Robot/TargetPose", doubleArrayOf(
            targetPoint.pose.x, targetPoint.pose.y, targetPoint.pose.heading.radians
        ))
        val dx = targetPoint.pose.x - currentPose.x
        val dy = targetPoint.pose.y - currentPose.y
        robot.telemetry.putNumber("Robot/TrajectoryError", kotlin.math.hypot(dx, dy))

        autoDistance += targetPoint.velocityMps * dt
    }

    // ── Simulation ──

    override fun simulationPeriodic() {
        if (!RobotBase.isSimulation()) return

        val now = Timer.getFPGATimestamp()
        val dt = Math.min(now - lastSimTime, 0.05)
        lastSimTime = now

        // Step physics and dispatch any resulting actions (ball intake/shoot)
        val actions = sim.step(robot.store.state, dt)
        for (action in actions) {
            robot.store.dispatch(action)
        }

        // Feed sim pose back into Store
        robot.store.dispatch(sim.getPoseUpdate())

        // Publish 3D visualization
        sim.publishVisualization(robot.store.state, robot.telemetry)
    }
}
