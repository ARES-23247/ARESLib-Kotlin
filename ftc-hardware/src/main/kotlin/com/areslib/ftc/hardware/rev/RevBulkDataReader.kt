package com.areslib.ftc.hardware.rev

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Object implementation for Rev Bulk Data Reader.
 *
 * Hardware IO abstraction layer bridging physical robot sensors and actuators into immutable Redux state representations.
 */
object RevBulkDataReader {
    private val motorsList = CopyOnWriteArrayList<RevMotorController>()
    @Volatile private var pollingRunning = false
    private var pollingThread: Thread? = null

    fun registerMotor(motor: RevMotorController) {
        synchronized(this) {
            motorsList.add(motor)
            startPollingThreadIfNeeded()
        }
    }

    fun unregisterMotor(motor: RevMotorController) {
        synchronized(this) {
            motorsList.remove(motor)
        }
    }

    private fun startPollingThreadIfNeeded() {
        if (pollingRunning) return
        pollingRunning = true
        pollingThread = Thread {
            while (pollingRunning) {
                for (motorInstance in motorsList) {
                    motorInstance.pollCurrentSync()
                }
                try {
                    Thread.sleep(50) // 20Hz
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
        }.apply {
            isDaemon = true
            name = "ARES-MotorCurrent-Thread"
            start()
        }
    }

    fun unregisterAll() {
        synchronized(this) {
            pollingRunning = false
            pollingThread?.interrupt()
            pollingThread = null
            motorsList.clear()
        }
    }
}
