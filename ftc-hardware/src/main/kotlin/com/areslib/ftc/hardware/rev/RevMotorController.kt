package com.areslib.ftc.hardware.rev

import com.areslib.hardware.actuator.MotorIO
import com.areslib.hardware.HardwareRegistry
import com.qualcomm.robotcore.hardware.DcMotorEx
import com.qualcomm.robotcore.hardware.DcMotor
import com.qualcomm.robotcore.hardware.CRServo
import com.areslib.hardware.actuator.ServoIO
import com.qualcomm.robotcore.hardware.Servo

class RevMotorController(
    private val motor: DcMotorEx,
    val name: String? = null
) : MotorIO, AutoCloseable {
    private var encoderOffset = 0.0
    private var cachedPosition = 0.0
    private var cachedVelocity = 0.0
    private var cachedAmps = 0.0
    private val currentLock = Any()

    init {
        try {
            motor.mode = DcMotor.RunMode.STOP_AND_RESET_ENCODER
            motor.mode = DcMotor.RunMode.RUN_WITHOUT_ENCODER
        } catch (_: Exception) {}

        if (name != null) {
            HardwareRegistry.registerMotor(name, this)
        }
        
        RevBulkDataReader.registerMotor(this)
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

            if (kotlin.math.abs(value) > 0.5 && kotlin.math.abs(currentVel) < 10.0) {
                when {
                    stallStartTimeMs == 0L -> stallStartTimeMs = timeMs
                    timeMs - stallStartTimeMs > 500 -> isStalled = true
                }
            } else {
                stallStartTimeMs = 0L
                isStalled = false
            }

            val amps = this.currentAmps
            if (amps > 9.2) {
                isStalled = true
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
            if (motor.javaClass.simpleName.contains("Mock")) {
                Thread.sleep(2)
            }
            val amps = motor.getCurrent(org.firstinspires.ftc.robotcore.external.navigation.CurrentUnit.AMPS)
            synchronized(currentLock) {
                cachedAmps = amps
            }
        } catch (_: Exception) {}
    }

    override fun refresh() {
        updateInputs()
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
        RevBulkDataReader.unregisterMotor(this)
    }
}

class RevCRServoController(
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

class RevEncoderController(
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

    override fun refresh() {
        updateInputs()
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

class RevCompositeMotorController(
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

class RevServoController(
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
