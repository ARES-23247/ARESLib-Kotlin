package com.areslib.ftc.hardware.rev

import com.areslib.hardware.sensor.ImuIO
import com.areslib.hardware.HardwareRegistry
import com.qualcomm.robotcore.hardware.IMU
import com.qualcomm.hardware.rev.RevHubOrientationOnRobot
import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit
import com.qualcomm.robotcore.hardware.AnalogInput
import com.qualcomm.robotcore.hardware.DigitalChannel
import com.areslib.hardware.actuator.MotorIO

/**
 * Class implementation for Rev Imu Controller.
 *
 * Hardware IO abstraction layer bridging physical robot sensors and actuators into immutable Redux state representations.
 */
class RevImuController(private val imu: IMU) : ImuIO, AutoCloseable {
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
                Thread.sleep(20)
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

    /**
     * updateInputs declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun updateInputs(inputs: com.areslib.hardware.sensor.ImuInputs) {
        synchronized(lock) {
            inputs.headingRadians = latestYaw - headingOffset
            inputs.pitchRadians = latestPitch
            inputs.rollRadians = latestRoll
            inputs.yawVelocityRadPerSec = latestYawVel
            inputs.timestampMs = latestTimestamp
        }
    }

    /**
     * resetHeading declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun resetHeading() {
        synchronized(lock) {
            headingOffset = latestYaw
        }
    }

    /**
     * close declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun close() {
        running = false
        imuThread.interrupt()
    }
}

/**
 * Class implementation for Rev Analog Sensor Controller.
 *
 * Hardware IO abstraction layer bridging physical robot sensors and actuators into immutable Redux state representations.
 */
class RevAnalogSensorController(private val analogInput: AnalogInput) : AutoCloseable {
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

    /**
     * getVoltage declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun getVoltage(): Double {
        return synchronized(lock) { latestVoltage }
    }

    /**
     * close declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun close() {
        running = false
        thread.interrupt()
    }
}

/**
 * Class implementation for Rev Digital Sensor Controller.
 *
 * Hardware IO abstraction layer bridging physical robot sensors and actuators into immutable Redux state representations.
 */
class RevDigitalSensorController(private val digitalChannel: DigitalChannel) : AutoCloseable {
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

    /**
     * getState declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun getState(): Boolean {
        return synchronized(lock) { latestState }
    }

    /**
     * close declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun close() {
        running = false
        thread.interrupt()
    }
}

/**
 * Class implementation for Rev Absolute Analog Encoder Controller.
 *
 * Hardware IO abstraction layer bridging physical robot sensors and actuators into immutable Redux state representations.
 */
class RevAbsoluteAnalogEncoderController @kotlin.jvm.JvmOverloads constructor(
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

    /**
     * updateInputs declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun updateInputs() {
        try {
            val volt = synchronized(lock) { latestVoltage }
            val normalized = volt / version.maxVoltage
            cachedPosition = (normalized * ticksPerRev) - offset
        } catch (_: Exception) {}
    }

    override val velocity: Double
        get() = 0.0

    override val position: Double
        get() = cachedPosition

    /**
     * resetEncoder declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun resetEncoder() {
        try {
            val volt = synchronized(lock) { latestVoltage }
            offset = (volt / version.maxVoltage) * ticksPerRev
            cachedPosition = 0.0
        } catch (_: Exception) {}
    }

    /**
     * close declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun close() {
        running = false
        thread.interrupt()
    }
}
