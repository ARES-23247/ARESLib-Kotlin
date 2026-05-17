package com.areslib.ftc.hardware

import com.qualcomm.robotcore.hardware.I2cDeviceSynchDevice
import com.qualcomm.robotcore.hardware.I2cDeviceSynch
import com.qualcomm.robotcore.hardware.configuration.annotations.DeviceProperties
import com.qualcomm.robotcore.hardware.configuration.annotations.I2cDeviceType
import com.areslib.hardware.MotorIO
import com.areslib.hardware.OdometryIO
import com.areslib.math.Pose2d
import com.areslib.math.Rotation2d
import java.nio.ByteBuffer
import java.nio.ByteOrder

@I2cDeviceType
@DeviceProperties(
    name = "DigitalChickenLabs OctoQuad",
    xmlTag = "OctoQuad",
    description = "OctoQuad 8-channel quadrature encoder / localizer module"
)
class OctoQuadFWv3(deviceClient: I2cDeviceSynch) : I2cDeviceSynchDevice<I2cDeviceSynch>(deviceClient, true) {

    companion object {
        const val OCTOQUAD_I2C_ADDRESS = 0x30
        const val OCTOQUAD_CHIP_ID: Byte = 0x51
        const val SUPPORTED_FW_VERSION_MAJ = 3

        const val REG_CHIP_ID = 0x00
        const val REG_FW_MAJ = 0x01
        const val REG_FW_MIN = 0x02
        
        // Encoder Data Registers
        const val REG_ENC_0 = 0x20
        const val REG_VEL_0 = 0x40
        const val REG_PULSE_WIDTH_0 = 0x80 // Pulse width in microseconds
        
        // Localizer Registers
        const val REG_LOC_STATUS = 0x60
        const val REG_LOC_X = 0x62
        const val REG_LOC_Y = 0x64
        const val REG_LOC_VEL_X = 0x66
        const val REG_LOC_VEL_Y = 0x68
        const val REG_LOC_H = 0x6A
        const val REG_LOC_VEL_H = 0x6C
        
        val OCTOQUAD_ENDIAN = ByteOrder.LITTLE_ENDIAN
        const val SCALAR_LOCALIZER_HEADING = 0.001f
        const val SCALAR_LOCALIZER_HEADING_VELOCITY = 0.001f
    }

    private var isInitialized = false

    override fun doInitialize(): Boolean {
        deviceClient.i2cAddress = com.qualcomm.robotcore.hardware.I2cAddr.create7bit(OCTOQUAD_I2C_ADDRESS)
        val chipId = deviceClient.read8(REG_CHIP_ID)
        val fwMaj = deviceClient.read8(REG_FW_MAJ)
        
        if (chipId != OCTOQUAD_CHIP_ID) {
            return false // Invalid device
        }
        
        if (fwMaj.toInt() != SUPPORTED_FW_VERSION_MAJ) {
            // Firmware mismatch
        }
        
        isInitialized = true
        return true
    }

    override fun getManufacturer(): Manufacturer = Manufacturer.Other

    override fun getDeviceName(): String = "OctoQuad FWv3"

    /**
     * Reads a single encoder position
     */
    fun readEncoderPosition(channel: Int): Int {
        val bytes = deviceClient.read(REG_ENC_0 + (channel * 4), 4)
        val buf = ByteBuffer.wrap(bytes).order(OCTOQUAD_ENDIAN)
        return buf.int
    }

    /**
     * Reads a single encoder velocity
     */
    fun readEncoderVelocity(channel: Int): Int {
        val bytes = deviceClient.read(REG_VEL_0 + (channel * 4), 4)
        val buf = ByteBuffer.wrap(bytes).order(OCTOQUAD_ENDIAN)
        return buf.int
    }

    /**
     * Reads the pulse width of a channel in microseconds
     */
    fun readChannelPulseWidth(channel: Int): Int {
        val bytes = deviceClient.read(REG_PULSE_WIDTH_0 + (channel * 2), 2)
        val buf = ByteBuffer.wrap(bytes).order(OCTOQUAD_ENDIAN)
        return buf.short.toInt() and 0xFFFF
    }

