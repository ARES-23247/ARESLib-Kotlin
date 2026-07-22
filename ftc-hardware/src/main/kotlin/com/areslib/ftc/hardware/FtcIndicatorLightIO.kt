package com.areslib.ftc.hardware

import com.areslib.hardware.actuator.IndicatorLightColor
import com.areslib.hardware.actuator.IndicatorLightIO
import com.areslib.telemetry.ITelemetry
import com.qualcomm.robotcore.hardware.HardwareMap
import com.qualcomm.robotcore.hardware.Servo
import kotlin.math.abs

/**
 * FTC hardware implementation for the GoBilda RGB Indicator Light (3118-0808-0002).
 * Connects to a standard servo port on the Control Hub / Expansion Hub.
 * The light's color is set by writing a position value (0.0 to 1.0) corresponding
 * to the GoBilda color gradient (see Product Insight #4 for exact mapping).
 *
 * Includes position caching to skip redundant I2C writes and reduce bus congestion.
 *
 * @param hardwareMap The FTC HardwareMap.
 * @param name The hardware configuration name for this indicator light servo.
 */
/**
 * Class implementation for Ftc Indicator Light I O.
 *
 * Hardware IO abstraction layer bridging physical robot sensors and actuators into immutable Redux state representations.
 */
class FtcIndicatorLightIO(
    hardwareMap: HardwareMap,
    val name: String
) : IndicatorLightIO, AutoCloseable {

    private val servo: Servo = hardwareMap.get(Servo::class.java, name)
    private var lastSentPosition = Double.NaN

    override var currentPosition: Double = 0.0
        private set

    override fun setPosition(position: Double) {
        val clamped = position.coerceIn(0.0, 1.0)
        currentPosition = clamped
        // Skip redundant writes to avoid I2C bus congestion
        if (lastSentPosition.isNaN() || abs(clamped - lastSentPosition) > 0.001) {
            servo.position = clamped
            lastSentPosition = clamped
        }
    }

    override fun safe() {
        setPosition(IndicatorLightColor.OFF.position)
    }

    override fun refresh() {
        // Write-only device — no sensor reads needed
    }

    override fun logTelemetry(telemetry: ITelemetry, prefix: String) {
        telemetry.putNumber("$prefix/Position", currentPosition)
    }

    override fun close() {
        safe()
    }
}
