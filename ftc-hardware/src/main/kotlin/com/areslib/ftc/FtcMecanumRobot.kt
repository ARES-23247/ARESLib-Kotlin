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
import com.areslib.telemetry.GamepadState

import com.areslib.telemetry.NT4Telemetry
import com.areslib.telemetry.DataLoggingTelemetry
import com.areslib.telemetry.ARESNetworkStatePublisher

class FtcMecanumRobot @kotlin.jvm.JvmOverloads constructor(
    val hardwareMap: HardwareMap,
    flName: String = "fl",
    frName: String = "fr",
    blName: String = "bl",
    brName: String = "br",
    pinpointName: String = "pinpoint",
    limelightName: String = "limelight",
    private val localTelemetry: Any? = null
) : AresRobot() {

    // Telemetry & Network Tables State Publisher
    private val nt4 = NT4Telemetry()
    private val dataLoggingTelemetry = DataLoggingTelemetry(nt4)
    private val publisher = ARESNetworkStatePublisher(dataLoggingTelemetry)

    // 1. Physical Hardware IO & Kinematics Controllers
    private val mecanumIO = MecanumHardwareIO(hardwareMap, flName, frName, blName, brName)
    private val pinpointDriver = hardwareMap.get(GoBildaPinpointDriver::class.java, pinpointName)
    private val pinpointIO = PinpointIO(pinpointDriver)
    
    private val limelightDriver = hardwareMap.get(Limelight3A::class.java, limelightName)
    private val limelightIO = FtcLimelightIO(limelightDriver)
    private val visionInputs = VisionIOInputs()
    
    private val kinematics = MecanumKinematics(trackWidthMeters = 0.45, wheelBaseMeters = 0.45)

    /**
     * Coordinated frame update for Mecanum Drivetrain.
     *
     * Pass gamepad states to get automatic input logging for replay.
     * If omitted, everything else is still logged — gamepads are optional.
     *
     * @param gamepad1 Optional driver gamepad state (use `gamepad1.toState()`)
     * @param gamepad2 Optional operator gamepad state (use `gamepad2.toState()`)
     */
    fun update(gamepad1: GamepadState? = null, gamepad2: GamepadState? = null) {
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

        // 5. Publish EVERYTHING to NT4 + CSV automatically
        publisher.publish(store.state, gamepad1, gamepad2)

        // 6. Driver Station local telemetry (human-readable summary)
        if (localTelemetry != null) {
            try {
                val addDataMethod = localTelemetry.javaClass.getMethod("addData", String::class.java, Any::class.java)
                val updateMethod = localTelemetry.javaClass.getMethod("update")

                addDataMethod.invoke(localTelemetry, "Mode", store.state.superstructure.mode.name)
                addDataMethod.invoke(localTelemetry, "Flywheel", "${store.state.superstructure.flywheelRPM.toInt()} RPM")
                addDataMethod.invoke(localTelemetry, "Intake", if (store.state.superstructure.intakeActive) "DEPLOYED" else "RETRACTED")
                addDataMethod.invoke(localTelemetry, "Pose", String.format("(%.2f, %.2f) %.1f°",
                    store.state.drive.poseEstimator.estimatedPose.x,
                    store.state.drive.poseEstimator.estimatedPose.y,
                    Math.toDegrees(store.state.drive.poseEstimator.estimatedPose.heading.radians)
                ))

                updateMethod.invoke(localTelemetry)
            } catch (_: Exception) {
                // Ignore reflection errors in test/mock environments
            }
        }
    }

    /**
     * Gracefully cleans up background logging threads and closes network telemetry.
     */
    fun close() {
        dataLoggingTelemetry.close()
    }
}
