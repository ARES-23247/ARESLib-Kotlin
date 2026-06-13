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
import com.areslib.frc.action.*
import com.areslib.frc.subsystem.*
import com.areslib.frc.state.marvinXIX

import edu.wpi.first.wpilibj.TimedRobot
import edu.wpi.first.wpilibj.XboxController
import edu.wpi.first.wpilibj.RobotBase
import edu.wpi.first.wpilibj.Timer
import edu.wpi.first.wpilibj.DriverStation
import com.ctre.phoenix6.swerve.SwerveDrivetrain

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
    private val coPilotController = XboxController(1)

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

                // Initialize CTRE SwerveDrivetrain using Tuner X constants
                val ctreDrivetrain = frc.robot.generated.TunerConstants.TunerSwerveDrivetrain(
                    frc.robot.generated.TunerConstants.DrivetrainConstants,
                    frc.robot.generated.TunerConstants.FrontLeft,
                    frc.robot.generated.TunerConstants.FrontRight,
                    frc.robot.generated.TunerConstants.BackLeft,
                    frc.robot.generated.TunerConstants.BackRight
                )
                val swerveIO = FRCSwerveHardwareIO(ctreDrivetrain)

                // Initialize Limelight cameras
                val limelightShooter = FrcLimelightIO("limelight-shooter")
                val limelightBack = FrcLimelightIO("limelight-back")
                val compositeVision = com.areslib.hardware.vision.CompositeVisionIO(listOf(limelightShooter, limelightBack))

                FrcSwerveRobot(
                    swerveIO = swerveIO,
                    flywheelIO = FRCFlywheelHardwareIO(leftMasterFX, leftFollowerFX, rightMasterFX, rightFollowerFX),
                    cowlIO = FRCCowlHardwareIO(cowlFX),
                    intakeIO = FRCIntakeHardwareIO(pivotFX, rollerFX),
                    feederIO = FRCFeederHardwareIO(feederFX),
                    floorIO = FRCFloorHardwareIO(floorFX),
                    climberIO = FRCClimberHardwareIO(climberFX),
                    visionIO = compositeVision,
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
        robot.update(controller.toState(), coPilotController.toState())
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

        // ── Copilot Swerve Lock Override ──
        if (coPilotController.xButton) {
            robot.drive.joystickDrive(0.0, 0.0, 0.0)
            return
        }

        // ── Gyro Reset ──
        if (controller.backButton || coPilotController.backButton) {
            robot.store.dispatch(RobotAction.PoseUpdate(
                xMeters = currentPose.x,
                yMeters = currentPose.y,
                headingRadians = 0.0,
                timestampMs = com.areslib.util.RobotClock.currentTimeMillis()
            ))
        }

        // ── Driver / Copilot Shooting Triggers ──
        val rtPressed = controller.rightTriggerAxis > 0.5
        val rbPressed = controller.rightBumper
        val bPressed = controller.bButton
        val copilotRtPressed = coPilotController.rightTriggerAxis > 0.5
        val copilotRbPressed = coPilotController.rightBumper

        when {
            rtPressed -> {
                // Shoot-on-the-Move (SOTM) Speaker Aiming
                val rx = robot.store.state.drive.xVelocityMetersPerSecond
                val ry = robot.store.state.drive.yVelocityMetersPerSecond
                val omega = robot.store.state.drive.angularVelocityRadiansPerSecond
                
                val cos = currentPose.heading.cos
                val sin = currentPose.heading.sin
                val fieldVx = rx * cos - ry * sin
                val fieldVy = rx * sin + ry * cos
                
                val fieldSpeeds = ChassisSpeeds(fieldVx, fieldVy, omega)
                
                ShotSetup.calculate(currentPose, fieldSpeeds, speakerTranslation, shotResult)
                
                robot.store.dispatch(SetFlywheelSpeed(shotResult.targetFlywheelRpm))
                robot.store.dispatch(RobotAction.SetFlywheelActive(true, com.areslib.util.RobotClock.currentTimeMillis()))
                robot.store.dispatch(SetCowlAngle(shotResult.targetCowlAngleDegrees))
                
                val headingError = shotResult.robotTargetHeadingRad - currentPose.heading.radians
                val wrappedError = com.areslib.math.InputMath.wrapAngle(headingError)
                val kp = 4.0
                rotation = wrappedError * kp + shotResult.angularVelocityFeedforwardRadPerSec
                
                val headingAligned = Math.abs(wrappedError) < 0.05
                val rpmAligned = Math.abs(robot.store.state.superstructure.marvinXIX.flywheel.velocityRpm - shotResult.targetFlywheelRpm) < 150.0
                if (headingAligned && rpmAligned) {
                    robot.store.dispatch(SetFeederSpeed(10.0))
                } else {
                    robot.store.dispatch(SetFeederSpeed(0.0))
                }
            }
            rbPressed -> {
                // Aim and Shuttle
                val isRed = alliance.isPresent && alliance.get() == DriverStation.Alliance.Red
                val targetPoses = if (isRed) {
                    listOf(Translation2d(14.6, 6.0), Translation2d(14.6, 2.0))
                } else {
                    listOf(Translation2d(2.0, 6.0), Translation2d(2.0, 2.0))
                }
                val shuttleTarget = targetPoses.minByOrNull { kotlin.math.hypot(it.x - currentPose.x, it.y - currentPose.y) }
                    ?: Translation2d(2.0, 6.0)

                val rx = robot.store.state.drive.xVelocityMetersPerSecond
                val ry = robot.store.state.drive.yVelocityMetersPerSecond
                val omega = robot.store.state.drive.angularVelocityRadiansPerSecond
                
                val cos = currentPose.heading.cos
                val sin = currentPose.heading.sin
                val fieldVx = rx * cos - ry * sin
                val fieldVy = rx * sin + ry * cos
                val fieldSpeeds = ChassisSpeeds(fieldVx, fieldVy, omega)
                
                ShotSetup.calculate(currentPose, fieldSpeeds, shuttleTarget, shotResult)
                
                robot.store.dispatch(SetFlywheelSpeed(shotResult.targetFlywheelRpm))
                robot.store.dispatch(RobotAction.SetFlywheelActive(true, com.areslib.util.RobotClock.currentTimeMillis()))
                robot.store.dispatch(SetCowlAngle(shotResult.targetCowlAngleDegrees))
                
                val headingError = shotResult.robotTargetHeadingRad - currentPose.heading.radians
                val wrappedError = com.areslib.math.InputMath.wrapAngle(headingError)
                val kp = 4.0
                rotation = wrappedError * kp + shotResult.angularVelocityFeedforwardRadPerSec
                
                val headingAligned = Math.abs(wrappedError) < 0.05
                val rpmAligned = Math.abs(robot.store.state.superstructure.marvinXIX.flywheel.velocityRpm - shotResult.targetFlywheelRpm) < 150.0
                if (headingAligned && rpmAligned) {
                    robot.store.dispatch(SetFeederSpeed(10.0))
                    robot.store.dispatch(SetFloorSpeed(10.0))
                } else {
                    robot.store.dispatch(SetFeederSpeed(0.0))
                    robot.store.dispatch(SetFloorSpeed(0.0))
                }
            }
            bPressed -> {
                // Static Shoot (Speaker Aiming)
                val dist = kotlin.math.hypot(currentPose.x - speakerTranslation.x, currentPose.y - speakerTranslation.y)
                val targetRpm = ShotSetup.interpolateRpm(dist)
                val targetCowl = ShotSetup.interpolateCowl(dist)
                
                robot.store.dispatch(SetFlywheelSpeed(targetRpm))
                robot.store.dispatch(RobotAction.SetFlywheelActive(true, com.areslib.util.RobotClock.currentTimeMillis()))
                robot.store.dispatch(SetCowlAngle(targetCowl))
                
                val headingError = Math.atan2(speakerTranslation.y - currentPose.y, speakerTranslation.x - currentPose.x) - currentPose.heading.radians + Math.PI
                val wrappedError = com.areslib.math.InputMath.wrapAngle(headingError)
                val kp = 4.0
                rotation = wrappedError * kp
                
                val headingAligned = Math.abs(wrappedError) < 0.05
                val rpmAligned = Math.abs(robot.store.state.superstructure.marvinXIX.flywheel.velocityRpm - targetRpm) < 150.0
                if (headingAligned && rpmAligned) {
                    robot.store.dispatch(SetFeederSpeed(10.0))
                } else {
                    robot.store.dispatch(SetFeederSpeed(0.0))
                }
            }
            copilotRtPressed -> {
                // Copilot manual Hub shot reference
                robot.store.dispatch(SetFlywheelSpeed(3350.0))
                robot.store.dispatch(SetCowlAngle(0.5))
                robot.store.dispatch(RobotAction.SetFlywheelActive(true, com.areslib.util.RobotClock.currentTimeMillis()))
            }
            copilotRbPressed -> {
                // Copilot manual Front of Ladder shot reference
                robot.store.dispatch(SetFlywheelSpeed(3650.0))
                robot.store.dispatch(SetCowlAngle(1.1))
                robot.store.dispatch(RobotAction.SetFlywheelActive(true, com.areslib.util.RobotClock.currentTimeMillis()))
            }
            else -> {
                robot.store.dispatch(RobotAction.SetFlywheelActive(false, com.areslib.util.RobotClock.currentTimeMillis()))
            }
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
        val copilotLtPressed = coPilotController.leftTriggerAxis > 0.5

        // ── POV Left/Right: Manual Intake Deploy Override ──
        if (controller.pov == 90) {
            intakeDeployed = true
        } else if (controller.pov == 270) {
            intakeDeployed = false
        }

        // Dispatch states according to pilot control priorities
        when {
            lbPressed -> {
                // Unjam sequence takes top priority
                slamtakeActive = false
                robot.store.dispatch(SetIntakePivot(deployed = true))
                robot.store.dispatch(SetIntakeRollers(-5.0))
                robot.store.dispatch(SetFloorSpeed(-5.0))
                robot.store.dispatch(SetFeederSpeed(-5.0))
            }
            slamtakeActive -> {
                val elapsed = Timer.getFPGATimestamp() - slamtakeStartTime
                if (elapsed < 0.5) {
                    robot.store.dispatch(SetIntakePivot(deployed = true))
                    robot.store.dispatch(SetIntakeRollers(10.0))
                    robot.store.dispatch(SetFloorSpeed(10.0))
                    robot.store.dispatch(SetFeederSpeed(0.0))
                } else if (elapsed < 1.5) {
                    robot.store.dispatch(SetIntakePivot(deployed = false))
                    robot.store.dispatch(SetIntakeRollers(10.0))
                    robot.store.dispatch(SetFloorSpeed(10.0))
                    robot.store.dispatch(SetFeederSpeed(0.0))
                } else {
                    slamtakeActive = false
                    robot.store.dispatch(SetIntakePivot(deployed = intakeDeployed))
                    robot.store.dispatch(SetIntakeRollers(0.0))
                    robot.store.dispatch(SetFloorSpeed(0.0))
                    if (!rtPressed && !rbPressed && !bPressed) {
                        robot.store.dispatch(SetFeederSpeed(0.0))
                    }
                }
            }
            ltPressed -> {
                // Active manual intake
                robot.store.dispatch(SetIntakePivot(deployed = true))
                robot.store.dispatch(SetIntakeRollers(10.0))
                robot.store.dispatch(SetFloorSpeed(10.0))
                robot.store.dispatch(SetFeederSpeed(10.0))
            }
            copilotLtPressed -> {
                // Copilot manual feed override
                robot.store.dispatch(SetIntakePivot(deployed = intakeDeployed))
                robot.store.dispatch(SetIntakeRollers(10.0))
                robot.store.dispatch(SetFloorSpeed(10.0))
                robot.store.dispatch(SetFeederSpeed(10.0))
            }
            else -> {
                // Default stop everything
                robot.store.dispatch(SetIntakePivot(deployed = intakeDeployed))
                robot.store.dispatch(SetIntakeRollers(0.0))
                robot.store.dispatch(SetFloorSpeed(0.0))
                if (!rtPressed && !rbPressed && !bPressed) {
                    robot.store.dispatch(SetFeederSpeed(0.0))
                }
            }
        }

        // ── POV Up/Down: Climber Voltage (Driver or Copilot) ──
        val povUp = controller.pov == 0 || coPilotController.pov == 0
        val povDown = controller.pov == 180 || coPilotController.pov == 180
        if (povUp) {
            robot.store.dispatch(SetClimberVoltage(6.0))
        } else if (povDown) {
            robot.store.dispatch(SetClimberVoltage(-6.0))
        } else {
            robot.store.dispatch(SetClimberVoltage(0.0))
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
                    "FlywheelOn" -> robot.marvinShooter.spinUp(4000.0)
                    "IntakeDeploy" -> {
                        robot.marvinIntake.deploy()
                        robot.marvinIntake.setRollerSpeed(15.0)
                    }
                    "FeederShoot" -> robot.marvinShooter.shoot()
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
