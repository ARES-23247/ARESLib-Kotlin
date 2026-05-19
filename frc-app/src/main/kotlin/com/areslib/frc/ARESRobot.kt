package com.areslib.frc

import com.areslib.state.DriveState
import com.areslib.state.RobotState
import com.areslib.reducer.rootReducer
import com.areslib.pathing.Path
import com.areslib.pathing.PathPoint
import com.areslib.control.HolonomicDriveController
import com.areslib.control.PIDController
import com.areslib.math.Rotation2d
import com.areslib.math.Pose2d
import com.areslib.hardware.FlywheelIO
import com.areslib.hardware.CowlIO
import com.areslib.hardware.IntakeIO
import com.areslib.hardware.FeederIO
import com.areslib.sim.FlywheelSim
import com.areslib.sim.IntakePivotSim
import com.areslib.action.RobotAction

import edu.wpi.first.wpilibj.TimedRobot
import edu.wpi.first.wpilibj.XboxController
import edu.wpi.first.wpilibj.RobotController
import edu.wpi.first.wpilibj.Timer
import edu.wpi.first.wpilibj.RobotBase
import com.ctre.phoenix6.mechanisms.swerve.SwerveDrivetrain

import org.dyn4j.dynamics.Body
import org.dyn4j.world.World
import org.dyn4j.geometry.Geometry
import org.dyn4j.geometry.MassType

/**
 * Main Robot loop for the FRC CTRE Swerve Integration.
 * Replaces the traditional command-based FRC robot with the pure mathematical Redux state machine.
 */
class ARESRobot : TimedRobot() {

    // Redux State
    private var currentState = RobotState(
        drive = DriveState(
            odometryX = 2.0, 
            odometryY = 2.0, 
            odometryHeading = 0.0
        )
    )
    
    // Telemetry and Logging
    private val telemetry = FRCTelemetry()
    
    // Virtual/Physical Gamepad
    private val driverController = XboxController(0)
    
    // Hardware IO Bridge
    // In a real robot project, you would instantiate this with the generated CommandSwerveDrivetrain from Phoenix Tuner X.
    private var swerveIO: FRCSwerveHardwareIO<SwerveDrivetrain>? = null

    // Edge detector state for LB toggle
    private var prevLB = false
    private var intakeDeployed = false

    // Dyn4j Physics Drivetrain
    private val world = World<Body>()
    private val robotBody = Body()
    private val balls = mutableListOf<Body>()
    private var lastSimTime = 0.0

    // Sim models for Superstructure
    private val flywheelSim = FlywheelSim()
    private val intakePivotSim = IntakePivotSim()
    private var simFlywheelVoltage = 0.0
    private var simCowlVoltage = 0.0
    private var simIntakePivotVoltage = 0.0
    private var simIntakeRollerVoltage = 0.0
    private var simFeederVoltage = 0.0
    private var simCowlAngle = 0.0
    private var simFeederPieceDetected = false
    private var simFlywheelRotationAngle = 0.0

    private val simulatedFlywheelIO = object : FlywheelIO {
        override fun setVelocityRpm(rpm: Double) {
            val error = rpm - flywheelSim.velocityRpm
            simFlywheelVoltage = (error * 0.003).coerceIn(-12.0, 12.0)
        }
        override fun setAppliedVoltage(volts: Double) {
            simFlywheelVoltage = volts.coerceIn(-12.0, 12.0)
        }
        override val velocityRpm: Double
            get() = flywheelSim.velocityRpm
        override val currentAmps: Double
            get() = flywheelSim.getCurrentAmps(simFlywheelVoltage)
        override val tempCelsius: Double
            get() = 30.0
    }

    private val simulatedCowlIO = object : CowlIO {
        override fun setTargetAngle(degrees: Double) {
            val error = degrees - simCowlAngle
            simCowlVoltage = (error * 0.5).coerceIn(-12.0, 12.0)
        }
        override fun setAppliedVoltage(volts: Double) {
            simCowlVoltage = volts.coerceIn(-12.0, 12.0)
        }
        override val angleDegrees: Double
            get() = simCowlAngle
        override val currentAmps: Double
            get() = Math.abs(simCowlVoltage) * 0.2
    }

