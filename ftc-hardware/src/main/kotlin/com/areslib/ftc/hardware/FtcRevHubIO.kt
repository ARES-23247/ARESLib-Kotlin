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
import com.qualcomm.hardware.bosch.BNO055IMU
import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit
import org.firstinspires.ftc.robotcore.external.navigation.AxesOrder
import org.firstinspires.ftc.robotcore.external.navigation.AxesReference

class FtcMotor(private val motor: DcMotorEx) : MotorIO {
    init {
        motor.mode = DcMotor.RunMode.STOP_AND_RESET_ENCODER
        motor.mode = DcMotor.RunMode.RUN_WITHOUT_ENCODER
    }

    private var targetPower: Double = 0.0

    override var powerScale: Double = 1.0
        set(value) {
            field = value.coerceIn(0.0, 1.0)
            motor.power = targetPower * field
        }

    override var power: Double
        get() = targetPower
        set(value) {
            targetPower = value
            motor.power = value * powerScale
        }

    override val velocity: Double
        get() = motor.velocity

    override val position: Double
        get() = motor.currentPosition.toDouble()

    override fun resetEncoder() {
        val currentMode = motor.mode
        motor.mode = DcMotor.RunMode.STOP_AND_RESET_ENCODER
        motor.mode = currentMode
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

    override var powerScale: Double = 1.0
        set(value) {
            field = value.coerceIn(0.0, 1.0)
            crServo.power = targetPower * field
        }

    override var power: Double
        get() = targetPower
        set(value) {
            targetPower = value
            crServo.power = value * powerScale
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
    override var power: Double
        get() = 0.0
        set(value) {}

    override val velocity: Double
        get() = motor.velocity

    override val position: Double
        get() = motor.currentPosition.toDouble()

    override fun resetEncoder() {
        val currentMode = motor.mode
        motor.mode = DcMotor.RunMode.STOP_AND_RESET_ENCODER
        motor.mode = currentMode
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

    override var power: Double
        get() = 0.0
        set(value) {}

    override val velocity: Double
        get() = 0.0 // Velocity estimation from absolute analog usually requires a filter

    override val position: Double
        get() {
            val normalized = analogInput.voltage / version.maxVoltage
            return (normalized * ticksPerRev) - offset
        }

    override fun resetEncoder() {
        offset = (analogInput.voltage / version.maxVoltage) * ticksPerRev
    }
}

class FtcServo(private val servo: Servo) : ServoIO {
    override var position: Double
        get() = servo.position
        set(value) {
            servo.position = value
        }
}

class FtcImu(private val imu: BNO055IMU) : ImuIO {
    private var headingOffset = 0.0

    init {
        val parameters = BNO055IMU.Parameters()
        parameters.angleUnit = BNO055IMU.AngleUnit.RADIANS
        imu.initialize(parameters)
    }

    override fun update() {
        // BNO055 automatically updates internal state. We just read it when needed.
    }

    override val heading: Rotation2d
        get() {
            val angles = imu.getAngularOrientation(AxesReference.INTRINSIC, AxesOrder.ZYX, AngleUnit.RADIANS)
            return Rotation2d(angles.firstAngle.toDouble() - headingOffset)
        }

    override fun resetHeading() {
        val angles = imu.getAngularOrientation(AxesReference.INTRINSIC, AxesOrder.ZYX, AngleUnit.RADIANS)
        headingOffset = angles.firstAngle.toDouble()
    }
}

class FtcAnalogSensor(private val analogInput: AnalogInput) {
    fun getVoltage(): Double {
        return analogInput.voltage
    }
}

class FtcDigitalSensor(private val digitalChannel: DigitalChannel) {
    init {
        digitalChannel.mode = DigitalChannel.Mode.INPUT
    }

    fun getState(): Boolean {
        return digitalChannel.state
    }
}
