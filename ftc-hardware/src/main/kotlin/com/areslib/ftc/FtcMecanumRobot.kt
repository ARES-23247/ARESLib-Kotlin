package com.areslib.ftc

import com.qualcomm.robotcore.hardware.HardwareMap
import com.qualcomm.hardware.gobilda.GoBildaPinpointDriver
import com.qualcomm.hardware.limelightvision.Limelight3A
import com.areslib.subsystem.AresRobot
import com.areslib.hardware.ftc.vision.FtcLimelightIO
import com.areslib.hardware.vision.VisionIOInputs
import com.areslib.kinematics.MecanumKinematics
import com.areslib.math.ChassisSpeeds
import com.areslib.action.RobotAction

class FtcMecanumRobot @kotlin.jvm.JvmOverloads constructor(
    val hardwareMap: HardwareMap,
    flName: String = "fl",
    frName: String = "fr",
    blName: String = "bl",
    brName: String = "br",
    pinpointName: String = "pinpoint",
    limelightName: String = "limelight"
) : AresRobot() {

    // 1. Physical Hardware IO & Kinematics Controllers
    private val mecanumIO = MecanumHardwareIO(hardwareMap, flName, frName, blName, brName)
    private val pinpointDriver = hardwareMap.get(GoBildaPinpointDriver::class.java, pinpointName)
    private val pinpointIO = PinpointIO(pinpointDriver)
    
    private val limelightDriver = hardwareMap.get(Limelight3A::class.java, limelightName)
    private val limelightIO = FtcLimelightIO(limelightDriver)
    private val visionInputs = VisionIOInputs()
    
    private val kinematics = MecanumKinematics(trackWidthMeters = 0.45, wheelBaseMeters = 0.45)

    /**
     * Coordinated frame update for Mecanum Drivetrain:
     * 1. Poll physical sensors (Pinpoint, Limelight).
     * 2. Feed updates into EKF odometry & vision fusion store.
     * 3. Fetch target joystick velocities, convert to wheel velocities via kinematics, and apply battery voltage scaling.
     */
    fun update() {
        val timestamp = System.currentTimeMillis()

        // 1. Read pinpoint sensors and update the EKF state store
        val poseUpdate = pinpointIO.getPoseUpdate()
        store.dispatch(poseUpdate)

        // 2. Update visual AprilTag observations
        limelightIO.updateInputs(visionInputs)
        if (visionInputs.measurements.isNotEmpty()) {
            store.dispatch(RobotAction.VisionMeasurementsReceived(
                visionInputs.measurements,
                timestamp,
                null
            ))
        }

        // 3. Process kinematics using current State targets
        val maxSpeed = 2.0
        val vx = store.state.drive.xVelocityMetersPerSecond * maxSpeed
        val vy = store.state.drive.yVelocityMetersPerSecond * maxSpeed
        val omega = store.state.drive.angularVelocityRadiansPerSecond * maxSpeed
        
        val robotHeading = store.state.drive.poseEstimator.estimatedPose.heading
        val chassisSpeeds = ChassisSpeeds.fromFieldRelativeSpeeds(vx, vy, omega, robotHeading)
        val wheelSpeeds = kinematics.toWheelSpeeds(chassisSpeeds)

        // 4. Fetch voltage sensor for sag compensation
        val voltageSensors = hardwareMap.getAll(com.qualcomm.robotcore.hardware.VoltageSensor::class.java)
        val batteryVoltage = if (voltageSensors.isNotEmpty()) {
            voltageSensors[0].voltage
        } else {
            12.0
        }

        // Apply battery-compensated voltage vectors
        mecanumIO.apply(wheelSpeeds.normalize(1.0), batteryVoltage)
    }
}
