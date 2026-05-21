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
    private var lastUpdateTimeMs = 0L

    // Cache buffers for registers
    private val cachedPositions = IntArray(8)
    private val cachedVelocities = IntArray(8)
    private val cachedPulseWidths = IntArray(8)

    override fun doInitialize(): Boolean {
        return try {
            deviceClient.i2cAddress = com.qualcomm.robotcore.hardware.I2cAddr.create7bit(OCTOQUAD_I2C_ADDRESS)
            val chipId = deviceClient.read8(REG_CHIP_ID)
            val fwMaj = deviceClient.read8(REG_FW_MAJ)
            
            if (chipId != OCTOQUAD_CHIP_ID) {
                false
            } else {
                isInitialized = true
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    override fun getManufacturer(): Manufacturer = Manufacturer.Other

    override fun getDeviceName(): String = "OctoQuad FWv3"

    /**
     * Bulk reads positions, velocities, and pulse widths.
     * Rate-limited to once per 3ms loop cycle to prevent duplicate I2C reads.
     */
    fun update() {
        if (!isInitialized) return
        val now = com.areslib.util.RobotClock.currentTimeMillis()
        if (now - lastUpdateTimeMs < 3) return
        lastUpdateTimeMs = now

        try {
            val posBytes = deviceClient.read(REG_ENC_0, 32)
            if (posBytes.size >= 32) {
                val buf = ByteBuffer.wrap(posBytes).order(OCTOQUAD_ENDIAN)
                for (i in 0 until 8) {
                    cachedPositions[i] = buf.int
                }
            }
        } catch (e: Exception) {
            // Gracefully handle read failures
        }

        try {
            val velBytes = deviceClient.read(REG_VEL_0, 32)
            if (velBytes.size >= 32) {
                val buf = ByteBuffer.wrap(velBytes).order(OCTOQUAD_ENDIAN)
                for (i in 0 until 8) {
                    cachedVelocities[i] = buf.int
                }
            }
        } catch (e: Exception) {
            // Gracefully handle read failures
        }

        try {
            val pwBytes = deviceClient.read(REG_PULSE_WIDTH_0, 16)
            if (pwBytes.size >= 16) {
                val buf = ByteBuffer.wrap(pwBytes).order(OCTOQUAD_ENDIAN)
                for (i in 0 until 8) {
                    cachedPulseWidths[i] = buf.short.toInt() and 0xFFFF
                }
            }
        } catch (e: Exception) {
            // Gracefully handle read failures
        }
    }

    fun getCachedPosition(channel: Int): Int = cachedPositions.getOrElse(channel) { 0 }
    fun getCachedVelocity(channel: Int): Int = cachedVelocities.getOrElse(channel) { 0 }
    fun getCachedPulseWidth(channel: Int): Int = cachedPulseWidths.getOrElse(channel) { 0 }

    /**
     * Reads a single encoder position (legacy, non-cached fallback)
     */
    fun readEncoderPosition(channel: Int): Int {
        return try {
            val bytes = deviceClient.read(REG_ENC_0 + (channel * 4), 4)
            val buf = ByteBuffer.wrap(bytes).order(OCTOQUAD_ENDIAN)
            buf.int
        } catch (_: Exception) {
            0
        }
    }

    /**
     * Reads a single encoder velocity (legacy, non-cached fallback)
     */
    fun readEncoderVelocity(channel: Int): Int {
        return try {
            val bytes = deviceClient.read(REG_VEL_0 + (channel * 4), 4)
            val buf = ByteBuffer.wrap(bytes).order(OCTOQUAD_ENDIAN)
            buf.int
        } catch (_: Exception) {
            0
        }
    }

    /**
     * Reads the pulse width of a channel in microseconds (legacy, non-cached fallback)
     */
    fun readChannelPulseWidth(channel: Int): Int {
        return try {
            val bytes = deviceClient.read(REG_PULSE_WIDTH_0 + (channel * 2), 2)
            val buf = ByteBuffer.wrap(bytes).order(OCTOQUAD_ENDIAN)
            buf.short.toInt() and 0xFFFF
        } catch (_: Exception) {
            0
        }
    }

    /**
     * Resets a single encoder
     */
    fun resetEncoder(channel: Int) {
        try {
            val cmdBytes = byteArrayOf(0x01, channel.toByte())
            deviceClient.write(0x10, cmdBytes) // 0x10 is COMMAND register
        } catch (_: Exception) {}
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
        return try {
            val bytes = deviceClient.read(REG_LOC_X, 12)
            val buf = ByteBuffer.wrap(bytes).order(OCTOQUAD_ENDIAN)
            
            val block = LocalizerDataBlock()
            block.posX_mm = buf.short
            block.posY_mm = buf.short
            block.velX_mmS = buf.short
            block.velY_mmS = buf.short
            block.heading_rad = buf.short * SCALAR_LOCALIZER_HEADING
            block.velHeading_radS = buf.short * SCALAR_LOCALIZER_HEADING_VELOCITY
            block
        } catch (_: Exception) {
            LocalizerDataBlock()
        }
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

    fun updateInputs() {
        octoQuad.update()
    }

    override val velocity: Double
        get() {
            octoQuad.update()
            return octoQuad.getCachedVelocity(channel).toDouble()
        }

    override val position: Double
        get() {
            octoQuad.update()
            return octoQuad.getCachedPosition(channel).toDouble()
        }

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

    fun updateInputs() {
        octoQuad.update()
    }

    override val velocity: Double
        get() = 0.0

    override val position: Double
        get() {
            octoQuad.update()
            val pulseUs = octoQuad.getCachedPulseWidth(channel).toDouble()
            val range = version.maxPulseUs - version.minPulseUs
            val normalized = if (range != 0.0) (pulseUs - version.minPulseUs) / range else 0.0
            val clampedNormalized = if (normalized.isFinite()) normalized.coerceIn(0.0, 1.0) else 0.0
            return (clampedNormalized * ticksPerRev) - offset
        }

    override fun resetEncoder() {
        octoQuad.update()
        val pulseUs = octoQuad.getCachedPulseWidth(channel).toDouble()
        val range = version.maxPulseUs - version.minPulseUs
        val normalized = if (range != 0.0) (pulseUs - version.minPulseUs) / range else 0.0
        val clampedNormalized = if (normalized.isFinite()) normalized.coerceIn(0.0, 1.0) else 0.0
        offset = clampedNormalized * ticksPerRev
    }
}

/**
 * Wrapper for the OctoQuad's absolute localizer feature.
 */
class OctoQuadOdometryIO(private val octoQuad: OctoQuadFWv3) : OdometryIO {
    override fun initialize(startPose: Pose2d) {
        // Reset command
        octoQuad.resetEncoder(0) // Dummy implementation for now
    }

    override fun updateInputs(inputs: com.areslib.hardware.OdometryInputs) {
        octoQuad.update()
        val lastData = octoQuad.readLocalizerData()
        inputs.posX = lastData.posX_mm / 1000.0
        inputs.posY = lastData.posY_mm / 1000.0
        inputs.heading = lastData.heading_rad.toDouble()
        inputs.velX = lastData.velX_mmS / 1000.0
        inputs.velY = lastData.velY_mmS / 1000.0
        inputs.headingVelocity = lastData.velHeading_radS.toDouble()
        inputs.timestampMs = com.areslib.util.RobotClock.currentTimeMillis()
    }
}
