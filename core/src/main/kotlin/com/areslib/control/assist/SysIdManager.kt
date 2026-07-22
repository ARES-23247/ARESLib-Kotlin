package com.areslib.control.assist

import com.areslib.math.wrapAngle
import kotlin.math.sqrt

enum class SysIdMechanism {
    LINEAR,
    ANGULAR,
    FLYWHEEL
}

enum class SysIdRoutine {
    NONE,
    QUASISTATIC,
    DYNAMIC
}

/**
 * Class implementation for Sys Id Manager.
 *
 * Robotics framework control component.
 */
class SysIdManager {
    var activeMechanism = SysIdMechanism.LINEAR
        private set
    var activeRoutine = SysIdRoutine.NONE
        private set
    var startTimeMs = 0L
        private set
    var startX = 0.0
        private set
    var startY = 0.0
        private set
    var startHeading = 0.0
        private set
    
    var currentVoltage = 0.0
        private set
    
    private var lastTimeMs = 0L
    private var lastVelocity = 0.0
    private var lastHeading = 0.0
    
    var accumulatedHeadingChange = 0.0
        private set
    var accumulatedPosition = 0.0
        private set
    var calculatedAcceleration = 0.0
        private set

    fun start(mechanism: SysIdMechanism, routine: SysIdRoutine, timestampMs: Long, x: Double, y: Double, heading: Double) {
        activeMechanism = mechanism
        activeRoutine = routine
        startTimeMs = timestampMs
        startX = x
        startY = y
        startHeading = heading
        currentVoltage = 0.0
        lastTimeMs = timestampMs
        lastVelocity = 0.0
        lastHeading = heading
        accumulatedHeadingChange = 0.0
        accumulatedPosition = 0.0
        calculatedAcceleration = 0.0
    }

    fun stop() {
        activeRoutine = SysIdRoutine.NONE
        currentVoltage = 0.0
    }

    fun isActive(): Boolean = activeRoutine != SysIdRoutine.NONE

    /**
     * Checks if safety limits are violated.
     * Returns true if safe, false if safety limit exceeded (should abort).
     */
    fun checkSafety(x: Double, y: Double, heading: Double, timestampMs: Long): Boolean {
        if (!isActive()) return true
        
        val elapsedSec = (timestampMs - startTimeMs) / 1000.0
        if (elapsedSec > 5.0) {
            return false // Time safety limit
        }

        if (activeMechanism == SysIdMechanism.FLYWHEEL) {
            return true
        }

        if (activeMechanism == SysIdMechanism.LINEAR) {
            val dx = x - startX
            val dy = y - startY
            val dist = sqrt(dx * dx + dy * dy)
            if (dist > 1.5) {
                return false // Distance safety limit
            }
        } else {
            val diff = wrapAngle(heading - lastHeading)
            accumulatedHeadingChange += kotlin.math.abs(diff)
            lastHeading = heading
            if (accumulatedHeadingChange > 4.0 * kotlin.math.PI) {
                return false // Rotation safety limit (2 full rotations)
            }
        }
        return true
    }

    /**
     * Computes the current target voltage and numerical acceleration.
     * @param timestampMs Current timestamp in milliseconds.
     * @param velocity Current measured velocity (linear velocity in m/s, or angular velocity in rad/s).
     */
    fun update(timestampMs: Long, velocity: Double): Double {
        if (!isActive()) return 0.0
        
        val elapsedSec = (timestampMs - startTimeMs) / 1000.0
        val dt = (timestampMs - lastTimeMs) / 1000.0
        
        // Calculate acceleration and integrate position
        if (dt > 1e-4) {
            accumulatedPosition += velocity * dt
            calculatedAcceleration = (velocity - lastVelocity) / dt
        }
        lastTimeMs = timestampMs
        lastVelocity = velocity

        currentVoltage = when (activeRoutine) {
            SysIdRoutine.QUASISTATIC -> {
                if (elapsedSec < 2.5) {
                    1.2 * elapsedSec
                } else {
                    -1.2 * (elapsedSec - 2.5)
                }
            }
            SysIdRoutine.DYNAMIC -> {
                if (elapsedSec < 1.5) {
                    3.0
                } else {
                    -3.0
                }
            }
            else -> 0.0
        }
        
        // Clamp voltage to battery limits
        if (currentVoltage > 12.0) currentVoltage = 12.0
        if (currentVoltage < -12.0) currentVoltage = -12.0
        
        return currentVoltage
    }
}