    private val simulatedIntakeIO = object : IntakeIO {
        override fun setPivotAngle(degrees: Double) {
            val error = degrees - intakePivotSim.angleDegrees
            simIntakePivotVoltage = (error * 0.4).coerceIn(-12.0, 12.0)
        }
        override fun setPivotVoltage(volts: Double) {
            simIntakePivotVoltage = volts.coerceIn(-12.0, 12.0)
        }
        override fun setRollerVoltage(volts: Double) {
            simIntakeRollerVoltage = volts.coerceIn(-12.0, 12.0)
        }
        override val pivotAngleDegrees: Double
            get() = intakePivotSim.angleDegrees
        override val pivotCurrentAmps: Double
            get() = Math.abs(simIntakePivotVoltage) * 0.3
        override val rollerCurrentAmps: Double
            get() = Math.abs(simIntakeRollerVoltage) * 0.2
    }

    private val simulatedFeederIO = object : FeederIO {
        override fun setAppliedVoltage(volts: Double) {
            simFeederVoltage = volts.coerceIn(-12.0, 12.0)
        }
        override val isBeamBroken: Boolean
            get() = simFeederPieceDetected
        override val currentAmps: Double
            get() = Math.abs(simFeederVoltage) * 0.1
    }

    // Poly superstructure IO
    private var flywheelIO: FlywheelIO = simulatedFlywheelIO
    private var cowlIO: CowlIO = simulatedCowlIO
    private var intakeIO: IntakeIO = simulatedIntakeIO
    private var feederIO: FeederIO = simulatedFeederIO

    // PathPlanner Trajectory Follower
    private var activePath: Path? = null
    private var autoStartTime = 0.0
    private var autoDistance = 0.0

    private val driveController = HolonomicDriveController(
        PIDController(4.0, 0.0, 0.1),
        PIDController(4.0, 0.0, 0.1),
        PIDController(3.0, 0.0, 0.0)
    )

    override fun robotInit() {
        if (RobotBase.isReal()) {
            try {
                // In a production robot, you would instantiate with the generated Constants:
                // swerveIO = FRCSwerveHardwareIO(TunerConstants.DriveTrain)
                
                val leaderFX = com.ctre.phoenix6.hardware.TalonFX(10)
                val followerFX = com.ctre.phoenix6.hardware.TalonFX(11)
                flywheelIO = FRCFlywheelHardwareIO(leaderFX, followerFX)

                val cowlFX = com.ctre.phoenix6.hardware.TalonFX(12)
                cowlIO = FRCCowlHardwareIO(cowlFX)

                val pivotFX = com.ctre.phoenix6.hardware.TalonFX(13)
                val rollerFX = com.ctre.phoenix6.hardware.TalonFX(14)
                val encoder = com.ctre.phoenix6.hardware.CANcoder(15)
                intakeIO = FRCIntakeHardwareIO(pivotFX, rollerFX, encoder)

                val feederFX = com.ctre.phoenix6.hardware.TalonFX(16)
                val beamBreak = edu.wpi.first.wpilibj.DigitalInput(0)
                feederIO = FRCFeederHardwareIO(feederFX, beamBreak)
            } catch (e: Exception) {
                println("Failed to initialize physical hardware: ${e.message}")
            }
        }
        
        world.setGravity(org.dyn4j.geometry.Vector2(0.0, 0.0))
        
        // Robot Setup
        val robotFixture = robotBody.addFixture(Geometry.createRectangle(0.7, 0.7)) // ~28 inch square
        robotFixture.density = 78.0
        robotBody.linearDamping = 1.0
        robotBody.angularDamping = 2.0
        robotBody.setMass(MassType.NORMAL)
        robotBody.translate(2.0, 2.0)
        world.addBody(robotBody)
        
        createWalls()
        spawnFuel()
        
        lastSimTime = Timer.getFPGATimestamp()
    }

