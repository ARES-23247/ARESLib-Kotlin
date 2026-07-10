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

    private val kinematics = MecanumKinematics(trackWidthMeters = 0.45, wheelBaseMeters = 0.45)

    override fun updateHardwareInputs() {
        com.areslib.hardware.HardwareRegistry.refreshAll()
    }

    override fun updateSubsystems(dtSeconds: Double, batteryVoltage: Double, powerScale: Double) {
        mecanumIO.drive(store.state.drive, kinematics, batteryVoltage, dtSeconds)
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
    fun driveToPose(targetPose: Pose2d, isRequested: Boolean) {
        val now = com.areslib.util.RobotClock.currentTimeMillis()
        
        if (isRequested) {
            if (!wasPathfindRequested) {
                val config = com.areslib.state.RobotFieldManager.activeConfig
                val costmap = com.areslib.pathing.Costmap.fromFieldConfig(config)
                
                activePathfindTask = com.areslib.fsm.PathfindToPoseTask(
                    targetPose = targetPose,
                    follower = pathfindFollower,
                    costmap = costmap,
                    maxVelocityMps = mecanumIO.maxWheelSpeedMetersPerSecond * 0.6,
                    maxAccelerationMps2 = 1.5
                )
                
                pathfindStartMs = now
                val initActions = activePathfindTask!!.initialize(store.state)
                initActions.forEach { store.dispatch(it) }
                wasPathfindRequested = true
            } else {
                val task = activePathfindTask
                if (task != null) {
                    val elapsed = now - pathfindStartMs
                    if (task.isCompleted(store.state, elapsed)) {
                        val endActions = task.end(store.state, interrupted = false)
                        endActions.forEach { store.dispatch(it) }
                        activePathfindTask = null
                    } else {
                        val execActions = task.execute(store.state, elapsed)
                        execActions.forEach { store.dispatch(it) }
                    }
                }
            }
        } else {
            if (wasPathfindRequested) {
                val task = activePathfindTask
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
    fun driveToWaypoint(name: String, isRequested: Boolean) {
        val wp = com.areslib.pathing.FieldWaypointLoader.getWaypoint(name)
        if (wp != null) {
            driveToPose(wp.toPose(), isRequested)
        } else {
            if (isRequested) {
                localTelemetry?.addData("Error", "Waypoint '$name' not found!")
            }
            driveToPose(Pose2d(0.0, 0.0, Rotation2d(0.0)), false)
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
