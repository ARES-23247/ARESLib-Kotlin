package com.areslib.ftc.hardware

import com.areslib.hardware.MotorIO
import com.areslib.hardware.ServoIO
import com.areslib.hardware.ImuIO
import com.areslib.math.Rotation2d
import com.qualcomm.robotcore.hardware.DcMotorEx
import com.qualcomm.robotcore.hardware.DcMotor
import com.qualcomm.robotcore.hardware.Servo
import com.qualcomm.robotcore.hardware.CRServo
import com.qualcomm.robotcore.hardware.AnalogInput
import com.qualcomm.robotcore.hardware.DigitalChannel
import com.qualcomm.robotcore.hardware.IMU
import com.qualcomm.hardware.rev.RevHubOrientationOnRobot
import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit

class FtcMotor(private val motor: DcMotorEx) : MotorIO {
    private var encoderOffset = 0.0
    private var cachedPosition = 0.0
    private var cachedVelocity = 0.0
    private var cachedAmps = 0.0
    private var updateCount = 0

    init {
        try {
            motor.mode = DcMotor.RunMode.STOP_AND_RESET_ENCODER
            motor.mode = DcMotor.RunMode.RUN_WITHOUT_ENCODER
        } catch (_: Exception) {}
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
                if (stallStartTimeMs == 0L) {
                    stallStartTimeMs = timeMs
                } else if (timeMs - stallStartTimeMs > 500) {
                    isStalled = true
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
        try {
            if (updateCount % 10 == 0) {
                cachedAmps = motor.getCurrent(org.firstinspires.ftc.robotcore.external.navigation.CurrentUnit.AMPS)
            }
            updateCount++
        } catch (_: Exception) {}
    }

    override val velocity: Double
        get() = cachedVelocity

    override val position: Double
        get() = cachedPosition

    override val currentAmps: Double
        get() = cachedAmps

    override fun resetEncoder() {
        try {
            encoderOffset = motor.currentPosition.toDouble()
            cachedPosition = 0.0
        } catch (_: Exception) {}
    }
}

/**
 * Wraps a Continuous Rotation Servo (CRServo), which behaves similarly to a DC motor
 * but lacks built-in encoder feedback. Can optionally be paired with an external
 * MotorIO representation of an encoder for closed-loop control.
 */
class FtcCRServo(
    private val crServo: CRServo,
    private val externalEncoder: MotorIO? = null
) : MotorIO {
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
class FtcEncoder(private val motor: DcMotorEx) : MotorIO {
    private var encoderOffset = 0.0
    private var cachedPosition = 0.0
    private var cachedVelocity = 0.0

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
class FtcAbsoluteAnalogEncoder(
    private val analogInput: AnalogInput,
    private val version: com.areslib.hardware.RevEncoderVersion = com.areslib.hardware.RevEncoderVersion.V1,
    private val ticksPerRev: Double = 8192.0
) : MotorIO {
    private var offset = 0.0
    private var cachedPosition = 0.0

    override var power: Double
        get() = 0.0
        @Suppress("UNUSED_PARAMETER")
        set(value) {}

    fun updateInputs() {
        try {
            val normalized = analogInput.voltage / version.maxVoltage
            cachedPosition = (normalized * ticksPerRev) - offset
        } catch (_: Exception) {}
    }

    override val velocity: Double
        get() = 0.0 // Velocity estimation from absolute analog usually requires a filter

    override val position: Double
        get() = cachedPosition

    override fun resetEncoder() {
        try {
            offset = (analogInput.voltage / version.maxVoltage) * ticksPerRev
            cachedPosition = 0.0
        } catch (_: Exception) {}
    }
}

class FtcServo(private val servo: Servo) : ServoIO {
    private var lastSentPosition = Double.NaN

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

class FtcImu(private val imu: IMU) : ImuIO {
    private var headingOffset = 0.0

    init {
        try {
            val orientation = RevHubOrientationOnRobot(
                RevHubOrientationOnRobot.LogoFacingDirection.UP,
                RevHubOrientationOnRobot.UsbFacingDirection.FORWARD
            )
            val parameters = IMU.Parameters(orientation)
            imu.initialize(parameters)
        } catch (_: Exception) {}
    }

    override fun updateInputs(inputs: com.areslib.hardware.ImuInputs) {
        try {
            val yawPitchRoll = imu.getRobotYawPitchRollAngles()
            inputs.headingRadians = yawPitchRoll.getYaw(AngleUnit.RADIANS) - headingOffset
            inputs.pitchRadians = yawPitchRoll.getPitch(AngleUnit.RADIANS)
            inputs.rollRadians = yawPitchRoll.getRoll(AngleUnit.RADIANS)
            
            val angularVel = imu.getRobotAngularVelocity(AngleUnit.RADIANS)
            inputs.yawVelocityRadPerSec = angularVel.getZRotationRate(AngleUnit.RADIANS).toDouble()
            inputs.timestampMs = com.areslib.util.RobotClock.currentTimeMillis()
        } catch (e: Exception) {
            // Read timeout / fallback: gracefully swallow exception to prevent loop hangs
        }
    }

    override fun resetHeading() {
        try {
            val yawPitchRoll = imu.getRobotYawPitchRollAngles()
            headingOffset = yawPitchRoll.getYaw(AngleUnit.RADIANS)
        } catch (_: Exception) {}
    }
}

class FtcAnalogSensor(private val analogInput: AnalogInput) {
    fun getVoltage(): Double {
        return try {
            analogInput.voltage
        } catch (_: Exception) {
            0.0
        }
    }
}

class FtcDigitalSensor(private val digitalChannel: DigitalChannel) {
    init {
        try {
            digitalChannel.mode = DigitalChannel.Mode.INPUT
        } catch (_: Exception) {}
    }

    fun getState(): Boolean {
        return try {
            digitalChannel.state
        } catch (_: Exception) {
            false
        }
    }
}