    override fun autonomousInit() {
        try {
            activePath = PathLoader.loadPath("SimPath")
            val startPoint = activePath?.points?.firstOrNull()
            if (startPoint != null) {
                // Snap Redux State
                currentState = currentState.copy(
                    drive = currentState.drive.copy(
                        odometryX = startPoint.pose.x,
                        odometryY = startPoint.pose.y,
                        odometryHeading = startPoint.pose.heading.radians
                    )
                )
                
                // Snap Dyn4j Physics Body to match initial trajectory pose
                robotBody.transform.setTranslation(startPoint.pose.x, startPoint.pose.y)
                robotBody.transform.setRotation(startPoint.pose.heading.radians)
                robotBody.linearVelocity.set(0.0, 0.0)
                robotBody.angularVelocity = 0.0
                robotBody.isAtRest = false
            }
        } catch (e: Exception) {
            println("ERROR: Failed to load autonomous path SimPath: ${e.message}")
            activePath = null
        }
        autoStartTime = Timer.getFPGATimestamp()
        autoDistance = 0.0
    }

    override fun autonomousPeriodic() {
        val path = activePath
        if (path != null) {
            val dt = 0.02 // TimedRobot standard loop period (20ms)
            
            val currentPose = Pose2d(
                currentState.drive.odometryX,
                currentState.drive.odometryY,
                Rotation2d(currentState.drive.odometryHeading)
            )
            
            // Sample path
            val targetPoint = path.sampleAtDistance(autoDistance)
            
            // Compute desired ChassisSpeeds (robot-relative)
            val speeds = driveController.calculate(
                currentPose = currentPose,
                targetPose = targetPoint.pose,
                targetVelocityMps = targetPoint.velocityMps,
                targetHeading = targetPoint.pose.heading,
                dtSeconds = dt
            )
            
            // Rotate robot-relative speeds back to field-relative velocities for the simulation
            val cos = currentPose.heading.cos
            val sin = currentPose.heading.sin
            val fieldX = speeds.vxMetersPerSecond * cos - speeds.vyMetersPerSecond * sin
            val fieldY = speeds.vxMetersPerSecond * sin + speeds.vyMetersPerSecond * cos
            
            // Write target velocities to state
            currentState = currentState.copy(
                drive = currentState.drive.copy(
                    xVelocityMetersPerSecond = fieldX,
                    yVelocityMetersPerSecond = fieldY,
                    angularVelocityRadiansPerSecond = speeds.omegaRadiansPerSecond
                )
            )
            
            // Check for event markers
            for (event in path.events) {
                val prevDistance = autoDistance
                val nextDistance = autoDistance + targetPoint.velocityMps * dt
                if (event.triggerDistanceMeters in prevDistance..nextDistance) {
                    println("AUTO EVENT TRIGGERED: ${event.eventName} at distance ${event.triggerDistanceMeters}m")
                    telemetry.putString("Robot/ActiveEvent", event.eventName)
                    
                    val timeNow = System.currentTimeMillis()
                    when (event.eventName) {
                        "FlywheelOn" -> {
                            currentState = rootReducer(currentState, RobotAction.SetFlywheelActive(true, timeNow))
                            currentState = rootReducer(currentState, RobotAction.SetFlywheelSpeed(4000.0, timeNow))
                        }
                        "IntakeDeploy" -> {
                            currentState = rootReducer(currentState, RobotAction.SetIntakeActive(true, timeNow))
                            currentState = rootReducer(currentState, RobotAction.SetIntakePivot(true, timeNow))
                            currentState = rootReducer(currentState, RobotAction.SetIntakeRollers(15.0, timeNow))
                        }
                        "FeederShoot" -> {
                            currentState = rootReducer(currentState, RobotAction.SetInventoryCount(1, timeNow))
                            currentState = rootReducer(currentState, RobotAction.SetFeederSpeed(20.0, timeNow))
                            currentState = rootReducer(currentState, RobotAction.SetTransferActive(true, timeNow))
                        }
                    }
                }
            }

            // Stream target trajectory and errors to AdvantageScope
            telemetry.putDoubleArray("Robot/TargetPose", doubleArrayOf(
                targetPoint.pose.x,
                targetPoint.pose.y,
                targetPoint.pose.heading.radians
            ))
            
            val dx = targetPoint.pose.x - currentPose.x
            val dy = targetPoint.pose.y - currentPose.y
            val error = kotlin.math.hypot(dx, dy)
            telemetry.putNumber("Robot/TrajectoryError", error)

            // Propagate distance along profile
            autoDistance += targetPoint.velocityMps * dt
        }
    }

