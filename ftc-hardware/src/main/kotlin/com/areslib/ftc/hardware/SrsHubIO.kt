package com.areslib.ftc.hardware

import com.qualcomm.robotcore.hardware.I2cDeviceSynchDevice
import com.qualcomm.robotcore.hardware.I2cDeviceSynch
import com.qualcomm.robotcore.hardware.configuration.annotations.DeviceProperties
import com.qualcomm.robotcore.hardware.AnalogInput
import com.qualcomm.robotcore.hardware.configuration.annotations.I2cDeviceType
import com.areslib.hardware.actuator.MotorIO
import com.areslib.hardware.actuator.RevEncoderVersion
import com.areslib.hardware.actuator.ServoIO
import com.areslib.hardware.sensor.ColorSensorIO
import com.areslib.hardware.sensor.DistanceSensorIO
import com.areslib.hardware.sensor.MultizoneDistanceSensorIO
import com.areslib.hardware.drive.OdometryIO
import com.areslib.math.geometry.Pose2d
import com.areslib.math.geometry.Rotation2d

@I2cDeviceType
@DeviceProperties(
    name = "SRS Hub",
    xmlTag = "SrsHub",
    description = "SRS Robotics Expansion Hub over I2C"
)
/**
 * Class implementation for Srs Hub Driver.
 *
 * Hardware IO abstraction layer bridging physical robot sensors and actuators into immutable Redux state representations.
 */
class SrsHubDriver(deviceClient: I2cDeviceSynch) : I2cDeviceSynchDevice<I2cDeviceSynch>(deviceClient, true), AutoCloseable {
    // Structured bulk cache buffers
    private val cachedAnalog = DoubleArray(4)
    private val cachedDigital = BooleanArray(4)
    private val cachedEncoders = IntArray(4)
    private val cachedPwmPulseWidths = IntArray(4)
    
    // I2C Sub-sensors cached data
    private val cachedVL53L5CX = Array(4) { IntArray(64) }
    
    // APDS9151 and VL53L0X cache (ports 0-3)
    private val cachedColorsRed = IntArray(4)
    private val cachedColorsGreen = IntArray(4)
    private val cachedColorsBlue = IntArray(4)
    private val cachedColorsAlpha = IntArray(4)
    private val cachedI2cDistances = DoubleArray(4)
    
    // Pinpoint Odometry cache (ports 0-3)
    private val cachedOdoX = DoubleArray(4)
    private val cachedOdoY = DoubleArray(4)
    private val cachedOdoHeading = DoubleArray(4)
    private val cachedOdoVelX = DoubleArray(4)
    private val cachedOdoVelY = DoubleArray(4)
    private val cachedOdoHeadingVel = DoubleArray(4)

    private val activePinpoints = BooleanArray(4)
    private val pendingServoPositions = DoubleArray(4) { Double.NaN }
    private val pingBuffers = Array(4) { byteArrayOf(1) }
    private val servoWriteBuffers = Array(4) { ByteArray(2) }

    private val lock = Any()
    private var running = true
    private var isInitialized = false

