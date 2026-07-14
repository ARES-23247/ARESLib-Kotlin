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
class OctoQuadFWv3(deviceClient: I2cDeviceSynch) : I2cDeviceSynchDevice<I2cDeviceSynch>(deviceClient, true), AutoCloseable {

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
    private var cachedLocalizerData = LocalizerDataBlock()

    // Preallocated thread-local buffers to guarantee zero dynamic allocations in loop
    private val threadPos = IntArray(8)
    private val threadVel = IntArray(8)
    private val threadPw = IntArray(8)
    private val threadLocalizer = LocalizerDataBlock()

    private fun readIntLE(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or
               ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
               ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
               ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun readShortLE(bytes: ByteArray, offset: Int): Short {
        return ((bytes[offset].toInt() and 0xFF) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 8)).toShort()
    }
    
    private val lock = Any()
    private var running = true

    init {
        com.areslib.hardware.HardwareRegistry.registerCloseable(this)
        val thread = Thread {
            while (running) {
                if (isInitialized) {
                    var posSuccess = false
                    var velSuccess = false
                    var pwSuccess = false
                    var locSuccess = false

                    try {
                        val posBytes = deviceClient.read(REG_ENC_0, 32)
                        if (posBytes.size >= 32) {
                            for (i in 0 until 8) {
                                threadPos[i] = readIntLE(posBytes, i * 4)
                            }
                            posSuccess = true
                        }
                    } catch (_: Exception) {}

                    try {
                        val velBytes = deviceClient.read(REG_VEL_0, 32)
                        if (velBytes.size >= 32) {
                            for (i in 0 until 8) {
                                threadVel[i] = readIntLE(velBytes, i * 4)
                            }
                            velSuccess = true
                        }
                    } catch (_: Exception) {}

                    try {
                        val pwBytes = deviceClient.read(REG_PULSE_WIDTH_0, 16)
                        if (pwBytes.size >= 16) {
                            for (i in 0 until 8) {
                                threadPw[i] = readShortLE(pwBytes, i * 2).toInt() and 0xFFFF
                            }
                            pwSuccess = true
                        }
                    } catch (_: Exception) {}

                    try {
                        val bytes = deviceClient.read(REG_LOC_X, 12)
                        if (bytes.size >= 12) {
                            threadLocalizer.posX_mm = readShortLE(bytes, 0)
                            threadLocalizer.posY_mm = readShortLE(bytes, 2)
                            threadLocalizer.velX_mmS = readShortLE(bytes, 4)
                            threadLocalizer.velY_mmS = readShortLE(bytes, 6)
                            threadLocalizer.heading_rad = readShortLE(bytes, 8) * SCALAR_LOCALIZER_HEADING
                            threadLocalizer.velHeading_radS = readShortLE(bytes, 10) * SCALAR_LOCALIZER_HEADING_VELOCITY
                            locSuccess = true
                        }
                    } catch (_: Exception) {}

                    synchronized(lock) {
                        if (posSuccess) System.arraycopy(threadPos, 0, cachedPositions, 0, 8)
                        if (velSuccess) System.arraycopy(threadVel, 0, cachedVelocities, 0, 8)
                        if (pwSuccess) System.arraycopy(threadPw, 0, cachedPulseWidths, 0, 8)
                        if (locSuccess) {
                            cachedLocalizerData.posX_mm = threadLocalizer.posX_mm
                            cachedLocalizerData.posY_mm = threadLocalizer.posY_mm
                            cachedLocalizerData.heading_rad = threadLocalizer.heading_rad
                            cachedLocalizerData.velX_mmS = threadLocalizer.velX_mmS
                            cachedLocalizerData.velY_mmS = threadLocalizer.velY_mmS
                            cachedLocalizerData.velHeading_radS = threadLocalizer.velHeading_radS
                        }
                        lastUpdateTimeMs = com.areslib.util.RobotClock.currentTimeMillis()
                    }
                }
                try { Thread.sleep(5) } catch (_: InterruptedException) { Thread.currentThread().interrupt(); break }
            }
        }
        thread.isDaemon = true
        thread.name = "ARES-Octoquad-Thread"
        thread.start()
    }

    override fun doInitialize(): Boolean {
        return try {
            deviceClient.i2cAddress = com.qualcomm.robotcore.hardware.I2cAddr.create7bit(OCTOQUAD_I2C_ADDRESS)
            val chipId = deviceClient.read8(REG_CHIP_ID)
            deviceClient.read8(REG_FW_MAJ)
            
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

    fun update() {
        // Background thread handles update
    }

    fun getCachedPosition(channel: Int): Int = synchronized(lock) { cachedPositions.getOrElse(channel) { 0 } }
    fun getCachedVelocity(channel: Int): Int = synchronized(lock) { cachedVelocities.getOrElse(channel) { 0 } }
    fun getCachedPulseWidth(channel: Int): Int = synchronized(lock) { cachedPulseWidths.getOrElse(channel) { 0 } }

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

    fun readLocalizerData(): LocalizerDataBlock = synchronized(lock) { cachedLocalizerData }

    override fun close() {
        running = false
    }
}

/**
 * Wrapper for an individual encoder plugged into the OctoQuad.
 */
class OctoQuadEncoderIO(private val octoQuad: OctoQuadFWv3, private val channel: Int) : MotorIO {
    override var power: Double
        get() = 0.0 // Encoders are read-only
        set(@Suppress("UNUSED_PARAMETER") value) {
            // Encoders are read-only, cannot set power
        }

    fun updateInputs() {
        octoQuad.update()
    }

    override val velocity: Double
        get() = octoQuad.getCachedVelocity(channel).toDouble()

    override val position: Double
        get() = octoQuad.getCachedPosition(channel).toDouble()

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
        set(@Suppress("UNUSED_PARAMETER") value) {}

    fun updateInputs() {
        octoQuad.update()
    }

    override val velocity: Double
        get() = 0.0

    override val position: Double
        get() {
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