    override fun robotPeriodic() {
        // 1. READ (Hardware -> State)
        val timestamp = System.currentTimeMillis()
        
        val driveState = if (RobotBase.isReal() && swerveIO != null) {
            swerveIO!!.read()
        } else {
            currentState.drive // Use previous/simulation state
        }
        
        // Merge drive state
        currentState = currentState.copy(
            timestampMs = timestamp,
            drive = driveState
        )

        // Read and merge superstructure sensors
        val sensorAction = RobotAction.SuperstructureSensorUpdate(
            flywheelRpm = if (RobotBase.isReal()) flywheelIO.velocityRpm else flywheelSim.velocityRpm,
            cowlAngle = if (RobotBase.isReal()) cowlIO.angleDegrees else simCowlAngle,
            intakeAngle = if (RobotBase.isReal()) intakeIO.pivotAngleDegrees else intakePivotSim.angleDegrees,
            pieceDetected = if (RobotBase.isReal()) feederIO.isBeamBroken else simFeederPieceDetected,
            timestampMs = timestamp
        )
        currentState = rootReducer(currentState, sensorAction)
        
        // 2. STATE MERGE & INPUT MAP (Xbox -> Actions -> Reducer)
        if (isTeleop && driverController.isConnected) {
            // Apply deadbands so it doesn't drift
            val applyDeadband = { value: Double -> if (Math.abs(value) < 0.1) 0.0 else value }
            val forward = applyDeadband(-driverController.leftY)
            val strafe = applyDeadband(-driverController.leftX)
            val rotation = applyDeadband(-driverController.rightX)
            
            val vx = forward * 4.5
            val vy = strafe * 4.5
            val omega = rotation * Math.PI
            
            currentState = currentState.copy(
                drive = currentState.drive.copy(
                    xVelocityMetersPerSecond = vx,
                    yVelocityMetersPerSecond = vy,
                    angularVelocityRadiansPerSecond = omega
                )
            )

            // Right Bumper (RB): Spins flywheel to 4000 RPM
            val rbPressed = driverController.rightBumper
            currentState = rootReducer(currentState, RobotAction.SetFlywheelActive(rbPressed, timestamp))
            currentState = rootReducer(currentState, RobotAction.SetFlywheelSpeed(if (rbPressed) 4000.0 else 0.0, timestamp))

            // Right Trigger (RT): Runs Feeder/Transfer when flywheel is ready
            val rtPressed = driverController.rightTriggerAxis > 0.5
            currentState = rootReducer(currentState, RobotAction.SetTransferActive(rtPressed, timestamp))
            currentState = rootReducer(currentState, RobotAction.SetFeederSpeed(if (rtPressed) 12.0 else 0.0, timestamp))

            // Left Bumper (LB): Toggles Intake deployment (extend/retract)
            val currLB = driverController.leftBumper
            if (currLB && !prevLB) {
                intakeDeployed = !intakeDeployed
                currentState = rootReducer(currentState, RobotAction.SetIntakePivot(intakeDeployed, timestamp))
                currentState = rootReducer(currentState, RobotAction.SetIntakeActive(intakeDeployed, timestamp))
            }
            prevLB = currLB

            // Left Trigger (LT): Runs Intake rollers
            val ltPressed = driverController.leftTriggerAxis > 0.5
            currentState = rootReducer(currentState, RobotAction.SetIntakeRollers(if (ltPressed) 10.0 else 0.0, timestamp))

            // DPad Up/Down: Cowl angle control
            if (driverController.getPOV() == 0) {
                currentState = rootReducer(currentState, RobotAction.SetCowlAngle(45.0, timestamp))
            } else if (driverController.getPOV() == 180) {
                currentState = rootReducer(currentState, RobotAction.SetCowlAngle(15.0, timestamp))
            }
        } else {
            // Reset target velocities and superstructure states to idle when disabled/disconnected
            currentState = currentState.copy(
                drive = currentState.drive.copy(
                    xVelocityMetersPerSecond = 0.0,
                    yVelocityMetersPerSecond = 0.0,
                    angularVelocityRadiansPerSecond = 0.0
                )
            )
            // Transition superstructure to idle/inactive
            currentState = rootReducer(currentState, RobotAction.SetFlywheelActive(false, timestamp))
            currentState = rootReducer(currentState, RobotAction.SetFlywheelSpeed(0.0, timestamp))
            currentState = rootReducer(currentState, RobotAction.SetTransferActive(false, timestamp))
            currentState = rootReducer(currentState, RobotAction.SetFeederSpeed(0.0, timestamp))
            currentState = rootReducer(currentState, RobotAction.SetIntakeRollers(0.0, timestamp))
        }
        
        // 3. WRITE (State -> Hardware/Sim Polymorphically)
        if (RobotBase.isReal() && swerveIO != null) {
            swerveIO!!.write(currentState.drive)
        }

        // Write to superstructure outputs polymorphically
        flywheelIO.setVelocityRpm(currentState.superstructure.flywheel.targetVelocityRpm)
        cowlIO.setTargetAngle(currentState.superstructure.cowl.targetAngleDegrees)
        intakeIO.setPivotAngle(currentState.superstructure.intake.targetAngleDegrees)
        
        val targetRollerSpeed = currentState.superstructure.intake.targetRollerVelocityRps
        intakeIO.setRollerVoltage((targetRollerSpeed / 10.0) * 12.0)
        
        val targetFeederSpeed = currentState.superstructure.feeder.targetVelocityRps
        feederIO.setAppliedVoltage((targetFeederSpeed / 12.0) * 12.0)
        
        // 4. TELEMETRY (Log deterministic state)
        val robotHeading = currentState.drive.odometryHeading
        val robotX = currentState.drive.odometryX
        val robotY = currentState.drive.odometryY
        
        telemetry.putNumber("Robot/Odometry/X", robotX)
        telemetry.putNumber("Robot/Odometry/Y", robotY)
        telemetry.putNumber("Robot/Odometry/Heading", robotHeading)
        telemetry.putDoubleArray("Robot/Pose", doubleArrayOf(robotX, robotY, robotHeading))
        
        // Log EKF covariance diagonals
        val cov = currentState.drive.poseEstimator.covariance
        telemetry.putDoubleArray("Robot/Odometry/Covariance", doubleArrayOf(cov.m00, cov.m11, cov.m22))

        // Calculate and publish 3D poses for AdvantageScope
        val halfHeading = robotHeading / 2.0
        val robotQW = Math.cos(halfHeading)
        val robotQZ = Math.sin(halfHeading)
        
        val robotPose3d = doubleArrayOf(robotX, robotY, 0.0, robotQW, 0.0, 0.0, robotQZ)
        telemetry.putDoubleArray("Robot/Pose3d", robotPose3d)

        // Intake Pivot 3D Pose: mounted at front center, height = 0.2, pitch = intakeAngle
        val intakeAngleRad = Math.toRadians(if (RobotBase.isReal()) intakeIO.pivotAngleDegrees else intakePivotSim.angleDegrees)
        val halfIntake = intakeAngleRad / 2.0
        val intCosY = Math.cos(halfIntake)
        val intSinY = Math.sin(halfIntake)
        val intQW = robotQW * intCosY
        val intQX = -robotQZ * intSinY
        val intQY = robotQW * intSinY
        val intQZ = robotQZ * intCosY
        
        val intakePose3d = doubleArrayOf(
            robotX + 0.35 * Math.cos(robotHeading),
            robotY + 0.35 * Math.sin(robotHeading),
            0.2,
            intQW, intQX, intQY, intQZ
        )
        telemetry.putDoubleArray("Robot/Superstructure/3D/Intake", intakePose3d)

        // Cowl Hood 3D Pose: mounted at rear/middle, height = 0.6, pitch = cowlAngle
        val cowlAngleRad = Math.toRadians(if (RobotBase.isReal()) cowlIO.angleDegrees else simCowlAngle)
        val halfCowl = cowlAngleRad / 2.0
        val cowlCosY = Math.cos(halfCowl)
        val cowlSinY = Math.sin(halfCowl)
        val cowlQW = robotQW * cowlCosY
        val cowlQX = -robotQZ * cowlSinY
        val cowlQY = robotQW * cowlSinY
        val cowlQZ = robotQZ * cowlCosY
        
        val cowlPose3d = doubleArrayOf(
            robotX - 0.2 * Math.cos(robotHeading),
            robotY - 0.2 * Math.sin(robotHeading),
            0.6,
            cowlQW, cowlQX, cowlQY, cowlQZ
        )
        telemetry.putDoubleArray("Robot/Superstructure/3D/Cowl", cowlPose3d)

        // Flywheel 3D Pose: spinning, height = 0.6, pitch = simFlywheelRotationAngle
        val halfFlywheel = simFlywheelRotationAngle / 2.0
        val flyCosY = Math.cos(halfFlywheel)
        val flySinY = Math.sin(halfFlywheel)
        val flyQW = robotQW * flyCosY
        val flyQX = -robotQZ * flySinY
        val flyQY = robotQW * flySinY
        val flyQZ = robotQZ * flyCosY
        
        val flywheelPose3d = doubleArrayOf(
            robotX - 0.1 * Math.cos(robotHeading),
            robotY - 0.1 * Math.sin(robotHeading),
            0.6,
            flyQW, flyQX, flyQY, flyQZ
        )
        telemetry.putDoubleArray("Robot/Superstructure/3D/Flywheel", flywheelPose3d)

        // Swerve Module Kinematics and Module States
        val vx = currentState.drive.xVelocityMetersPerSecond
        val vy = currentState.drive.yVelocityMetersPerSecond
        val omega = currentState.drive.angularVelocityRadiansPerSecond
        
        val offsets = arrayOf(
            Pair(0.35, 0.35),   // FL
            Pair(0.35, -0.35),  // FR
            Pair(-0.35, 0.35),  // RL
            Pair(-0.35, -0.35)  // RR
        )
        val swerveStates = DoubleArray(8)
        for (i in 0..3) {
            val ox = offsets[i].first
            val oy = offsets[i].second
            val wvx = vx - omega * oy
            val wvy = vy + omega * ox
            val speed = Math.hypot(wvx, wvy)
            val angle = Math.atan2(wvy, wvx)
            swerveStates[i * 2] = angle
            swerveStates[i * 2 + 1] = speed
        }
        telemetry.putDoubleArray("Robot/SwerveStates", swerveStates)

        // Superstructure Telemetry (Phase 72 & 73 groundwork)
        telemetry.putString("Robot/Superstructure/Mode", currentState.superstructure.mode.name)
        telemetry.putNumber("Robot/Superstructure/FlywheelRPM", currentState.superstructure.flywheel.velocityRpm)
        telemetry.putNumber("Robot/Superstructure/FlywheelTargetRPM", currentState.superstructure.flywheel.targetVelocityRpm)
        telemetry.putNumber("Robot/Superstructure/CowlAngle", currentState.superstructure.cowl.angleDegrees)
        telemetry.putNumber("Robot/Superstructure/CowlTargetAngle", currentState.superstructure.cowl.targetAngleDegrees)
        telemetry.putNumber("Robot/Superstructure/IntakeAngle", currentState.superstructure.intake.pivotAngleDegrees)
        telemetry.putNumber("Robot/Superstructure/IntakeTargetAngle", currentState.superstructure.intake.targetAngleDegrees)
        telemetry.putBoolean("Robot/Superstructure/FeederPieceDetected", currentState.superstructure.feeder.gamePieceDetected)
        telemetry.putNumber("Robot/Superstructure/InventoryCount", currentState.superstructure.inventoryCount.toDouble())
    }

