package com.areslib.ftc.core

import com.qualcomm.robotcore.hardware.HardwareMap

class FtcOpModeLifecycleController {

    fun init(hardwareMap: HardwareMap) {
        com.areslib.ftc.hardware.FtcPerformanceManager.initialize(hardwareMap)
        com.areslib.telemetry.RobotWebServer.start()
        com.areslib.logging.LogManagerServer.startServer()
        com.areslib.telemetry.RobotStatusTracker.isEnabled = false
        com.areslib.telemetry.RobotStatusTracker.activeOpMode = "Init"
    }

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

    fun close() {
        com.areslib.telemetry.RobotStatusTracker.isEnabled = false
        com.areslib.telemetry.RobotWebServer.stop()
    }
}