    /**
     * Resets a single encoder
     */
    fun resetEncoder(channel: Int) {
        // Implementation for resetting encoder via command register
        // Command 0x01 is Reset Single Encoder (Dat0 = channel)
        val cmdBytes = byteArrayOf(0x01, channel.toByte())
        deviceClient.write(0x10, cmdBytes) // 0x10 is COMMAND register
    }

    data class LocalizerDataBlock(
        var posX_mm: Short = 0,
        var posY_mm: Short = 0,
        var heading_rad: Float = 0f,
        var velX_mmS: Short = 0,
        var velY_mmS: Short = 0,
        var velHeading_radS: Float = 0f
    )

    fun readLocalizerData(): LocalizerDataBlock {
        val bytes = deviceClient.read(REG_LOC_X, 12)
        val buf = ByteBuffer.wrap(bytes).order(OCTOQUAD_ENDIAN)
        
        val block = LocalizerDataBlock()
        block.posX_mm = buf.short
        block.posY_mm = buf.short
        block.velX_mmS = buf.short
        block.velY_mmS = buf.short
        block.heading_rad = buf.short * SCALAR_LOCALIZER_HEADING
        block.velHeading_radS = buf.short * SCALAR_LOCALIZER_HEADING_VELOCITY
        return block
    }
}

/**
 * Wrapper for an individual encoder plugged into the OctoQuad.
 */
class OctoQuadEncoderIO(private val octoQuad: OctoQuadFWv3, private val channel: Int) : MotorIO {
    override var power: Double
        get() = 0.0 // Encoders are read-only
        set(value) {
            // Encoders are read-only, cannot set power
        }

    override val velocity: Double
        get() = octoQuad.readEncoderVelocity(channel).toDouble()

    override val position: Double
        get() = octoQuad.readEncoderPosition(channel).toDouble()

    override fun resetEncoder() {
        octoQuad.resetEncoder(channel)
    }
}

/**
 * Wrapper for an absolute PWM encoder plugged into the OctoQuad.
 */
class OctoQuadAbsolutePWMEncoder(
    private val octoQuad: OctoQuadFWv3,
    private val channel: Int,
    private val version: com.areslib.hardware.RevEncoderVersion = com.areslib.hardware.RevEncoderVersion.V1,
    private val ticksPerRev: Double = 8192.0
) : MotorIO {
    private var offset = 0.0

    override var power: Double
        get() = 0.0
        set(value) {}

    override val velocity: Double
        get() = 0.0

    override val position: Double
        get() {
            val pulseUs = octoQuad.readChannelPulseWidth(channel).toDouble()
            val normalized = (pulseUs - version.minPulseUs) / (version.maxPulseUs - version.minPulseUs)
            return (normalized.coerceIn(0.0, 1.0) * ticksPerRev) - offset
        }

    override fun resetEncoder() {
        val pulseUs = octoQuad.readChannelPulseWidth(channel).toDouble()
        val normalized = (pulseUs - version.minPulseUs) / (version.maxPulseUs - version.minPulseUs)
        offset = normalized.coerceIn(0.0, 1.0) * ticksPerRev
    }
}

/**
 * Wrapper for the OctoQuad's absolute localizer feature.
 */
class OctoQuadOdometryIO(private val octoQuad: OctoQuadFWv3) : OdometryIO {
    private var lastData = OctoQuadFWv3.LocalizerDataBlock()

    override fun initialize(startPose: Pose2d) {
        // Reset command
        octoQuad.resetEncoder(0) // Dummy implementation for now
    }

    override fun update() {
        lastData = octoQuad.readLocalizerData()
    }

    override val position: Pose2d
        get() {
            return Pose2d(
                x = lastData.posX_mm / 1000.0,
                y = lastData.posY_mm / 1000.0,
                heading = Rotation2d(lastData.heading_rad.toDouble())
            )
        }

    override val velocity: Pose2d
        get() {
            return Pose2d(
                x = lastData.velX_mmS / 1000.0,
                y = lastData.velY_mmS / 1000.0,
                heading = Rotation2d(lastData.velHeading_radS.toDouble())
            )
        }
}