    override fun simulationPeriodic() {
        if (!RobotBase.isSimulation()) return
        
        val now = Timer.getFPGATimestamp()
        val dt = Math.min(now - lastSimTime, 0.05)
        lastSimTime = now
        val timestamp = System.currentTimeMillis()

        if (dt > 0.0) {
            // Step Dyn4j Physics Engine
            val kpLinear = 50.0
            val kpAngular = 20.0
            val forceX = (currentState.drive.xVelocityMetersPerSecond - robotBody.linearVelocity.x) * kpLinear
            val forceY = (currentState.drive.yVelocityMetersPerSecond - robotBody.linearVelocity.y) * kpLinear
            val torque = (currentState.drive.angularVelocityRadiansPerSecond - robotBody.angularVelocity) * kpAngular

            robotBody.isAtRest = false
            robotBody.applyForce(org.dyn4j.geometry.Vector2(forceX, forceY))
            robotBody.applyTorque(torque)
            
            world.step(1, dt)

            // Read back the pose from physics
            val t = robotBody.transform
            currentState = currentState.copy(
                drive = currentState.drive.copy(
                    odometryX = t.translationX,
                    odometryY = t.translationY,
                    odometryHeading = t.rotationAngle
                )
            )

            // STEP SUPERSTRUCTURE PHYSICS
            flywheelSim.update(simFlywheelVoltage, dt)
            intakePivotSim.update(simIntakePivotVoltage, dt)
            
            // Step flywheel visual angle
            val flywheelRps = (if (RobotBase.isReal()) flywheelIO.velocityRpm else flywheelSim.velocityRpm) / 60.0
            simFlywheelRotationAngle += (flywheelRps * 2.0 * Math.PI) * dt
            
            // Sim Cowl Hood Position Integrating Voltage
            simCowlAngle += (simCowlVoltage * 15.0) * dt // Simple cowl angle motor speed approximation
            simCowlAngle = simCowlAngle.coerceIn(0.0, 70.0) // 0 to 70 deg hood travel
            
            // Game Piece collision detection
            val robotX = t.translationX
            val robotY = t.translationY
            val robotHeading = t.rotationAngle

            // Check if we can intake balls
            val intakeDeployed = intakePivotSim.angleDegrees > 45.0
            val intakeSpinning = simIntakeRollerVoltage > 1.0 // Intake roller active
            
            if (intakeDeployed && intakeSpinning && currentState.superstructure.inventoryCount < 3) {
                val ballIterator = balls.iterator()
                while (ballIterator.hasNext()) {
                    val ball = ballIterator.next()
                    val bx = ball.transform.translationX
                    val by = ball.transform.translationY
                    val dist = Math.hypot(bx - robotX, by - robotY)
                    if (dist < 0.5) { // Collision range with front bumper
                        // Remove ball from world & list
                        world.removeBody(ball)
                        ballIterator.remove()
                        
                        // Dispatch SetInventoryCount action
                        val newCount = currentState.superstructure.inventoryCount + 1
                        currentState = rootReducer(currentState, RobotAction.SetInventoryCount(newCount, timestamp))
                        simFeederPieceDetected = true
                        println("BALL INGESTED! Inventory: $newCount")
                        break
                    }
                }
            }

            // Check if we are shooting
            val flywheelAtSpeed = currentState.superstructure.isFlywheelAtSpeed
            val feederSpinning = simFeederVoltage > 2.0
            if (flywheelAtSpeed && feederSpinning && currentState.superstructure.inventoryCount > 0) {
                // SHOOT!
                val newCount = currentState.superstructure.inventoryCount - 1
                currentState = rootReducer(currentState, RobotAction.SetInventoryCount(newCount, timestamp))
                simFeederPieceDetected = newCount > 0
                
                // Spawn a ball at the robot's front going fast!
                val shootSpeed = 12.0 // m/s
                val bx = robotX + Math.cos(robotHeading) * 0.5
                val by = robotY + Math.sin(robotHeading) * 0.5
                val vx = robotBody.linearVelocity.x + Math.cos(robotHeading) * shootSpeed
                val vy = robotBody.linearVelocity.y + Math.sin(robotHeading) * shootSpeed
                
                val newBall = Body()
                val fixture = newBall.addFixture(Geometry.createCircle(0.0635))
                fixture.friction = 0.6
                fixture.restitution = 0.4
                fixture.density = 5.92
                newBall.setMass(MassType.NORMAL)
                newBall.linearDamping = 0.5
                newBall.angularDamping = 0.5
                newBall.translate(bx, by)
                newBall.linearVelocity.set(vx, vy)
                
                world.addBody(newBall)
                balls.add(newBall)
                
                println("BALL SHOT! Inventory left: $newCount")
            }
        }
        
        // Publish Fuel 3D Array
        val gamePieceData = DoubleArray(balls.size * 7)
        for (i in balls.indices) {
            gamePieceData[i * 7] = balls[i].transform.translationX
            gamePieceData[i * 7 + 1] = balls[i].transform.translationY
            gamePieceData[i * 7 + 2] = 0.0635 // Z height (radius)
            
            val theta = balls[i].transform.rotationAngle
            gamePieceData[i * 7 + 3] = kotlin.math.cos(theta / 2.0) // qW
            gamePieceData[i * 7 + 4] = 0.0                          // qX
            gamePieceData[i * 7 + 5] = 0.0                          // qY
            gamePieceData[i * 7 + 6] = kotlin.math.sin(theta / 2.0) // qZ
        }
        telemetry.putDoubleArray("Robot/FuelPoses", gamePieceData)
    }
    
