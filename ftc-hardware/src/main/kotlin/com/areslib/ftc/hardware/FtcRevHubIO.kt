package com.areslib.ftc.hardware

import com.areslib.hardware.actuator.MotorIO
import com.areslib.hardware.actuator.RevEncoderVersion
import com.areslib.hardware.actuator.ServoIO
import com.areslib.hardware.sensor.ImuIO
import com.areslib.hardware.HardwareRegistry
import com.areslib.math.geometry.Rotation2d
import com.qualcomm.robotcore.hardware.DcMotorEx
import com.qualcomm.robotcore.hardware.DcMotor
import com.qualcomm.robotcore.hardware.Servo
import com.qualcomm.robotcore.hardware.CRServo
import com.qualcomm.robotcore.hardware.AnalogInput
import com.qualcomm.robotcore.hardware.DigitalChannel
import com.qualcomm.robotcore.hardware.IMU
import com.qualcomm.hardware.rev.RevHubOrientationOnRobot
import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit

class FtcMotor(
    private val motor: DcMotorEx,
    val name: String? = null
) : MotorIO, AutoCloseable {
    private var encoderOffset = 0.0
    private var cachedPosition = 0.0
    private var cachedVelocity = 0.0
    private var cachedAmps = 0.0
    private var updateCount = 0
    private val currentLock = Any()

    init {
        try {
            motor.mode = DcMotor.RunMode.STOP_AND_RESET_ENCODER
            motor.mode = DcMotor.RunMode.RUN_WITHOUT_ENCODER
        } catch (_: Exception) {}

        if (name != null) {
            HardwareRegistry.registerMotor(name, this)
        }
        
        synchronized(FtcMotor) {
            motorsList.add(this)
            startPollingThreadIfNeeded()
        }
        HardwareRegistry.registerCloseable(this)
    }

    private var targetPower: Double = 0.0
    private var stallStartTimeMs = 0L
    private var isStalled = false
    private var lastSentPower = Double.NaN

    override var powerScale: Double = 1.0
        set(value) {
            field = value.coerceIn(0.0, 1.0)
            if (!isStalled) {
                try {
                    val commandPower = targetPower * field
                    if (lastSentPower.isNaN() || kotlin.math.abs(commandPower - lastSentPower) > 0.001) {
                        motor.power = commandPower
                        lastSentPower = commandPower
                    }
                } catch (_: Exception) {}
            }
        }

    override var power: Double
        get() = targetPower
        set(value) {
            targetPower = value
            val timeMs = com.areslib.util.RobotClock.currentTimeMillis()
            val currentVel = this.velocity

            // Automated stall detection: comparing encoder velocities against applied voltage
            if (kotlin.math.abs(value) > 0.5 && kotlin.math.abs(currentVel) < 10.0) {
                when {
                    stallStartTimeMs == 0L -> stallStartTimeMs = timeMs
                    timeMs - stallStartTimeMs > 500 -> isStalled = true
                }
            } else {
                stallStartTimeMs = 0L
                isStalled = false
            }

            // Current spike limit (9.2A FTC breaker threshold)
            val amps = this.currentAmps
            if (amps > 9.2) {
                isStalled = true // Trip virtual breaker
            }

            try {
                val commandPower = if (isStalled) 0.0 else value * powerScale
                if (lastSentPower.isNaN() || kotlin.math.abs(commandPower - lastSentPower) > 0.001) {
                    motor.power = commandPower
                    lastSentPower = commandPower
                }
            } catch (_: Exception) {}
        }

    fun updateInputs() {
        try {
            cachedPosition = motor.currentPosition.toDouble() - encoderOffset
        } catch (_: Exception) {}
        try {
            cachedVelocity = motor.velocity
        } catch (_: Exception) {}
        if (motor.javaClass.simpleName.contains("Mock")) {
            pollCurrentSync()
        }
    }

    fun pollCurrentSync() {
        try {
            val amps = motor.getCurrent(org.firstinspires.ftc.robotcore.external.navigation.CurrentUnit.AMPS)
            synchronized(currentLock) {
                cachedAmps = amps
            }
        } catch (_: Exception) {}
    }

    override val velocity: Double
        get() = cachedVelocity

    override val position: Double
        get() = cachedPosition

    override val currentAmps: Double
        get() = synchronized(currentLock) { cachedAmps }

    override fun resetEncoder() {
        try {
            encoderOffset = motor.currentPosition.toDouble()
            cachedPosition = 0.0
        } catch (_: Exception) {}
    }

    override fun close() {
        synchronized(FtcMotor) {
            motorsList.remove(this)
        }
    }

    companion object {
        private val motorsList = java.util.concurrent.CopyOnWriteArrayList<FtcMotor>()
        @Volatile private var pollingRunning = false
        private var pollingThread: Thread? = null

        private fun startPollingThreadIfNeeded() {
            if (pollingRunning) return
            pollingRunning = true
            pollingThread = Thread {
                while (pollingRunning) {
                    for (motorInstance in motorsList) {
                        motorInstance.pollCurrentSync()
                    }
                    try {
                        Thread.sleep(50) // 20Hz
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                }
            }.apply {
                isDaemon = true
                name = "ARES-MotorCurrent-Thread"
                start()
            }
        }

        fun unregisterAll() {
            synchronized(this) {
                pollingRunning = false
                pollingThread?.interrupt()
                pollingThread = null
                motorsList.clear()
            }
        }
    }
}

/**
 * Wraps a Continuous Rotation Servo (CRServo), which behaves similarly to a DC motor
 * but lacks built-in encoder feedback. Can optionally be paired with an external
 * MotorIO representation of an encoder for closed-loop control.
 */
class FtcCRServo(
    private val crServo: CRServo,
    private val externalEncoder: MotorIO? = null,
    val name: String? = null
) : MotorIO {
    init {
        if (name != null) {
            HardwareRegistry.registerMotor(name, this)
        }
    }
    private var targetPower: Double = 0.0
    private var lastSentPower = Double.NaN

    override var powerScale: Double = 1.0
        set(value) {
            field = value.coerceIn(0.0, 1.0)
            try {
                val commandPower = targetPower * field
                if (lastSentPower.isNaN() || kotlin.math.abs(commandPower - lastSentPower) > 0.001) {
                    crServo.power = commandPower
                    lastSentPower = commandPower
                }
            } catch (_: Exception) {}
        }

    override var power: Double
        get() = targetPower
        set(value) {
            targetPower = value
            try {
                val commandPower = value * powerScale
                if (lastSentPower.isNaN() || kotlin.math.abs(commandPower - lastSentPower) > 0.001) {
                    crServo.power = commandPower
                    lastSentPower = commandPower
                }
            } catch (_: Exception) {}
        }

    override val velocity: Double
        get() = externalEncoder?.velocity ?: 0.0

    override val position: Double
        get() = externalEncoder?.position ?: 0.0

    override fun resetEncoder() {
        externalEncoder?.resetEncoder()
    }
}

/**
 * A read-only representation of an encoder plugged into a standard motor port.
 * Power assignments are ignored.
 */
class FtcEncoder(
    private val motor: DcMotorEx,
    val name: String? = null
) : MotorIO {
    private var encoderOffset = 0.0
    private var cachedPosition = 0.0
    private var cachedVelocity = 0.0

    init {
        if (name != null) {
            HardwareRegistry.registerMotor(name, this)
        }
    }

    override var power: Double
        get() = 0.0
        @Suppress("UNUSED_PARAMETER")
        set(value) {}

    fun updateInputs() {
        try {
            cachedPosition = motor.currentPosition.toDouble() - encoderOffset
        } catch (_: Exception) {}
        try {
            cachedVelocity = motor.velocity
        } catch (_: Exception) {}
    }

    override val velocity: Double
        get() = cachedVelocity

    override val position: Double
        get() = cachedPosition

    override fun resetEncoder() {
        try {
            encoderOffset = motor.currentPosition.toDouble()
            cachedPosition = 0.0
        } catch (_: Exception) {}
    }
}

/**
 * A composite wrapper that routes power commands to one device (e.g. a CRServo or un-encoded motor)
 * and reads position/velocity from another device (e.g. an FtcEncoder, OctoQuadEncoder, etc.).
 */
class CompositeMotorIO(
    private val actuator: MotorIO,
    private val sensor: MotorIO
) : MotorIO {
    override var power: Double
        get() = actuator.power
        set(value) {
            actuator.power = value
        }

    override val velocity: Double
        get() = sensor.velocity

    override val position: Double
        get() = sensor.position

    override val currentAmps: Double
        get() = actuator.currentAmps

    override fun resetEncoder() {
        sensor.resetEncoder()
    }
}

/**
 * Reads an absolute encoder plugged into a standard analog port (e.g. REV Through Bore).
 */
class FtcAbsoluteAnalogEncoder @kotlin.jvm.JvmOverloads constructor(
    private val analogInput: AnalogInput,
    private val version: com.areslib.hardware.actuator.RevEncoderVersion = com.areslib.hardware.actuator.RevEncoderVersion.V1,
    private val ticksPerRev: Double = 8192.0,
    val name: String? = null
) : MotorIO, AutoCloseable {
    private var offset = 0.0
    private var cachedPosition = 0.0
    private val lock = Any()
    private var running = true
    private var latestVoltage = 0.0

    private val thread = Thread {
        while (running) {
            try {
                val volt = analogInput.voltage
                synchronized(lock) {
                    latestVoltage = volt
                }
            } catch (_: Exception) {}
            try {
                Thread.sleep(5)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
        }
    }.apply {
        isDaemon = true
        name = "ARES-AnalogEncoder-Thread-${name ?: "unnamed"}"
    }

    init {
        if (name != null) {
            HardwareRegistry.registerMotor(name, this)
        }
        HardwareRegistry.registerCloseable(this)
        thread.start()
    }

    override var power: Double
        get() = 0.0
        @Suppress("UNUSED_PARAMETER")
        set(value) {}

    fun updateInputs() {
        try {
            val volt = synchronized(lock) { latestVoltage }
            val normalized = volt / version.maxVoltage
            cachedPosition = (normalized * ticksPerRev) - offset
        } catch (_: Exception) {}
    }

    override val velocity: Double
        get() = 0.0 // Velocity estimation from absolute analog usually requires a filter

    override val position: Double
        get() = cachedPosition

    override fun resetEncoder() {
        try {
            val volt = synchronized(lock) { latestVoltage }
            offset = (volt / version.maxVoltage) * ticksPerRev
            cachedPosition = 0.0
        } catch (_: Exception) {}
    }

    override fun close() {
        running = false
        thread.interrupt()
    }
}

class FtcServo(
    private val servo: Servo,
    val name: String? = null
) : ServoIO {
    private var lastSentPosition = Double.NaN

    init {
        if (name != null) {
            HardwareRegistry.registerServo(name, this)
        }
    }

    override var position: Double
        get() = try {
            servo.position
        } catch (_: Exception) {
            0.0
        }
        set(value) {
            try {
                if (lastSentPosition.isNaN() || kotlin.math.abs(value - lastSentPosition) > 0.001) {
                    servo.position = value
                    lastSentPosition = value
                }
            } catch (_: Exception) {}
        }
}

class FtcImu(private val imu: IMU) : ImuIO, AutoCloseable {
    private var headingOffset = 0.0
    private val lock = Any()

    private var latestYaw = 0.0
    private var latestPitch = 0.0
    private var latestRoll = 0.0
    private var latestYawVel = 0.0
    private var latestTimestamp = 0L
    @Volatile private var running = true

    private val imuThread = Thread {
        while (running) {
            try {
                val yawPitchRoll = imu.getRobotYawPitchRollAngles()
                val angularVel = imu.getRobotAngularVelocity(AngleUnit.RADIANS)
                synchronized(lock) {
                    latestYaw = yawPitchRoll.getYaw(AngleUnit.RADIANS)
                    latestPitch = yawPitchRoll.getPitch(AngleUnit.RADIANS)
                    latestRoll = yawPitchRoll.getRoll(AngleUnit.RADIANS)
                    latestYawVel = angularVel.getZRotationRate(AngleUnit.RADIANS).toDouble()
                    latestTimestamp = com.areslib.util.RobotClock.currentTimeMillis()
                }
            } catch (_: Exception) {}
            
            try {
                Thread.sleep(5) // Poll at ~200Hz
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
        }
    }.apply {
        isDaemon = true
        priority = Thread.NORM_PRIORITY
        name = "ARES-Asynchronous-IMU-Thread"
    }

    init {
        try {
            val orientation = RevHubOrientationOnRobot(
                RevHubOrientationOnRobot.LogoFacingDirection.UP,
                RevHubOrientationOnRobot.UsbFacingDirection.FORWARD
            )
            val parameters = IMU.Parameters(orientation)
            imu.initialize(parameters)
        } catch (_: Exception) {}

        // Populate initial values synchronously to prevent race conditions during startup/tests
        try {
            val yawPitchRoll = imu.getRobotYawPitchRollAngles()
            val angularVel = imu.getRobotAngularVelocity(AngleUnit.RADIANS)
            synchronized(lock) {
                latestYaw = yawPitchRoll.getYaw(AngleUnit.RADIANS)
                latestPitch = yawPitchRoll.getPitch(AngleUnit.RADIANS)
                latestRoll = yawPitchRoll.getRoll(AngleUnit.RADIANS)
                latestYawVel = angularVel.getZRotationRate(AngleUnit.RADIANS).toDouble()
                latestTimestamp = com.areslib.util.RobotClock.currentTimeMillis()
            }
        } catch (_: Exception) {}

        HardwareRegistry.registerCloseable(this)
        imuThread.start()
    }

    override fun updateInputs(inputs: com.areslib.hardware.sensor.ImuInputs) {
        synchronized(lock) {
            inputs.headingRadians = latestYaw - headingOffset
            inputs.pitchRadians = latestPitch
            inputs.rollRadians = latestRoll
            inputs.yawVelocityRadPerSec = latestYawVel
            inputs.timestampMs = latestTimestamp
        }
    }

    override fun resetHeading() {
        synchronized(lock) {
            headingOffset = latestYaw
        }
    }

    override fun close() {
        running = false
        imuThread.interrupt()
    }
}


class FtcAnalogSensor(private val analogInput: AnalogInput) : AutoCloseable {
    private val lock = Any()
    private var running = true
    private var latestVoltage = 0.0

    private val thread = Thread {
        while (running) {
            try {
                val volt = analogInput.voltage
                synchronized(lock) {
                    latestVoltage = volt
                }
            } catch (_: Exception) {}
            try {
                Thread.sleep(20)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
        }
    }.apply {
        isDaemon = true
        name = "ARES-AnalogSensor-Thread"
    }

    init {
        HardwareRegistry.registerCloseable(this)
        thread.start()
    }

    fun getVoltage(): Double {
        return synchronized(lock) { latestVoltage }
    }

    override fun close() {
        running = false
        thread.interrupt()
    }
}

class FtcDigitalSensor(private val digitalChannel: DigitalChannel) : AutoCloseable {
    private val lock = Any()
    private var running = true
    private var latestState = false

    private val thread = Thread {
        while (running) {
            try {
                val state = digitalChannel.state
                synchronized(lock) {
                    latestState = state
                }
            } catch (_: Exception) {}
            try {
                Thread.sleep(20)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
        }
    }.apply {
        isDaemon = true
        name = "ARES-DigitalSensor-Thread"
    }

    init {
        try {
            digitalChannel.mode = DigitalChannel.Mode.INPUT
        } catch (_: Exception) {}

        HardwareRegistry.registerCloseable(this)
        thread.start()
    }

    fun getState(): Boolean {
        return synchronized(lock) { latestState }
    }

    override fun close() {
        running = false
        thread.interrupt()
    }
}


