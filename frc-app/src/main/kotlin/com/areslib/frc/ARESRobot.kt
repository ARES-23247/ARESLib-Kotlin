package com.areslib.frc

import com.areslib.action.RobotAction
import com.areslib.control.HolonomicDriveController
import com.areslib.control.PIDController
import com.areslib.control.ShotResult
import com.areslib.control.ShotSetup
import com.areslib.math.ChassisSpeeds
import com.areslib.math.Pose2d
import com.areslib.math.Rotation2d
import com.areslib.math.Translation2d
import com.areslib.pathing.Path
import com.areslib.reducer.rootReducer

import edu.wpi.first.wpilibj.TimedRobot
import edu.wpi.first.wpilibj.XboxController
import edu.wpi.first.wpilibj.RobotBase
import edu.wpi.first.wpilibj.Timer
import edu.wpi.first.wpilibj.DriverStation
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

    // Slamtake state tracking
    private var slamtakeActive = false
    private var slamtakeStartTime = 0.0

    // Pre-allocated ShotResult for SOTM (zero-allocation)
    private val shotResult = ShotResult()
    private var speakerTranslation = Translation2d(4.625, 4.035)

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
                // Marvin 19 Physical Hardware on "CAN2" high-speed bus
                val leftMasterFX = com.ctre.phoenix6.hardware.TalonFX(9, "CAN2")
                val leftFollowerFX = com.ctre.phoenix6.hardware.TalonFX(10, "CAN2")
                val rightMasterFX = com.ctre.phoenix6.hardware.TalonFX(11, "CAN2")
                val rightFollowerFX = com.ctre.phoenix6.hardware.TalonFX(12, "CAN2")
                val cowlFX = com.ctre.phoenix6.hardware.TalonFX(13, "CAN2")
                val pivotFX = com.ctre.phoenix6.hardware.TalonFX(14, "CAN2")
                val rollerFX = com.ctre.phoenix6.hardware.TalonFX(15, "CAN2")
                val floorFX = com.ctre.phoenix6.hardware.TalonFX(16, "CAN2")
                val climberFX = com.ctre.phoenix6.hardware.TalonFX(19, "CAN2")
                val feederFX = com.ctre.phoenix6.hardware.TalonFX(20, "CAN2")

                FrcSwerveRobot(
                    swerveIO = null, // swerveIO = FRCSwerveHardwareIO(TunerConstants.DriveTrain)
                    flywheelIO = FRCFlywheelHardwareIO(leftMasterFX, leftFollowerFX, rightMasterFX, rightFollowerFX),
                    cowlIO = FRCCowlHardwareIO(cowlFX),
                    intakeIO = FRCIntakeHardwareIO(pivotFX, rollerFX),
                    feederIO = FRCFeederHardwareIO(feederFX),
                    floorIO = FRCFloorHardwareIO(floorFX),
                    climberIO = FRCClimberHardwareIO(climberFX),
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
                    floorIO = sim.floorIO,
                    climberIO = sim.climberIO,
                    isSimulation = true
                )
            }
        } else {
            FrcSwerveRobot(
                flywheelIO = sim.flywheelIO,
                cowlIO = sim.cowlIO,
                intakeIO = sim.intakeIO,
                feederIO = sim.feederIO,
                floorIO = sim.floorIO,
                climberIO = sim.climberIO,
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
        val alliance = DriverStation.getAlliance()
        if (alliance.isPresent) {
            speakerTranslation = if (alliance.get() == DriverStation.Alliance.Red) {
                Translation2d(11.915, 4.035)
            } else {
                Translation2d(4.625, 4.035)
            }
        }

        val applyDeadband = { value: Double -> if (Math.abs(value) < 0.1) 0.0 else value }
        val forward = applyDeadband(-controller.leftY) * 4.5
        val strafe = applyDeadband(-controller.leftX) * 4.5
        var rotation = applyDeadband(-controller.rightX) * Math.PI

        val currentPose = Pose2d(
            robot.store.state.drive.odometryX,
            robot.store.state.drive.odometryY,
            Rotation2d(robot.store.state.drive.odometryHeading)
        )

        // ── Right Trigger: Shoot-on-the-Move (SOTM) ──
        val rtPressed = controller.rightTriggerAxis > 0.5
        if (rtPressed) {
            // Compute field-centric velocity from robot-centric state velocities
            val rx = robot.store.state.drive.xVelocityMetersPerSecond
            val ry = robot.store.state.drive.yVelocityMetersPerSecond
            val omega = robot.store.state.drive.angularVelocityRadiansPerSecond
            
            val cos = currentPose.heading.cos
            val sin = currentPose.heading.sin
            val fieldVx = rx * cos - ry * sin
            val fieldVy = rx * sin + ry * cos
            
            val fieldSpeeds = ChassisSpeeds(fieldVx, fieldVy, omega)
            
            // Execute lookahead math
            ShotSetup.calculate(currentPose, fieldSpeeds, speakerTranslation, shotResult)
            
            // Dispatch shooter parameters
            robot.store.dispatch(RobotAction.SetFlywheelSpeed(shotResult.targetFlywheelRpm))
            robot.store.dispatch(RobotAction.SetFlywheelActive(true, com.areslib.util.RobotClock.currentTimeMillis()))
            robot.store.dispatch(RobotAction.SetCowlAngle(shotResult.targetCowlAngleDegrees))
            
            // Steer drivetrain using P controller + direct feedforward
            val headingError = shotResult.robotTargetHeadingRad - currentPose.heading.radians
            val wrappedError = com.areslib.math.InputMath.wrapAngle(headingError)
            
            val kp = 4.0
            rotation = wrappedError * kp + shotResult.angularVelocityFeedforwardRadPerSec
            
            // Intelligent SOTM feeder auto-trigger
            val headingAligned = Math.abs(wrappedError) < 0.05
            val rpmAligned = Math.abs(robot.store.state.superstructure.flywheel.velocityRpm - shotResult.targetFlywheelRpm) < 150.0
            if (headingAligned && rpmAligned) {
                robot.store.dispatch(RobotAction.SetFeederSpeed(10.0))
            } else {
                robot.store.dispatch(RobotAction.SetFeederSpeed(0.0))
            }
        } else {
            // Standard flywheel / feeder stop if not shooting/intaking
            robot.store.dispatch(RobotAction.SetFlywheelActive(false, com.areslib.util.RobotClock.currentTimeMillis()))
            // Feeder speed is handled below (dependent on LT/LB)
        }

        // Apply drive command
        robot.drive.joystickDrive(forward, strafe, rotation)

        // ── A Button: Slamtake Sequence ──
        val aPressed = controller.aButton
        if (aPressed && !slamtakeActive) {
            slamtakeActive = true
            slamtakeStartTime = Timer.getFPGATimestamp()
        }

        // ── Left Bumper: Unjam ──
        val lbPressed = controller.leftBumper

        // ── Left Trigger: Intake/Feeder active run ──
        val ltPressed = controller.leftTriggerAxis > 0.5

        // Dispatch states according to pilot control priorities
        when {
            lbPressed -> {
                // Unjam sequence takes top priority
                slamtakeActive = false
                robot.store.dispatch(RobotAction.SetIntakePivot(deployed = true))
                robot.store.dispatch(RobotAction.SetIntakeRollers(-5.0))
                robot.store.dispatch(RobotAction.SetFloorSpeed(-5.0))
                robot.store.dispatch(RobotAction.SetFeederSpeed(-5.0))
            }
            slamtakeActive -> {
                val elapsed = Timer.getFPGATimestamp() - slamtakeStartTime
                if (elapsed < 0.5) {
                    robot.store.dispatch(RobotAction.SetIntakePivot(deployed = true))
                    robot.store.dispatch(RobotAction.SetIntakeRollers(10.0))
                    robot.store.dispatch(RobotAction.SetFloorSpeed(10.0))
                    robot.store.dispatch(RobotAction.SetFeederSpeed(0.0))
                } else if (elapsed < 1.5) {
                    robot.store.dispatch(RobotAction.SetIntakePivot(deployed = false))
                    robot.store.dispatch(RobotAction.SetIntakeRollers(10.0))
                    robot.store.dispatch(RobotAction.SetFloorSpeed(10.0))
                    robot.store.dispatch(RobotAction.SetFeederSpeed(0.0))
                } else {
                    slamtakeActive = false
                    robot.store.dispatch(RobotAction.SetIntakePivot(deployed = false))
                    robot.store.dispatch(RobotAction.SetIntakeRollers(0.0))
                    robot.store.dispatch(RobotAction.SetFloorSpeed(0.0))
                    if (!rtPressed) {
                        robot.store.dispatch(RobotAction.SetFeederSpeed(0.0))
                    }
                }
            }
            ltPressed -> {
                // Active manual intake
                robot.store.dispatch(RobotAction.SetIntakePivot(deployed = true))
                robot.store.dispatch(RobotAction.SetIntakeRollers(10.0))
                robot.store.dispatch(RobotAction.SetFloorSpeed(10.0))
                robot.store.dispatch(RobotAction.SetFeederSpeed(10.0))
            }
            else -> {
                // Default stop everything
                robot.store.dispatch(RobotAction.SetIntakePivot(deployed = false))
                robot.store.dispatch(RobotAction.SetIntakeRollers(0.0))
                robot.store.dispatch(RobotAction.SetFloorSpeed(0.0))
                if (!rtPressed) {
                    robot.store.dispatch(RobotAction.SetFeederSpeed(0.0))
                }
            }
        }

        // ── POV Up/Down: Climber Voltage ──
        if (controller.pov == 0) {
            robot.store.dispatch(RobotAction.SetClimberVoltage(6.0))
        } else if (controller.pov == 180) {
            robot.store.dispatch(RobotAction.SetClimberVoltage(-6.0))
        } else {
            robot.store.dispatch(RobotAction.SetClimberVoltage(0.0))
        }
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
        val alliance = DriverStation.getAlliance()
        if (alliance.isPresent) {
            speakerTranslation = if (alliance.get() == DriverStation.Alliance.Red) {
                Translation2d(11.915, 4.035)
            } else {
                Translation2d(4.625, 4.035)
            }
        }

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
