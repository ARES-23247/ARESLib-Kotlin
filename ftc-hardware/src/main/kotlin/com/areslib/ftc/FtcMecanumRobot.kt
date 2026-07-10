package com.areslib.ftc

import com.qualcomm.robotcore.hardware.HardwareMap
import org.firstinspires.ftc.robotcore.external.Telemetry
import com.areslib.ftc.MecanumHardwareIO
import com.areslib.kinematics.MecanumKinematics
import com.areslib.kinematics.MecanumWheelSpeeds
import com.areslib.math.Pose2d
import com.areslib.math.Rotation2d
import com.areslib.math.ChassisSpeeds
import com.qualcomm.robotcore.hardware.DcMotor
import com.qualcomm.robotcore.hardware.DcMotorEx
import com.areslib.telemetry.logDriveMotor
import com.areslib.subsystem.DriveSubsystem
import com.areslib.subsystem.MecanumDriveFacade
import com.areslib.action.RobotAction
import com.areslib.control.VisionAlignController
import com.areslib.ftc.telemetry.LimelightProxyAutoStart

/**
 * Concrete Mecanum Drive robot facade.
 * Implements wheel kinematics conversions, battery sag motor compensation,
 * and publishes mecanum-specific motor currents and powers.
 */
class FtcMecanumRobot @kotlin.jvm.JvmOverloads constructor(
    hardwareMap: HardwareMap,
    flName: String = "fl",
    frName: String = "fr",
    blName: String = "bl",
    brName: String = "br",
    pinpointName: String? = "pinpoint",
    limelightName: String? = "limelight",
    localTelemetry: Telemetry? = null,
    flDirection: com.qualcomm.robotcore.hardware.DcMotorSimple.Direction = com.qualcomm.robotcore.hardware.DcMotorSimple.Direction.FORWARD,
    frDirection: com.qualcomm.robotcore.hardware.DcMotorSimple.Direction = com.qualcomm.robotcore.hardware.DcMotorSimple.Direction.REVERSE,
    blDirection: com.qualcomm.robotcore.hardware.DcMotorSimple.Direction = com.qualcomm.robotcore.hardware.DcMotorSimple.Direction.FORWARD,
    brDirection: com.qualcomm.robotcore.hardware.DcMotorSimple.Direction = com.qualcomm.robotcore.hardware.DcMotorSimple.Direction.REVERSE
) : FtcBaseRobot(hardwareMap, pinpointName, limelightName, localTelemetry) {

    // Subsystem Facades
    val drive = DriveSubsystem(store)
    val mecanumDrive = MecanumDriveFacade(store)

    private val visionAlignController = VisionAlignController()

    // 0. Superstructure Hardware (Optional)
    // Removed intake and shooter as they belong in TeamCode

    init {
        LimelightProxyAutoStart.start()
    }

    // 1. Physical Hardware IO & Kinematics Controllers
    val mecanumIO = MecanumHardwareIO(
        hardwareMap, flName, frName, blName, brName,
        flDirection = flDirection,
        frDirection = frDirection,
        blDirection = blDirection,
        brDirection = brDirection
    )

    init {
        // Core initializations
        drive.maxSpeedMps = mecanumIO.maxWheelSpeedMetersPerSecond
    }

    val sysIdManager = com.areslib.control.SysIdManager()
    private var lastCommandProcessed = ""

    private val kinematics = MecanumKinematics(trackWidthMeters = 0.45, wheelBaseMeters = 0.45)

    override fun updateHardwareInputs() {
        com.areslib.hardware.HardwareRegistry.refreshAll()
        
        val command = telemetryManager.nt4.getString("SysId/Command", "")
        if (command != lastCommandProcessed) {
            lastCommandProcessed = command
            when {
                command == "STOP" -> sysIdManager.stop()
                command.startsWith("START_") -> {
                    val parts = command.removePrefix("START_").split("_")
                    if (parts.size >= 2) {
                        val mechStr = parts[0]
                        val routineStr = command.removePrefix("START_${mechStr}_")
                        
                        val mechanism = try {
                            com.areslib.control.SysIdMechanism.valueOf(mechStr)
                        } catch (e: Exception) {
                            com.areslib.control.SysIdMechanism.LINEAR
                        }
                        
                        val routine = try {
                            com.areslib.control.SysIdRoutine.valueOf(routineStr)
                        } catch (e: Exception) {
                            com.areslib.control.SysIdRoutine.NONE
                        }
                        
                        val pose = store.state.drive.poseEstimator.estimatedPose
                        sysIdManager.start(
                            mechanism = mechanism,
                            routine = routine,
                            timestampMs = com.areslib.util.RobotClock.currentTimeMillis(),
                            x = pose.x,
                            y = pose.y,
                            heading = pose.heading.radians
                        )
                    }
                }
            }
        }
    }

    override fun updateSubsystems(dtSeconds: Double, batteryVoltage: Double, powerScale: Double) {
        val pose = store.state.drive.poseEstimator.estimatedPose
        val timestamp = com.areslib.util.RobotClock.currentTimeMillis()
        
        if (sysIdManager.isActive()) {
            if (!sysIdManager.checkSafety(pose.x, pose.y, pose.heading.radians, timestamp)) {
                sysIdManager.stop()
                mecanumIO.setMotorPowers(0.0, 0.0, 0.0, 0.0)
            } else {
                val velocity = if (sysIdManager.activeMechanism == com.areslib.control.SysIdMechanism.LINEAR) {
                    store.state.drive.xVelocityMetersPerSecond
                } else {
                    store.state.drive.angularVelocityRadiansPerSecond
                }
                
                val voltage = sysIdManager.update(timestamp, velocity)
                val power = (voltage / batteryVoltage).coerceIn(-1.0, 1.0)
                
                if (sysIdManager.activeMechanism == com.areslib.control.SysIdMechanism.LINEAR) {
                    mecanumIO.setMotorPowers(power, power, power, power)
                } else {
                    mecanumIO.setMotorPowers(-power, power, -power, power)
                }
            }
        } else {
            mecanumIO.drive(store.state.drive, kinematics, batteryVoltage, dtSeconds)
        }
    }

    override fun publishRobotTelemetry(timestamp: Long) {
        localTelemetry?.let { t ->
            t.addData("Motor Powers", String.format("FL:%.2f | FR:%.2f | RL:%.2f | RR:%.2f",
                mecanumIO.flIO.power * mecanumIO.flIO.powerScale,
                mecanumIO.frIO.power * mecanumIO.frIO.powerScale,
                mecanumIO.blIO.power * mecanumIO.blIO.powerScale,
                mecanumIO.brIO.power * mecanumIO.brIO.powerScale
            ))

            val currentStr = if (powerManager.floodgate != null) {
                String.format("%.1f A (Physical)", powerManager.floodgate.current)
            } else {
                String.format("%.1f A (Estimated)", powerManager.currentAmps)
            }
            t.addData("Current Draw", currentStr)
        }

        // Detailed hardware analytics logging
        telemetryManager.dataLoggingTelemetry.logDriveMotor("fl", mecanumIO.flIO)
        telemetryManager.dataLoggingTelemetry.logDriveMotor("fr", mecanumIO.frIO)
        telemetryManager.dataLoggingTelemetry.logDriveMotor("bl", mecanumIO.blIO)
        telemetryManager.dataLoggingTelemetry.logDriveMotor("br", mecanumIO.brIO)

        // SysId Telemetry Streaming
        val dataLogging = telemetryManager.dataLoggingTelemetry
        dataLogging.putString("SysId/Status", sysIdManager.activeRoutine.name)
        if (sysIdManager.isActive()) {
            val pose = store.state.drive.poseEstimator.estimatedPose
            val position = if (sysIdManager.activeMechanism == com.areslib.control.SysIdMechanism.LINEAR) {
                val dx = pose.x - sysIdManager.startX
                val dy = pose.y - sysIdManager.startY
                kotlin.math.sqrt(dx * dx + dy * dy)
            } else {
                sysIdManager.accumulatedHeadingChange
            }
            
            val velocity = if (sysIdManager.activeMechanism == com.areslib.control.SysIdMechanism.LINEAR) {
                store.state.drive.xVelocityMetersPerSecond
            } else {
                store.state.drive.angularVelocityRadiansPerSecond
            }
            
            dataLogging.putDoubleArray(
                "SysId/Data",
                doubleArrayOf(
                    timestamp.toDouble(),
                    sysIdManager.currentVoltage,
                    position,
                    velocity,
                    sysIdManager.calculatedAcceleration
                )
            )
        } else {
            dataLogging.putDoubleArray("SysId/Data", doubleArrayOf())
        }
    }

    override fun safeHardware() {
        com.areslib.hardware.HardwareRegistry.safeAll()
        stopAll()
    }

    /**
     * Beginner API: Standard drive method (defaults to field-centric)
     */
    fun drive(x: Double, y: Double, rotation: Double) {
        driveFieldCentric(x, y, rotation)
    }

    /**
     * Beginner API: Drive the robot field-centric
     */
    fun driveFieldCentric(x: Double, y: Double, rotation: Double) {
        store.dispatch(RobotAction.JoystickDriveIntent(x, y, rotation, isFieldCentric = true))
    }

    /**
     * Beginner API: Drive the robot from the robot's local frame of reference
     */
    fun driveRobotCentric(x: Double, y: Double, rotation: Double) {
        store.dispatch(RobotAction.JoystickDriveIntent(x, y, rotation, isFieldCentric = false))
    }

    /**
     * Beginner API: Auto-align the robot to a specific AprilTag ID
     */
    fun alignToTag(tagId: Int) {
        val intent = visionAlignController.calculate(store.state, tagId, true)
        if (intent != null) {
            store.dispatch(intent)
        }
    }

    private var activePathfindTask: com.areslib.fsm.PathfindToPoseTask? = null
    private var pathfindStartMs = 0L
    private val pathfindFollower by lazy { com.areslib.pathing.HolonomicPathFollower(drive) }
    private var wasPathfindRequested = false

    /**
     * Drives the robot to a target pose on the field, automatically generating
     * and following a path around static obstacles.
     */
    @kotlin.jvm.JvmOverloads
    fun driveToPose(targetPose: Pose2d, isRequested: Boolean, mirrorForAlliance: Boolean = true) {
        val now = com.areslib.util.RobotClock.currentTimeMillis()
        val task = activePathfindTask
        val elapsed = if (task != null) now - pathfindStartMs else 0L

        when {
            isRequested && !wasPathfindRequested -> {
                val config = com.areslib.state.RobotFieldManager.activeConfig
                val costmap = com.areslib.pathing.Costmap.fromFieldConfig(config)
                
                activePathfindTask = com.areslib.fsm.PathfindToPoseTask(
                    targetPose = targetPose,
                    follower = pathfindFollower,
                    costmap = costmap,
                    maxVelocityMps = mecanumIO.maxWheelSpeedMetersPerSecond * 0.85,
                    maxAccelerationMps2 = 3.0,
                    mirrorForAlliance = mirrorForAlliance
                )
                
                pathfindStartMs = now
                val initActions = activePathfindTask!!.initialize(store.state)
                initActions.forEach { store.dispatch(it) }
                wasPathfindRequested = true
            }
            isRequested && wasPathfindRequested && task != null && task.isCompleted(store.state, elapsed) -> {
                val endActions = task.end(store.state, interrupted = false)
                endActions.forEach { store.dispatch(it) }
                activePathfindTask = null
            }
            isRequested && wasPathfindRequested && task != null -> {
                val execActions = task.execute(store.state, elapsed)
                execActions.forEach { store.dispatch(it) }
            }
            !isRequested && wasPathfindRequested -> {
                if (task != null) {
                    val endActions = task.end(store.state, interrupted = true)
                    endActions.forEach { store.dispatch(it) }
                }
                pathfindFollower.stop()
                activePathfindTask = null
                wasPathfindRequested = false
            }
        }
    }

    /**
     * Drives the robot to a named field waypoint loaded from field_waypoints.json,
     * automatically pathfinding around static obstacles.
     */
    @kotlin.jvm.JvmOverloads
    fun driveToWaypoint(name: String, isRequested: Boolean, mirrorForAlliance: Boolean = true) {
        val wp = com.areslib.pathing.FieldWaypointLoader.getWaypoint(name)
        if (wp != null) {
            driveToPose(wp.toPose(), isRequested, mirrorForAlliance)
        } else {
            if (isRequested) {
                localTelemetry?.addData("Error", "Waypoint '$name' not found!")
            }
            driveToPose(Pose2d(0.0, 0.0, Rotation2d(0.0)), false, false)
        }
    }

    /**
     * Beginner API: Stop all mechanisms
     */
    fun stopAll() {
        com.areslib.hardware.HardwareRegistry.safeAll()
    }

    override fun close() {
        super.close()
        // Ensure proxy restarts for wireless tuning after the OpMode ends
        LimelightProxyAutoStart.start()
    }
}
