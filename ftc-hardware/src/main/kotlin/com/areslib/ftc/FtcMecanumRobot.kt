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
import com.areslib.control.BrownoutGuard
import com.areslib.control.CurrentBudgetManager
import com.areslib.ftc.hardware.FtcFloodgateCurrentSensor
import com.qualcomm.robotcore.hardware.AnalogInput

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
    val pinpointIO = PinpointIO(pinpointDriver)
    
    private val limelightIO: FtcLimelightIO? = try {
        val limelightDriver = hardwareMap.get(Limelight3A::class.java, limelightName)
        FtcLimelightIO(limelightDriver)
    } catch (_: Exception) {
        null
    }
    private val visionInputs = VisionIOInputs()
    
    private var lastUpdateTime = 0L
    private var lastVoltageReadTime = 0L
    private var cachedBatteryVoltage = 12.0
    
    private val kinematics = MecanumKinematics(trackWidthMeters = 0.45, wheelBaseMeters = 0.45)

    /** Brownout protection guard — auto-scales motor power on voltage sag */
    val brownoutGuard = BrownoutGuard.ftcDefaults()

    /** Floodgate V2 current sensor — null if no Floodgate is connected */
    val floodgate: FtcFloodgateCurrentSensor? = try {
        val analogInput = hardwareMap.get(AnalogInput::class.java, "floodgate")
        FtcFloodgateCurrentSensor(analogInput)
    } catch (_: Exception) {
        null // No Floodgate connected — current protection fallback active
    }

    /** Software current budget manager — used as a fallback if no Floodgate sensor is found */
    val currentBudgetManager: CurrentBudgetManager? = if (floodgate == null) {
        CurrentBudgetManager.ftcDefaults().apply {
            register(mecanumIO.flIO)
            register(mecanumIO.frIO)
            register(mecanumIO.blIO)
            register(mecanumIO.brIO)
        }
    } else {
        null
    }

    /**
     * Coordinated frame update for Mecanum Drivetrain.
     *
     * Pass gamepad states to get automatic input logging for replay.
     * If omitted, everything else is still logged — gamepads are optional.
     *
     * @param gamepad1 Optional driver gamepad state (use `gamepad1.toState()`)
     * @param gamepad2 Optional operator gamepad state (use `gamepad2.toState()`)
     */
    fun update(gamepad1: com.areslib.telemetry.GamepadState? = null, gamepad2: com.areslib.telemetry.GamepadState? = null) {
        val timestamp = com.areslib.util.RobotClock.currentTimeMillis()
        val dtSeconds = if (lastUpdateTime == 0L) 0.02 else (timestamp - lastUpdateTime) / 1000.0
        lastUpdateTime = timestamp

        // 1. Read pinpoint sensors and update the EKF state store
        val poseUpdate = pinpointIO.getPoseUpdate()
        store.dispatch(poseUpdate)

        // 2. Update visual AprilTag observations
        limelightIO?.let { io ->
            io.updateInputs(visionInputs)
            if (visionInputs.measurements.isNotEmpty()) {
                store.dispatch(RobotAction.VisionMeasurementsReceived(
                    visionInputs.measurements,
                    timestamp,
                    null
                ))
            }
        }

        // 3. Process kinematics using current State targets
        val maxSpeed = 2.0
        val vx = store.state.drive.xVelocityMetersPerSecond * maxSpeed
        val vy = store.state.drive.yVelocityMetersPerSecond * maxSpeed
        val omega = store.state.drive.angularVelocityRadiansPerSecond * maxSpeed
        
        val robotHeading = store.state.drive.poseEstimator.estimatedPose.heading
        val chassisSpeeds = ChassisSpeeds.fromFieldRelativeSpeeds(vx, vy, omega, robotHeading)
        val wheelSpeeds = kinematics.toWheelSpeeds(chassisSpeeds)

        // 4. Fetch voltage sensor for sag compensation (rate-limited to 10Hz/100ms to eliminate blocking JNI overhead)
        if (timestamp - lastVoltageReadTime > 100 || lastVoltageReadTime == 0L) {
            lastVoltageReadTime = timestamp
            val voltageSensors = hardwareMap.getAll(com.qualcomm.robotcore.hardware.VoltageSensor::class.java)
            cachedBatteryVoltage = if (voltageSensors.isNotEmpty()) {
                voltageSensors[0].voltage
            } else {
                12.0
            }
        }
        val batteryVoltage = cachedBatteryVoltage

        // Apply battery-compensated voltage vectors
        mecanumIO.apply(wheelSpeeds.normalize(1.0), batteryVoltage, dtSeconds)

        // 4b. Brownout protection — graduated power scaling on voltage sag
        brownoutGuard.update(batteryVoltage)
        var effectiveScale = brownoutGuard.powerScale

        // 4c. Floodgate current protection — throttle on overload (or software fallback)
        floodgate?.let { fg ->
            fg.update()
            if (fg.isOverloadWarning()) {
                // Graduated: scale inversely with fuse thermal load
                val fuseScale = (1.0 - fg.fuseThermalLoadPercent / 100.0).coerceIn(0.2, 1.0)
                effectiveScale = minOf(effectiveScale, fuseScale)
            }
        } ?: currentBudgetManager?.let { cbm ->
            cbm.update(batteryVoltage, enableCalibration = true)
            effectiveScale = minOf(effectiveScale, cbm.powerScale)
        }

        mecanumIO.applyPowerScale(effectiveScale)

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
