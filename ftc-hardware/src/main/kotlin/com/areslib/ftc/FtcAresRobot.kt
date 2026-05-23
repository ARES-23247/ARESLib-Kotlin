package com.areslib.ftc

import com.qualcomm.robotcore.hardware.HardwareMap
import com.qualcomm.robotcore.hardware.DcMotorEx
import com.qualcomm.robotcore.hardware.IMU
import com.areslib.subsystem.AresRobot
import com.areslib.ftc.hardware.FtcMotor
import com.areslib.ftc.hardware.FtcImu
import com.areslib.hardware.ImuInputs
import com.areslib.action.RobotAction
import com.areslib.control.BrownoutGuard
import com.areslib.control.CurrentBudgetManager
import com.areslib.ftc.hardware.FtcFloodgateCurrentSensor
import com.qualcomm.robotcore.hardware.AnalogInput

class FtcAresRobot(private val hardwareMap: HardwareMap) : AresRobot() {
    
    // 1. Concrete FTC Hardware wrappers
    val motor = FtcMotor(hardwareMap.get(DcMotorEx::class.java, "revMotor"))
    val imu = FtcImu(hardwareMap.get(IMU::class.java, "imu"))
    
    private val imuInputs = ImuInputs()
    private var lastVoltageReadTime = 0L
    private var cachedBatteryVoltage = 12.0

    /** Brownout protection guard — auto-scales motor power on voltage sag */
    val brownoutGuard = BrownoutGuard.ftcDefaults()

    /** Floodgate V2 current sensor — null if no Floodgate is connected */
    val floodgate: FtcFloodgateCurrentSensor? = try {
        val analogInput = hardwareMap.get(AnalogInput::class.java, "floodgate")
        FtcFloodgateCurrentSensor(analogInput)
    } catch (_: Exception) {
        null
    }

    /** Software current budget manager — used as a fallback if no Floodgate sensor is found */
    val currentBudgetManager: CurrentBudgetManager? = if (floodgate == null) {
        CurrentBudgetManager.ftcDefaults().apply {
            register(motor)
        }
    } else {
        null
    }

    /**
     * Coordinated update frame:
     * 1. Read hardware sensors (IMU, Encoders).
     * 2. Dispatch hardware update actions to the central Redux store.
     * 3. Run the math controllers and apply calculated voltages to motor outputs.
     * 4. Perform background telemetry logging (student does not write any logging boilerplate).
     */
    fun update() {
        val timestamp = com.areslib.util.RobotClock.currentTimeMillis()

        // 1. Read hardware inputs
        imu.updateInputs(imuInputs)

        // 2. Dispatch to the underlying Redux store
        store.dispatch(RobotAction.DriveHardwareUpdate(
            xVelocity = imuInputs.yawVelocityRadPerSec,
            yVelocity = 0.0,
            angularVelocity = imuInputs.yawVelocityRadPerSec,
            deltaX = 0.0,
            deltaY = 0.0,
            deltaHeading = 0.0,
            timestampMs = timestamp,
            pitchDegrees = Math.toDegrees(imuInputs.pitchRadians),
            rollDegrees = Math.toDegrees(imuInputs.rollRadians)
        ))

        store.dispatch(RobotAction.SuperstructureSensorUpdate(
            flywheelRpm = 0.0,
            cowlAngle = 0.0,
            intakeAngle = 0.0,
            pieceDetected = false,
            timestampMs = timestamp
        ))

        // 3. Write outputs to motors based on computed Redux state
        // (Use odometry X target voltage compensation as simple loop feedback)
        val targetPower = store.state.drive.odometryX * 0.1

        // 3b. Read battery voltage and apply brownout protection (rate-limited to 10Hz/100ms to eliminate blocking JNI overhead)
        if (timestamp - lastVoltageReadTime > 100 || lastVoltageReadTime == 0L) {
            lastVoltageReadTime = timestamp
            val voltageSensors = hardwareMap.getAll(com.qualcomm.robotcore.hardware.VoltageSensor::class.java)
            cachedBatteryVoltage = if (voltageSensors.isNotEmpty()) voltageSensors[0].voltage else 12.0
        }
        val batteryVoltage = cachedBatteryVoltage
        brownoutGuard.update(batteryVoltage)
        var effectiveScale = brownoutGuard.powerScale

        // 3c. Floodgate current protection (or software fallback)
        floodgate?.let { fg ->
            fg.update()
            if (fg.isOverloadWarning()) {
                val fuseScale = (1.0 - fg.fuseThermalLoadPercent / 100.0).coerceIn(0.2, 1.0)
                effectiveScale = minOf(effectiveScale, fuseScale)
            }
        } ?: currentBudgetManager?.let { cbm ->
            cbm.update(batteryVoltage, enableCalibration = true)
            effectiveScale = minOf(effectiveScale, cbm.powerScale)
        }

        motor.powerScale = effectiveScale
        motor.power = targetPower
    }
}
