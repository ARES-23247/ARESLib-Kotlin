package com.areslib.ftc.hardware

import com.areslib.ftc.hardware.rev.*
import com.areslib.hardware.actuator.MotorIO
import com.areslib.hardware.actuator.RevEncoderVersion
import com.areslib.hardware.actuator.ServoIO
import com.areslib.hardware.sensor.ImuIO
import com.areslib.hardware.sensor.ImuInputs
import com.qualcomm.robotcore.hardware.DcMotorEx
import com.qualcomm.robotcore.hardware.CRServo
import com.qualcomm.robotcore.hardware.AnalogInput
import com.qualcomm.robotcore.hardware.DigitalChannel
import com.qualcomm.robotcore.hardware.IMU
import com.qualcomm.robotcore.hardware.Servo

class FtcMotor(motor: DcMotorEx, name: String? = null) : MotorIO, AutoCloseable {
    private val delegate = RevMotorController(motor, name)
    override var powerScale: Double
        get() = delegate.powerScale
        set(value) { delegate.powerScale = value }
    override var power: Double
        get() = delegate.power
        set(value) { delegate.power = value }
    override val velocity: Double get() = delegate.velocity
    override val position: Double get() = delegate.position
    override val currentAmps: Double get() = delegate.currentAmps
    fun updateInputs() = delegate.updateInputs()
    fun pollCurrentSync() = delegate.pollCurrentSync()
    override fun refresh() = delegate.refresh()
    override fun resetEncoder() = delegate.resetEncoder()
    override fun close() = delegate.close()
    
    companion object {
        fun unregisterAll() = RevBulkDataReader.unregisterAll()
    }
}

class FtcCRServo(crServo: CRServo, externalEncoder: MotorIO? = null, name: String? = null) : MotorIO {
    private val delegate = RevCRServoController(crServo, externalEncoder, name)
    override var powerScale: Double
        get() = delegate.powerScale
        set(value) { delegate.powerScale = value }
    override var power: Double
        get() = delegate.power
        set(value) { delegate.power = value }
    override val velocity: Double get() = delegate.velocity
    override val position: Double get() = delegate.position
    override val currentAmps: Double get() = delegate.currentAmps
    override fun resetEncoder() = delegate.resetEncoder()
}

class FtcEncoder(motor: DcMotorEx, name: String? = null) : MotorIO {
    private val delegate = RevEncoderController(motor, name)
    override var power: Double
        get() = delegate.power
        set(value) { delegate.power = value }
    override val velocity: Double get() = delegate.velocity
    override val position: Double get() = delegate.position
    override val currentAmps: Double get() = delegate.currentAmps
    fun updateInputs() = delegate.updateInputs()
    override fun refresh() = delegate.refresh()
    override fun resetEncoder() = delegate.resetEncoder()
}

class CompositeMotorIO(actuator: MotorIO, sensor: MotorIO) : MotorIO {
    private val delegate = RevCompositeMotorController(actuator, sensor)
    override var power: Double
        get() = delegate.power
        set(value) { delegate.power = value }
    override val velocity: Double get() = delegate.velocity
    override val position: Double get() = delegate.position
    override val currentAmps: Double get() = delegate.currentAmps
    override fun resetEncoder() = delegate.resetEncoder()
}

class FtcAbsoluteAnalogEncoder @kotlin.jvm.JvmOverloads constructor(
    analogInput: AnalogInput,
    version: RevEncoderVersion = RevEncoderVersion.V1,
    ticksPerRev: Double = 8192.0,
    name: String? = null
) : MotorIO, AutoCloseable {
    private val delegate = RevAbsoluteAnalogEncoderController(analogInput, version, ticksPerRev, name)
    override var power: Double
        get() = delegate.power
        set(value) { delegate.power = value }
    override val velocity: Double get() = delegate.velocity
    override val position: Double get() = delegate.position
    override val currentAmps: Double get() = delegate.currentAmps
    fun updateInputs() = delegate.updateInputs()
    override fun resetEncoder() = delegate.resetEncoder()
    override fun close() = delegate.close()
}

class FtcServo(servo: Servo, name: String? = null) : ServoIO {
    private val delegate = RevServoController(servo, name)
    override var position: Double
        get() = delegate.position
        set(value) { delegate.position = value }
}

class FtcImu(imu: IMU) : ImuIO, AutoCloseable {
    private val delegate = RevImuController(imu)
    override fun updateInputs(inputs: ImuInputs) = delegate.updateInputs(inputs)
    override fun resetHeading() = delegate.resetHeading()
    override fun close() = delegate.close()
}

class FtcAnalogSensor(analogInput: AnalogInput) : AutoCloseable {
    private val delegate = RevAnalogSensorController(analogInput)
    fun getVoltage(): Double = delegate.getVoltage()
    override fun close() = delegate.close()
}

class FtcDigitalSensor(digitalChannel: DigitalChannel) : AutoCloseable {
    private val delegate = RevDigitalSensorController(digitalChannel)
    fun getState(): Boolean = delegate.getState()
    override fun close() = delegate.close()
}
