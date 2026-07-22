package com.areslib.ftc.drivetrain

import com.qualcomm.robotcore.hardware.DcMotorEx
import com.qualcomm.robotcore.hardware.AnalogInput
import com.areslib.hardware.drive.SwerveModuleIO
import com.areslib.hardware.drive.SwerveModuleInputs

/**
 * FTC Physical Swerve Module IO Hardware Adapter.
 *
 * Wraps a drive `DcMotorEx`, steer `DcMotorEx`, and absolute `AnalogInput` encoder for an FTC Swerve Pod (e.g. Axon, GoBilda Swerve).
 * Features a dedicated 200Hz background thread (`ARES-SwerveModuleIOFtc-Analog-Thread`) for non-blocking analog voltage sampling.
 *
 * ### Units & Sensor Conversion:
 * - Drive Motor Encoder: Radians ($rad$) using 2048 CPR tick scaling.
 * - Steer Encoder: Radians ($rad$) scaled from 0.0V–3.3V analog absolute angle range.
 * - Drive / Steer Power: Normalized duty-cycle percent ($-1.0$ to $+1.0$).
 *
 * @param driveMotor REV Expansion Hub `DcMotorEx` driving the module wheel.
 * @param steerMotor REV Expansion Hub `DcMotorEx` rotating the module steering pod.
 * @param analogEncoder Absolute analog position sensor (e.g. MA3, Lamprey, Axon encoder).
 */
class SwerveModuleIOFtc(
    private val driveMotor: DcMotorEx,
    private val steerMotor: DcMotorEx,
    private val analogEncoder: AnalogInput
) : SwerveModuleIO, AutoCloseable {

    private var lastDrivePosition = 0.0
    private var lastDriveVelocity = 0.0
    private var lastSteerAbsolute = 0.0
    private var lastWarningTime = 0L

    private val lock = Any()
    @Volatile private var running = true
    private var latestVoltage = 0.0

    init {
        com.areslib.hardware.HardwareRegistry.registerCloseable(this)
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

    /**
     * Polling update cycle reading drive position, drive velocity, and absolute steer angle into [SwerveModuleInputs].
     *
     * @param inputs Telemetry struct populated with current physical sensor values.
     */
    override fun updateInputs(inputs: SwerveModuleInputs) {
        try {
            lastDrivePosition = driveMotor.currentPosition * 2.0 * Math.PI / 2048.0
        } catch (e: Exception) {
            logWarning("Drive position read failed: ${e.message}")
        }

        try {
            lastDriveVelocity = driveMotor.velocity * 2.0 * Math.PI / 2048.0
        } catch (e: Exception) {
            logWarning("Drive velocity read failed: ${e.message}")
        }

        val volt = synchronized(lock) { latestVoltage }
        lastSteerAbsolute = (volt / 3.3) * 2.0 * Math.PI

        inputs.drivePositionRads = lastDrivePosition
        inputs.driveVelocityRadsPerSec = lastDriveVelocity
        inputs.steerAbsolutePositionRads = lastSteerAbsolute
        inputs.timestampMs = com.areslib.util.RobotClock.currentTimeMillis()
    }

    /**
     * Commands motor duty-cycle powers for drive and steer actuators.
     *
     * @param drivePower Normalized drive motor power (-1.0 to 1.0).
     * @param steerPower Normalized steer motor power (-1.0 to 1.0).
     */
    override fun setDesiredPower(drivePower: Double, steerPower: Double) {
        try {
            driveMotor.power = drivePower.coerceIn(-1.0, 1.0)
        } catch (e: Exception) {
            logWarning("Drive setPower failed: ${e.message}")
        }

        try {
            steerMotor.power = steerPower.coerceIn(-1.0, 1.0)
        } catch (e: Exception) {
            logWarning("Steer setPower failed: ${e.message}")
        }
    }

    private fun logWarning(msg: String) {
        val now = System.currentTimeMillis()
        if (now - lastWarningTime > 2000) {
            System.err.println("SwerveModuleIOFtc Warning: $msg")
            lastWarningTime = now
        }
    }

    /**
     * Terminates the analog sampling background thread and unregisters hardware resources.
     */
    override fun close() {
        running = false
    }
}
