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

    // Dyn4j Physics
    private val world = World<Body>()
    private val robotBody = Body()
    private val balls = mutableListOf<Body>()
    private var lastSimTime = 0.0

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
            // In a production robot, you would instantiate with the generated Constants:
            // swerveIO = FRCSwerveHardwareIO(TunerConstants.DriveTrain)
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
        
        // 2. STATE MERGE (Hardware -> State)
        currentState = currentState.copy(
            timestampMs = timestamp,
            drive = driveState
        )
        
        // In teleop, we would map the XboxController to ChassisSpeeds or commands here.
        if (isTeleop && driverController.isConnected) {
            // Apply deadbands so it doesn't drift
            val applyDeadband = { value: Double -> if (Math.abs(value) < 0.1) 0.0 else value }
            val forward = applyDeadband(-driverController.leftY)
            val strafe = applyDeadband(-driverController.leftX)
            val rotation = applyDeadband(-driverController.rightX)
            
            val vx = forward * 4.5
            val vy = strafe * 4.5
            val omega = rotation * Math.PI
            
            // For example, mapping to DriveState velocities (Placeholder math)
            currentState = currentState.copy(
                drive = currentState.drive.copy(
                    xVelocityMetersPerSecond = vx,
                    yVelocityMetersPerSecond = vy,
                    angularVelocityRadiansPerSecond = omega
                )
            )
        } else {
            // Reset target velocities to 0 when disabled or controller disconnected
            currentState = currentState.copy(
                drive = currentState.drive.copy(
                    xVelocityMetersPerSecond = 0.0,
                    yVelocityMetersPerSecond = 0.0,
                    angularVelocityRadiansPerSecond = 0.0
                )
            )
        }
        
        // 3. WRITE (State -> Hardware)
        if (RobotBase.isReal() && swerveIO != null) {
            swerveIO!!.write(currentState.drive)
        }
        
        // 4. TELEMETRY (Log deterministic state)
        telemetry.putNumber("Robot/Odometry/X", currentState.drive.odometryX)
        telemetry.putNumber("Robot/Odometry/Y", currentState.drive.odometryY)
        telemetry.putNumber("Robot/Odometry/Heading", currentState.drive.odometryHeading)
        telemetry.putDoubleArray("Robot/Pose", doubleArrayOf(
            currentState.drive.odometryX, 
            currentState.drive.odometryY, 
            currentState.drive.odometryHeading
        ))
    }

    override fun simulationPeriodic() {
        if (!RobotBase.isSimulation()) return
        
        val now = Timer.getFPGATimestamp()
        val dt = Math.min(now - lastSimTime, 0.05)
        lastSimTime = now

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