    private val thread = Thread {
        while (running) {
            if (isInitialized) {
                // 1. Process pending servo writes
                synchronized(lock) {
                    for (port in 0 until 4) {
                        val pos = pendingServoPositions[port]
                        if (!pos.isNaN()) {
                            try {
                                val raw = (pos.coerceIn(0.0, 1.0) * 65535.0).toInt()
                                val buffer = servoWriteBuffers[port]
                                buffer[0] = (raw and 0xFF).toByte()
                                buffer[1] = ((raw shr 8) and 0xFF).toByte()
                                deviceClient.write(16 + port * 2, buffer)
                                pendingServoPositions[port] = Double.NaN
                            } catch (_: Exception) {}
                        }
                    }
                }

                // 2. Trigger updates for active pinpoint sensors
                synchronized(lock) {
                    for (port in 0 until 4) {
                        if (activePinpoints[port]) {
                            try {
                                deviceClient.write(124 + port, pingBuffers[port])
                            } catch (_: Exception) {}
                        }
                    }
                }

                // 3. Poll the hub registers
                pollHub()
            }
            try {
                Thread.sleep(5)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
        }
    }.apply {
        isDaemon = true
        name = "ARES-SrsHub-Thread"
    }

    /**
     * registerPinpoint declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun registerPinpoint(port: Int) {
        synchronized(lock) {
            if (port in 0 until 4) {
                activePinpoints[port] = true
            }
        }
    }

    init {
        com.areslib.hardware.HardwareRegistry.registerCloseable(this)
        thread.start()
    }

    /**
     * doInitialize declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun doInitialize(): Boolean {
        return try {
            // Set up automatic repeated read window for the entire 256-byte register range
            deviceClient.readWindow = I2cDeviceSynch.ReadWindow(0, 256, I2cDeviceSynch.ReadMode.REPEAT)
            isInitialized = true
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * getManufacturer declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun getManufacturer(): Manufacturer = Manufacturer.Other

    /**
     * getDeviceName declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun getDeviceName(): String = "SRS Hub"

    /**
     * Polls the SRS Hub exactly once, fetching the entire integrated register space
     * containing analog, digital, encoders, and I2C sub-device telemetry in a single transaction.
     * Handled by the background thread, so this method is now a no-op wrapper.
     */
    fun update() {
        // No-op wrapper to satisfy legacy callers on the main thread
    }

    private fun pollHub() {
        try {
            val data = deviceClient.read(0, 256)
            if (data.size < 256) return // Safeguard against incomplete read

            synchronized(lock) {
                // 1. Parse Analog Input Voltages (Registers 0-7, 2 bytes per port)
                for (i in 0 until 4) {
                    val raw = (data[i * 2].toInt() and 0xFF) or ((data[i * 2 + 1].toInt() and 0xFF) shl 8)
                    cachedAnalog[i] = (raw / 65535.0) * 3.3
                }

                // 2. Parse Digital States (Register 8)
                val digitalByte = data[8].toInt()
                for (i in 0 until 4) {
                    cachedDigital[i] = (digitalByte and (1 shl i)) != 0
                }

                // 3. Parse Motor Encoders (Registers 9-24, 4 bytes per port)
                for (i in 0 until 4) {
                    val offset = 9 + i * 4
                    cachedEncoders[i] = readInt32(data, offset)
                }

                // 3b. Parse PWM Pulse Widths (Registers 24-31, 2 bytes per port)
                for (i in 0 until 4) {
                    val offset = 24 + i * 2
                    cachedPwmPulseWidths[i] = (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
                }

                // 4. Parse APDS9151 and VL53L0X I2C Sub-devices (Registers 32-95, 16 bytes per port)
                for (port in 0 until 4) {
                    val base = 32 + port * 16
                    cachedColorsRed[port] = (data[base].toInt() and 0xFF) or ((data[base + 1].toInt() and 0xFF) shl 8)
                    cachedColorsGreen[port] = (data[base + 2].toInt() and 0xFF) or ((data[base + 3].toInt() and 0xFF) shl 8)
                    cachedColorsBlue[port] = (data[base + 4].toInt() and 0xFF) or ((data[base + 5].toInt() and 0xFF) shl 8)
                    cachedColorsAlpha[port] = (data[base + 6].toInt() and 0xFF) or ((data[base + 7].toInt() and 0xFF) shl 8)
                    
                    val rawDistMm = (data[base + 8].toInt() and 0xFF) or ((data[base + 9].toInt() and 0xFF) shl 8)
                    cachedI2cDistances[port] = rawDistMm / 1000.0 // mm to meters
                }

                // 5. Parse Pinpoint Odometry data (Registers 96-191, 24 bytes per port)
                for (port in 0 until 4) {
                    val base = 96 + port * 24
                    cachedOdoX[port] = readInt32(data, base).toDouble()
                    cachedOdoY[port] = readInt32(data, base + 4).toDouble()
                    
                    val rawHeading = readInt32(data, base + 8)
                    cachedOdoHeading[port] = rawHeading / 1e6 // microradians to radians
                    
                    cachedOdoVelX[port] = readInt32(data, base + 12).toDouble()
                    cachedOdoVelY[port] = readInt32(data, base + 16).toDouble()
                    
                    val rawHeadingVel = readInt32(data, base + 20)
                    cachedOdoHeadingVel[port] = rawHeadingVel / 1e6
                }

                // 6. Parse VL53L5CX Multizone distance data (Registers 192-255, 64 zones of 2-byte values mapped dynamically)
                val vl53Base = 192
                for (i in 0 until 32) { // Retrieve standard 32 zones in single bulk block
                    val offset = vl53Base + (i * 2)
                    if (offset + 1 < data.size) {
                        val mm = (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
                        cachedVL53L5CX[0][i] = mm
                    }
                }
            }
        } catch (_: Exception) {
            // Swallow I2C read exceptions gracefully
        }
    }

    private fun readInt32(data: ByteArray, offset: Int): Int {
        if (offset + 3 >= data.size) return 0
        return (data[offset].toInt() and 0xFF) or
                ((data[offset + 1].toInt() and 0xFF) shl 8) or
                ((data[offset + 2].toInt() and 0xFF) shl 16) or
                ((data[offset + 3].toInt() and 0xFF) shl 24)
    }

    // Now, all accessor methods return cached data instantly in 0.0ms!
    fun getAnalogVoltage(port: Int): Double = synchronized(lock) { cachedAnalog.getOrElse(port) { 0.0 } }
    /**
     * getDigitalState declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun getDigitalState(port: Int): Boolean = synchronized(lock) { cachedDigital.getOrElse(port) { false } }
    /**
     * readEncoder declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun readEncoder(port: Int): Int = synchronized(lock) { cachedEncoders.getOrElse(port) { 0 } }
    /**
     * getPwmPulseWidth declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun getPwmPulseWidth(port: Int): Int = synchronized(lock) { cachedPwmPulseWidths.getOrElse(port) { 0 } }
    
    /**
     * getVL53L5CXDistances declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun getVL53L5CXDistances(port: Int): IntArray = synchronized(lock) { cachedVL53L5CX.getOrElse(port) { IntArray(64) } }
    /**
     * getI2cColorRed declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun getI2cColorRed(port: Int): Int = synchronized(lock) { cachedColorsRed.getOrElse(port) { 0 } }
    /**
     * getI2cColorGreen declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun getI2cColorGreen(port: Int): Int = synchronized(lock) { cachedColorsGreen.getOrElse(port) { 0 } }
    /**
     * getI2cColorBlue declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun getI2cColorBlue(port: Int): Int = synchronized(lock) { cachedColorsBlue.getOrElse(port) { 0 } }
    /**
     * getI2cColorAlpha declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun getI2cColorAlpha(port: Int): Int = synchronized(lock) { cachedColorsAlpha.getOrElse(port) { 0 } }
    /**
     * getI2cDistanceMeters declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun getI2cDistanceMeters(port: Int): Double = synchronized(lock) { cachedI2cDistances.getOrElse(port) { 0.0 } }

    /**
     * getI2cOdometryX declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun getI2cOdometryX(port: Int): Double = synchronized(lock) { cachedOdoX.getOrElse(port) { 0.0 } }
    /**
     * getI2cOdometryY declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun getI2cOdometryY(port: Int): Double = synchronized(lock) { cachedOdoY.getOrElse(port) { 0.0 } }
    /**
     * getI2cOdometryHeading declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun getI2cOdometryHeading(port: Int): Double = synchronized(lock) { cachedOdoHeading.getOrElse(port) { 0.0 } }
    /**
     * getI2cOdometryVelX declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun getI2cOdometryVelX(port: Int): Double = synchronized(lock) { cachedOdoVelX.getOrElse(port) { 0.0 } }
    /**
     * getI2cOdometryYVel declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun getI2cOdometryYVel(port: Int): Double = synchronized(lock) { cachedOdoVelY.getOrElse(port) { 0.0 } }
    /**
     * getI2cOdometryHeadingVel declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun getI2cOdometryHeadingVel(port: Int): Double = synchronized(lock) { cachedOdoHeadingVel.getOrElse(port) { 0.0 } }

    // Direct actuator writes (these bypass the read cache and run immediately)
    fun setPwmDutyCycle(port: Int, dutyCycle: Double) {
        synchronized(lock) {
            if (port in 0 until 4) {
                pendingServoPositions[port] = dutyCycle
            }
        }
    }

    /**
     * readPwmPulseWidth declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun readPwmPulseWidth(port: Int): Int {
        return try {
            val data = deviceClient.read(24 + port * 2, 2)
            if (data.size >= 2) {
                (data[0].toInt() and 0xFF) or ((data[1].toInt() and 0xFF) shl 8)
            } else {
                0
            }
        } catch (_: Exception) {
            0
        }
    }

    /**
     * resetI2cOdometry declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun resetI2cOdometry(port: Int) {
        try {
            deviceClient.write(120 + port, byteArrayOf(1))
        } catch (_: Exception) {}
    }

    /**
     * updateI2cOdometry declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun updateI2cOdometry() {
        // Handled asynchronously in background thread
    }

    /**
     * close declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun close() {
        running = false
        thread.interrupt()
    }
}



