package com.areslib.frc

import com.areslib.action.RobotAction
import com.areslib.hardware.FlywheelIO
import com.areslib.hardware.CowlIO
import com.areslib.hardware.IntakeIO
import com.areslib.hardware.FeederIO
import com.areslib.sim.FlywheelSim
import com.areslib.sim.IntakePivotSim
import com.areslib.state.RobotState
import com.areslib.telemetry.ITelemetry

import org.dyn4j.dynamics.Body
import org.dyn4j.world.World
import org.dyn4j.geometry.Geometry
import org.dyn4j.geometry.MassType
import org.dyn4j.geometry.Vector2

/**
 * Self-contained Dyn4j physics simulation for the FRC swerve robot.
 *
 * Owns the physics world, robot body, fuel balls, and simulated hardware IO objects.
 * The ARESRobot TimedRobot shell calls [step] each cycle and reads results back
 * via [getPoseUpdate] and the simulated IO properties.
 */
class Dyn4jSimulation(seed: Long = 42L) {

    // ── Physics World ──
    private val world = World<Body>()
    private val robotBody = Body()
    private val balls = mutableListOf<Body>()

    // ── Sim Models ──
    private val flywheelSim = FlywheelSim()
    private val intakePivotSim = IntakePivotSim()

    // ── Simulated Voltages (written by IO, consumed by step) ──
    private var simFlywheelVoltage = 0.0
    private var simCowlVoltage = 0.0
    private var simIntakePivotVoltage = 0.0
    private var simIntakeRollerVoltage = 0.0
    private var simFeederVoltage = 0.0
    private var simCowlAngle = 0.0
    private var simFeederPieceDetected = false
    var flywheelRotationAngle = 0.0
        private set

    // ── Simulated Hardware IO Objects ──

    val flywheelIO: FlywheelIO = object : FlywheelIO {
        override fun setVelocityRpm(rpm: Double) {
            val error = rpm - flywheelSim.velocityRpm
            simFlywheelVoltage = (error * 0.003).coerceIn(-12.0, 12.0)
        }
        override fun setAppliedVoltage(volts: Double) {
            simFlywheelVoltage = volts.coerceIn(-12.0, 12.0)
        }
        override val velocityRpm: Double get() = flywheelSim.velocityRpm
        override val currentAmps: Double get() = flywheelSim.getCurrentAmps(simFlywheelVoltage)
        override val tempCelsius: Double get() = 30.0
    }

    val cowlIO: CowlIO = object : CowlIO {
        override fun setTargetAngle(degrees: Double) {
            val error = degrees - simCowlAngle
            simCowlVoltage = (error * 0.5).coerceIn(-12.0, 12.0)
        }
        override fun setAppliedVoltage(volts: Double) {
            simCowlVoltage = volts.coerceIn(-12.0, 12.0)
        }
        override val angleDegrees: Double get() = simCowlAngle
        override val currentAmps: Double get() = Math.abs(simCowlVoltage) * 0.2
    }

    val intakeIO: IntakeIO = object : IntakeIO {
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
        override val pivotAngleDegrees: Double get() = intakePivotSim.angleDegrees
        override val pivotCurrentAmps: Double get() = Math.abs(simIntakePivotVoltage) * 0.3
        override val rollerCurrentAmps: Double get() = Math.abs(simIntakeRollerVoltage) * 0.2
    }

    val feederIO: FeederIO = object : FeederIO {
        override fun setAppliedVoltage(volts: Double) {
            simFeederVoltage = volts.coerceIn(-12.0, 12.0)
        }
        override val isBeamBroken: Boolean get() = simFeederPieceDetected
        override val currentAmps: Double get() = Math.abs(simFeederVoltage) * 0.1
    }

    init {
        world.setGravity(Vector2(0.0, 0.0))

        // Robot body (~28 inch square)
        val robotFixture = robotBody.addFixture(Geometry.createRectangle(0.7, 0.7))
        robotFixture.density = 78.0
        robotBody.linearDamping = 1.0
        robotBody.angularDamping = 2.0
        robotBody.setMass(MassType.NORMAL)
        robotBody.translate(2.0, 2.0)
        world.addBody(robotBody)

        createWalls()
        spawnFuel(seed)
    }

