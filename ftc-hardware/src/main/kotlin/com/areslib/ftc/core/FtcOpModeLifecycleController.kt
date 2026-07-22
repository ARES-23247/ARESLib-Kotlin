package com.areslib.ftc.core

import com.qualcomm.robotcore.hardware.HardwareMap

/**
 * Controller managing FTC OpMode lifecycle transitions, web server initialization, and loop rate throttling.
 *
 * Handles transition state tracking ("Init", "TeleOp", "Autonomous"), HTTP telemetry server startup, and desktop simulation sleep timing.
 */
class FtcOpModeLifecycleController {

    /**
     * Initializes performance managers, starts local HTTP web/log servers, and sets initial OpMode status to "Init".
     *
     * @param hardwareMap FTC OpMode hardware map instance.
     */
    fun init(hardwareMap: HardwareMap) {
        com.areslib.ftc.hardware.FtcPerformanceManager.initialize(hardwareMap)
        com.areslib.telemetry.RobotWebServer.start()
        com.areslib.logging.LogManagerServer.startServer()
        com.areslib.telemetry.RobotStatusTracker.isEnabled = false
        com.areslib.telemetry.RobotStatusTracker.activeOpMode = "Init"
    }

    /**
     * Updates OpMode status tracker flags and manages background web server lifecycle transitions.
     */
    fun update() {
        if (!com.areslib.telemetry.RobotStatusTracker.isEnabled && com.areslib.telemetry.RobotStatusTracker.activeOpMode != "Init") {
            com.areslib.telemetry.RobotWebServer.start()
        } else if (!com.areslib.telemetry.RobotStatusTracker.isEnabled) {
            com.areslib.telemetry.RobotWebServer.stop()
        }

        if (com.areslib.telemetry.RobotStatusTracker.activeOpMode != "Init") {
            com.areslib.telemetry.RobotStatusTracker.isEnabled = true
        }
    }

    /**
     * Throttles desktop simulation loop cycles to match target 50Hz (20ms) loop pacing.
     *
     * @param lastUpdateTime Timestamp of previous loop cycle start (ms).
     * @param isAndroid True when executing on physical Android Control Hub.
     */
    fun sleepForTargetDt(lastUpdateTime: Long, isAndroid: Boolean) {
        if (!isAndroid && lastUpdateTime != 0L) {
            val now = com.areslib.util.RobotClock.currentTimeMillis()
            val elapsed = now - lastUpdateTime
            if (elapsed < 20) {
                try {
                    Thread.sleep(20 - elapsed)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        }
    }

    /**
     * Sleeps for remaining loop time on desktop simulations to enforce 50Hz execution timing.
     *
     * @param timestamp Cycle start timestamp (ms).
     * @param isAndroid True when executing on physical Android Control Hub.
     */
    fun sleepRemaining(timestamp: Long, isAndroid: Boolean) {
        if (!isAndroid) {
            val elapsed = com.areslib.util.RobotClock.currentTimeMillis() - timestamp
            val sleepTime = 20L - elapsed
            if (sleepTime > 0) {
                try {
                    Thread.sleep(sleepTime)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        }
    }

    /**
     * Stops web servers and resets lifecycle tracking flags.
     */
    fun close() {
        com.areslib.telemetry.RobotStatusTracker.isEnabled = false
        com.areslib.telemetry.RobotWebServer.stop()
    }
}