    private fun createWalls() {
        val width = 16.541
        val height = 8.069
        
        // 4 outer walls
        val top = Body()
        top.addFixture(Geometry.createRectangle(width, 0.1))
        top.setMass(MassType.INFINITE)
        top.translate(width / 2.0, height)
        world.addBody(top)
        
        val bottom = Body()
        bottom.addFixture(Geometry.createRectangle(width, 0.1))
        bottom.setMass(MassType.INFINITE)
        bottom.translate(width / 2.0, 0.0)
        world.addBody(bottom)
        
        val left = Body()
        left.addFixture(Geometry.createRectangle(0.1, height))
        left.setMass(MassType.INFINITE)
        left.translate(0.0, height / 2.0)
        world.addBody(left)
        
        val right = Body()
        right.addFixture(Geometry.createRectangle(0.1, height))
        right.setMass(MassType.INFINITE)
        right.translate(width, height / 2.0)
        world.addBody(right)
        
        // Blue Hub
        val blueHub = Body()
        blueHub.addFixture(Geometry.createRectangle(1.1938, 1.1938))
        blueHub.setMass(MassType.INFINITE)
        blueHub.translate(4.135, 4.0345)
        world.addBody(blueHub)
        
        // Red Hub
        val redHub = Body()
        redHub.addFixture(Geometry.createRectangle(1.1938, 1.1938))
        redHub.setMass(MassType.INFINITE)
        redHub.translate(16.541 - 4.135, 4.0345)
        world.addBody(redHub)
    }