    /**
     * Step the physics simulation forward by [dt] seconds using the current [state].
     * Returns a list of RobotActions to dispatch back to the Store (inventory changes, etc).
     */
    fun step(state: RobotState, dt: Double): List<RobotAction> {
        val actions = mutableListOf<RobotAction>()
        val timestamp = System.currentTimeMillis()

        if (dt <= 0.0) return actions

        // ── Drive Physics ──
        val kpLinear = 50.0
        val kpAngular = 20.0
        val forceX = (state.drive.xVelocityMetersPerSecond - robotBody.linearVelocity.x) * kpLinear
        val forceY = (state.drive.yVelocityMetersPerSecond - robotBody.linearVelocity.y) * kpLinear
        val torque = (state.drive.angularVelocityRadiansPerSecond - robotBody.angularVelocity) * kpAngular

        robotBody.isAtRest = false
        robotBody.applyForce(Vector2(forceX, forceY))
        robotBody.applyTorque(torque)

        world.step(1, dt)

        // ── Superstructure Physics ──
        flywheelSim.update(simFlywheelVoltage, dt)
        intakePivotSim.update(simIntakePivotVoltage, dt)

        // Flywheel visual rotation
        val flywheelRps = flywheelSim.velocityRpm / 60.0
        flywheelRotationAngle += (flywheelRps * 2.0 * Math.PI) * dt

        // Cowl position integration
        simCowlAngle += (simCowlVoltage * 15.0) * dt
        simCowlAngle = simCowlAngle.coerceIn(0.0, 70.0)

        // ── Game Piece Collision ──
        val t = robotBody.transform
        val robotX = t.translationX
        val robotY = t.translationY
        val robotHeading = t.rotationAngle

        val intakeDeployed = intakePivotSim.angleDegrees > 45.0
        val intakeSpinning = simIntakeRollerVoltage > 1.0

        if (intakeDeployed && intakeSpinning && state.superstructure.inventoryCount < 3) {
            val ballIterator = balls.iterator()
            while (ballIterator.hasNext()) {
                val ball = ballIterator.next()
                val bx = ball.transform.translationX
                val by = ball.transform.translationY
                val dist = Math.hypot(bx - robotX, by - robotY)
                if (dist < 0.5) {
                    world.removeBody(ball)
                    ballIterator.remove()
                    val newCount = state.superstructure.inventoryCount + 1
                    actions.add(RobotAction.SetInventoryCount(newCount, timestamp))
                    simFeederPieceDetected = true
                    println("BALL INGESTED! Inventory: $newCount")
                    break
                }
            }
        }

        // ── Shooting ──
        val flywheelAtSpeed = state.superstructure.isFlywheelAtSpeed
        val feederSpinning = simFeederVoltage > 2.0
        if (flywheelAtSpeed && feederSpinning && state.superstructure.inventoryCount > 0) {
            val newCount = state.superstructure.inventoryCount - 1
            actions.add(RobotAction.SetInventoryCount(newCount, timestamp))
            simFeederPieceDetected = newCount > 0

            val shootSpeed = 12.0
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

        return actions
    }

    /**
     * Returns a PoseUpdate action reflecting the physics body's current transform.
     */
    fun getPoseUpdate(): RobotAction.PoseUpdate {
        val t = robotBody.transform
        return RobotAction.PoseUpdate(
            xMeters = t.translationX,
            yMeters = t.translationY,
            headingRadians = t.rotationAngle,
            timestampMs = System.currentTimeMillis()
        )
    }

    /**
     * Publishes fuel/game piece 3D poses and mechanism 3D visualization to telemetry.
     */
    fun publishVisualization(state: RobotState, telemetry: ITelemetry) {
        val robotX = state.drive.odometryX
        val robotY = state.drive.odometryY
        val robotHeading = state.drive.odometryHeading

        val halfHeading = robotHeading / 2.0
        val robotQW = Math.cos(halfHeading)
        val robotQZ = Math.sin(halfHeading)

        // ── Intake 3D Pose ──
        val intakeAngleRad = Math.toRadians(intakePivotSim.angleDegrees)
        val halfIntake = intakeAngleRad / 2.0
        val intCosY = Math.cos(halfIntake)
        val intSinY = Math.sin(halfIntake)
        telemetry.putDoubleArray("Robot/Superstructure/3D/Intake", doubleArrayOf(
            robotX + 0.35 * Math.cos(robotHeading),
            robotY + 0.35 * Math.sin(robotHeading),
            0.2,
            robotQW * intCosY, -robotQZ * intSinY, robotQW * intSinY, robotQZ * intCosY
        ))

        // ── Cowl 3D Pose ──
        val cowlAngleRad = Math.toRadians(simCowlAngle)
        val halfCowl = cowlAngleRad / 2.0
        val cowlCosY = Math.cos(halfCowl)
        val cowlSinY = Math.sin(halfCowl)
        telemetry.putDoubleArray("Robot/Superstructure/3D/Cowl", doubleArrayOf(
            robotX - 0.2 * Math.cos(robotHeading),
            robotY - 0.2 * Math.sin(robotHeading),
            0.6,
            robotQW * cowlCosY, -robotQZ * cowlSinY, robotQW * cowlSinY, robotQZ * cowlCosY
        ))

        // ── Flywheel 3D Pose ──
        val halfFlywheel = flywheelRotationAngle / 2.0
        val flyCosY = Math.cos(halfFlywheel)
        val flySinY = Math.sin(halfFlywheel)
        telemetry.putDoubleArray("Robot/Superstructure/3D/Flywheel", doubleArrayOf(
            robotX - 0.1 * Math.cos(robotHeading),
            robotY - 0.1 * Math.sin(robotHeading),
            0.6,
            robotQW * flyCosY, -robotQZ * flySinY, robotQW * flySinY, robotQZ * flyCosY
        ))

        // ── Fuel 3D Poses ──
        val gamePieceData = DoubleArray(balls.size * 7)
        for (i in balls.indices) {
            gamePieceData[i * 7] = balls[i].transform.translationX
            gamePieceData[i * 7 + 1] = balls[i].transform.translationY
            gamePieceData[i * 7 + 2] = 0.0635
            val theta = balls[i].transform.rotationAngle
            gamePieceData[i * 7 + 3] = kotlin.math.cos(theta / 2.0)
            gamePieceData[i * 7 + 4] = 0.0
            gamePieceData[i * 7 + 5] = 0.0
            gamePieceData[i * 7 + 6] = kotlin.math.sin(theta / 2.0)
        }
        telemetry.putDoubleArray("Robot/FuelPoses", gamePieceData)
    }

    /**
     * Snap the physics body to match a given pose (used at auto init).
     */
    fun resetPose(x: Double, y: Double, heading: Double) {
        robotBody.transform.setTranslation(x, y)
        robotBody.transform.setRotation(heading)
        robotBody.linearVelocity.set(0.0, 0.0)
        robotBody.angularVelocity = 0.0
        robotBody.isAtRest = false
    }

    // ── Field Setup ──

    private fun createWalls() {
        val width = 16.541
        val height = 8.069

        addWall(width / 2.0, height, width, 0.1)   // Top
        addWall(width / 2.0, 0.0, width, 0.1)      // Bottom
        addWall(0.0, height / 2.0, 0.1, height)     // Left
        addWall(width, height / 2.0, 0.1, height)   // Right
        addWall(4.135, 4.0345, 1.1938, 1.1938)      // Blue Hub
        addWall(width - 4.135, 4.0345, 1.1938, 1.1938) // Red Hub
    }

    private fun addWall(x: Double, y: Double, w: Double, h: Double) {
        val wall = Body()
        wall.addFixture(Geometry.createRectangle(w, h))
        wall.setMass(MassType.INFINITE)
        wall.translate(x, y)
        world.addBody(wall)
    }

    private fun spawnFuel(seed: Long) {
        val random = java.util.Random(seed)
        var spawned = 0
        while (spawned < 100) {
            val x = 1.0 + random.nextDouble() * 14.0
            val y = 1.0 + random.nextDouble() * 6.0

            // Exclusion zones
            val dxBlue = x - 4.135; val dyBlue = y - 4.0345
            if (dxBlue * dxBlue + dyBlue * dyBlue < 0.9) continue
            val dxRed = x - 12.406; val dyRed = y - 4.0345
            if (dxRed * dxRed + dyRed * dyRed < 0.9) continue
            val dxRobot = x - 2.0; val dyRobot = y - 2.0
            if (dxRobot * dxRobot + dyRobot * dyRobot < 0.7) continue

            val ball = Body()
            val fixture = ball.addFixture(Geometry.createCircle(0.0635))
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
