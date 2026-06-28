package com.areslib.telemetry

import com.areslib.state.RobotState
import com.areslib.hardware.HardwareRegistry
import com.google.gson.Gson
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class FullStateLogger(
    val runId: String = "",
    val robotId: String = "",
    val matchNumber: Int = 0,
    val alliance: String = "BLUE",
    val config: Config = Config()
) {
    data class Config(
        val logStates: Boolean = true,
        val logMotors: Boolean = true,
        val logVision: Boolean = true,
        val logFrequencyHz: Int = 50
    )

    private val gson = Gson()
    
    // We queue structured payloads to avoid main thread serializing
    private sealed class LogPayload {
        data class State(val state: RobotState) : LogPayload()
        data class Motors(val motors: List<MotorLogEntry>) : LogPayload()
        data class Vision(val visionEvent: VisionEventEntry) : LogPayload()
    }

    data class MotorLogEntry(
        val timestampMs: Long,
        val motorId: String,
        val voltage: Double,
        val current: Double,
        val temp: Double,
        val position: Double,
        val velocity: Double
    )

    data class VisionEventEntry(
        val timestampMs: Long,
        val tagId: Int,
        val cameraId: String,
        val targetPose: com.areslib.math.Pose3d,
        val accepted: Boolean,
        val rejectionReason: String?,
        val covarianceBefore: List<Double>?,
        val covarianceAfter: List<Double>?
    )

    private val queue = LinkedBlockingQueue<LogPayload>(2000)
    
    private var stateWriter: BufferedWriter? = null
    private var motorWriter: BufferedWriter? = null
    private var visionWriter: BufferedWriter? = null
    private var isRunning = false
    private var isMotorHeaderWritten = false

    private val executor = ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS,
        LinkedBlockingQueue(),
        { thread -> Thread(thread, "ARES-FullStateLogger-Thread").apply { isDaemon = true } }
    )

    private var lastStateLogTimeMs = 0L
    private var lastMotorLogTimeMs = 0L
    private val minStateLogIntervalMs = 1000L / config.logFrequencyHz

    // To track vision measurements changes
    private var lastLoggedMeasurementTimestamp = 0L

    init {
        try {
            val javaVendor = System.getProperty("java.vendor") ?: ""
            val isAndroid = javaVendor.contains("Android", ignoreCase = true) || File("/sdcard").exists()

            val logDir = if (isAndroid) File("/sdcard/FIRST/telemetry_logs/") else File("./logs/")
            logDir.mkdirs()

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())

            if (config.logStates) {
                stateWriter = BufferedWriter(FileWriter(File(logDir, "state_log_$timestamp.jsonl")))
            }
            if (config.logMotors) {
                motorWriter = BufferedWriter(FileWriter(File(logDir, "motor_log_$timestamp.csv")))
            }
            if (config.logVision) {
                visionWriter = BufferedWriter(FileWriter(File(logDir, "vision_log_$timestamp.jsonl")))
            }

            isRunning = true
            startLoggingLoop()
        } catch (e: Exception) {
            System.err.println("FullStateLogger: Failed to initialize! ${e.message}")
            isRunning = false
        }
    }

    fun logTick(state: RobotState, batteryVoltage: Double) {
        if (!isRunning) return
        val now = com.areslib.util.RobotClock.currentTimeMillis()

        // 1. Log full state if interval elapsed
        if (config.logStates && (now - lastStateLogTimeMs >= minStateLogIntervalMs)) {
            lastStateLogTimeMs = now
            queue.offer(LogPayload.State(state))
        }
        // 3. Log vision events if there are new measurements
        if (config.logVision && state.vision.measurements.isNotEmpty()) {
            val lastMeasurement = state.vision.measurements.last()
            if (lastMeasurement.timestampMs > lastLoggedMeasurementTimestamp) {
                lastLoggedMeasurementTimestamp = lastMeasurement.timestampMs
                
                val entry = VisionEventEntry(
                    timestampMs = lastMeasurement.timestampMs,
                    tagId = lastMeasurement.tagId,
                    cameraId = "limelight", // default camera_id
                    targetPose = lastMeasurement.targetPose,
                    accepted = state.vision.lastMeasurementAccepted,
                    rejectionReason = state.vision.lastRejectionReason,
                    covarianceBefore = state.vision.covarianceBeforeUpdate,
                    covarianceAfter = state.vision.covarianceAfterUpdate
                )
                queue.offer(LogPayload.Vision(entry))
            }
        }
    }

    fun logMotorsTick(batteryVoltage: Double) {
        if (!isRunning) return
        val now = com.areslib.util.RobotClock.currentTimeMillis()

        // 2. Log motor telemetry at the same rate
        if (config.logMotors && (now - lastMotorLogTimeMs >= minStateLogIntervalMs)) {
            lastMotorLogTimeMs = now
            val motors = HardwareRegistry.getRegisteredMotorsWithNames()
            val entries = ArrayList<MotorLogEntry>(motors.size)
            for ((name, motor) in motors) {
                entries.add(
                    MotorLogEntry(
                        timestampMs = now,
                        motorId = name,
                        voltage = motor.power * batteryVoltage,
                        current = motor.currentAmps,
                        temp = 0.0, // default if not supported
                        position = motor.position,
                        velocity = motor.velocity
                    )
                )
            }
            if (entries.isNotEmpty()) {
                queue.offer(LogPayload.Motors(entries))
            }
        }
    }

    private fun startLoggingLoop() {
        executor.submit {
            while (isRunning || queue.isNotEmpty()) {
                try {
                    val payload = queue.poll(100, TimeUnit.MILLISECONDS) ?: continue
                    when (payload) {
                        is LogPayload.State -> writeState(payload.state)
                        is LogPayload.Motors -> writeMotors(payload.motors)
                        is LogPayload.Vision -> writeVision(payload.visionEvent)
                    }
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                } catch (e: Exception) {
                    System.err.println("FullStateLogger: Error logging: ${e.message}")
                }
            }
            closeWriters()
        }
    }

    private fun writeState(state: RobotState) {
        val w = stateWriter ?: return
        try {
            val stateJson = gson.toJsonTree(state).asJsonObject
            stateJson.addProperty("run_id", runId)
            stateJson.addProperty("robot_id", robotId)
            stateJson.addProperty("match_number", matchNumber)
            stateJson.addProperty("alliance", state.drive.alliance.name)

            w.write(gson.toJson(stateJson))
            w.newLine()
            w.flush()
        } catch (e: IOException) {
            System.err.println("FullStateLogger: Failed to write state JSONL: ${e.message}")
        }
    }

    private fun writeMotors(entries: List<MotorLogEntry>) {
        val w = motorWriter ?: return
        try {
            if (!isMotorHeaderWritten) {
                w.write("run_id,robot_id,timestamp_ms,motor_id,voltage,current,temperature,position,velocity")
                w.newLine()
                isMotorHeaderWritten = true
            }
            for (i in 0 until entries.size) {
                val entry = entries[i]
                w.write("$runId,$robotId,${entry.timestampMs},${entry.motorId},${entry.voltage},${entry.current},${entry.temp},${entry.position},${entry.velocity}")
                w.newLine()
            }
            w.flush()
        } catch (e: IOException) {
            System.err.println("FullStateLogger: Failed to write motor CSV: ${e.message}")
        }
    }

    private fun writeVision(event: VisionEventEntry) {
        val w = visionWriter ?: return
        try {
            val eventJson = gson.toJsonTree(event).asJsonObject
            eventJson.addProperty("run_id", runId)
            eventJson.addProperty("robot_id", robotId)

            w.write(gson.toJson(eventJson))
            w.newLine()
            w.flush()
        } catch (e: IOException) {
            System.err.println("FullStateLogger: Failed to write vision event JSONL: ${e.message}")
        }
    }

    private fun closeWriters() {
        try {
            stateWriter?.close()
        } catch (_: Exception) {}
        try {
            motorWriter?.close()
        } catch (_: Exception) {}
        try {
            visionWriter?.close()
        } catch (_: Exception) {}
    }

    fun stop() {
        isRunning = false
        executor.shutdown()
        try {
            executor.awaitTermination(2, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
}