    private fun spawnFuel() {
        val random = java.util.Random()
        var spawned = 0
        while (spawned < 100) {
            val x = 1.0 + random.nextDouble() * 14.0
            val y = 1.0 + random.nextDouble() * 6.0
            
            // Check Blue Hub overlap
            val dxBlue = x - 4.135
            val dyBlue = y - 4.0345
            if (dxBlue * dxBlue + dyBlue * dyBlue < 0.9) continue
            
            // Check Red Hub overlap
            val dxRed = x - 12.406
            val dyRed = y - 4.0345
            if (dxRed * dxRed + dyRed * dyRed < 0.9) continue
            
            // Check Robot starting position overlap
            val dxRobot = x - 2.0
            val dyRobot = y - 2.0
            if (dxRobot * dxRobot + dyRobot * dyRobot < 0.7) continue
            
            val ball = Body()
            val fixture = ball.addFixture(Geometry.createCircle(0.0635)) // 5 inch diameter
            fixture.friction = 0.6
            fixture.restitution = 0.4
            fixture.density = 5.92
            ball.setMass(MassType.NORMAL)
            ball.linearDamping = 2.0
            ball.angularDamping = 2.0
            
            ball.translate(x, y)
            world.addBody(ball)
            balls.add(ball)
            spawned++
        }
    }
}
