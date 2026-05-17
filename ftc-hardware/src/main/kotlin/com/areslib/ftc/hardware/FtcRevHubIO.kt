package com.areslib.ftc.hardware

import com.areslib.hardware.MotorIO
import com.areslib.hardware.ServoIO
import com.areslib.hardware.ImuIO
import com.areslib.math.Rotation2d
import com.qualcomm.robotcore.hardware.DcMotorEx
import com.qualcomm.robotcore.hardware.DcMotor
import com.qualcomm.robotcore.hardware.Servo
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

    override var power: Double
        get() = motor.power
        set(value) {
            motor.power = value
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
