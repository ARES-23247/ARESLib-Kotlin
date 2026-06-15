package com.areslib.ftc

import com.qualcomm.robotcore.hardware.DcMotorEx
import com.qualcomm.robotcore.hardware.AnalogInput
import com.areslib.hardware.drive.SwerveModuleIO
import com.areslib.hardware.drive.SwerveModuleInputs

class SwerveModuleIOFtc(
    private val driveMotor: DcMotorEx,
    private val steerMotor: DcMotorEx,
    private val analogEncoder: AnalogInput
) : SwerveModuleIO {

    private var lastDrivePosition = 0.0
    private var lastDriveVelocity = 0.0
    private var lastSteerAbsolute = 0.0
    private var lastWarningTime = 0L

    private val lock = Any()
    @Volatile private var running = true
    private var latestVoltage = 0.0

    init {
        val thread = Thread {
            while (running) {
                try {
                    val volt = analogEncoder.voltage
                    synchronized(lock) {
                        latestVoltage = volt
                    }
                } catch (_: Exception) {}
                try {
                    Thread.sleep(5)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
        }
        thread.isDaemon = true
        thread.name = "ARES-SwerveModuleIOFtc-Analog-Thread"
        thread.start()
    }

    override fun updateInputs(inputs: SwerveModuleInputs) {
        // FTC has no synchronous CAN block like Phoenix 6, so we poll individually.
        try {
            lastDrivePosition = driveMotor.currentPosition * 2.0 * Math.PI / 2048.0
        } catch (e: Exception) {
            logError("driveMotor.currentPosition", e)
        }

        try {
            lastDriveVelocity = driveMotor.velocity * 2.0 * Math.PI / 2048.0
        } catch (e: Exception) {
            logError("driveMotor.velocity", e)
        }

        try {
            val volt = synchronized(lock) { latestVoltage }
            lastSteerAbsolute = volt / 3.3 * 2.0 * Math.PI
        } catch (e: Exception) {
            logError("analogEncoder.voltage", e)
        }

        inputs.drivePositionRads = lastDrivePosition
        inputs.driveVelocityRadsPerSec = lastDriveVelocity
        inputs.steerAbsolutePositionRads = lastSteerAbsolute
        inputs.timestampMs = com.areslib.util.RobotClock.currentTimeMillis() // System clock fallback
    }

    private fun logError(feature: String, e: Exception) {
        val now = com.areslib.util.RobotClock.currentTimeMillis()
        if (now - lastWarningTime > 2000L) {
            System.err.println("SwerveModuleIOFtc: Failed to read $feature from hardware. Using last known value. Error: ${e.message}")
            lastWarningTime = now
        }
    }

    fun close() {
        running = false
    }
}

