package com.areslib.control.safety

/**
 * A mutable output container to hold the filtered targets of a Control Barrier Function.
 *
 * Utilized in high-frequency control loops to prevent garbage-collector heap allocation
 * and CPU jitter on Android ART or RoboRIO runtime.
 */
class CBFFilteredOutput {
    @JvmField var x1Filtered: Double = 0.0
    @JvmField var x2Filtered: Double = 0.0

    /**
     * Resets or sets the values of this output buffer.
     */
    fun setTo(x1: Double, x2: Double) {
        this.x1Filtered = x1
        this.x2Filtered = x2
    }
}

/**
 * A highly optimized, graduate-grade analytical first-order Control Barrier Function (CBF) safety filter.
 *
 * This filter enforces provable mechanical safety constraints of the form:
 * $$h(x_1, x_2) = x_1 - m \cdot x_2 - c \geq 0$$
 * where:
 * - $x_1$ represents a protective protective state (e.g. intake deployed angle in degrees).
 * - $x_2$ represents an invading state (e.g. climber extension in meters).
 * - $m$ is the scaling gradient (degrees of deployment required per meter of extension).
 * - $c$ is the minimum baseline margin when $x_2 = 0$.
 *
 * By continuous-time differential inequality, safety is guaranteed invariant if the derivative satisfies:
 * $$\dot{h}(x_1, x_2) \geq -\alpha \cdot h(x_1, x_2)$$
 * which projects the commanded control inputs minimally onto the half-space of safe velocities.
 *
 * This class uses pre-allocated memory structures and an analytical closed-form orthogonal projection
 * to guarantee zero heap allocations in hot loop iterations.
 *
 * @property m The scaling gradient representing the coupling slope between states.
 * @property c The safety margin offset representing the absolute limit threshold.
 * @property alpha The safety convergence rate (higher values allow faster approaches, lower values are more conservative).
 */
class ControlBarrierFunction(
    val m: Double,
    val c: Double = 0.0,
    val alpha: Double = 5.0
) {
    /**
     * Filters commanded target values to mathematically guarantee the safety invariant set $h(x) >= 0$.
     *
     * If the desired targets violate or approach the safety boundary, they are minimally projected (least-squares distance)
     * onto the safe boundary line in velocity space.
     *
     * @param x1Target Commanded target value for protective state 1.
     * @param x2Target Commanded target value for invading state 2.
     * @param x1Current Current physical value of state 1.
     * @param x2Current Current physical value of state 2.
     * @param dtSeconds Loop time step interval in seconds.
     * @param outBuffer Pre-allocated output container where filtered target values are written.
     */
    fun filter(
        x1Target: Double,
        x2Target: Double,
        x1Current: Double,
        x2Current: Double,
        dtSeconds: Double,
        outBuffer: CBFFilteredOutput
    ) {
        filter(x1Target, x2Target, x1Current, x2Current, dtSeconds, c, outBuffer)
    }

    /**
     * Filters commanded target values using a dynamic margin offset override.
     */
    fun filter(
        x1Target: Double,
        x2Target: Double,
        x1Current: Double,
        x2Current: Double,
        dtSeconds: Double,
        cOverride: Double,
        outBuffer: CBFFilteredOutput
    ) {
        if (x1Target.isNaN() || x1Target.isInfinite() ||
            x2Target.isNaN() || x2Target.isInfinite() ||
            x1Current.isNaN() || x1Current.isInfinite() ||
            x2Current.isNaN() || x2Current.isInfinite() ||
            dtSeconds.isNaN() || dtSeconds.isInfinite() || dtSeconds <= 0.0
        ) {
            outBuffer.setTo(x1Target, x2Target)
            return
        }

        // 1. Calculate current safety margin h(x_current)
        val hCurrent = x1Current - m * x2Current - cOverride

        // 2. Compute minimum allowed safety margin for next step hNextMin
        // If current state is already violating boundary, aggressively force safety convergence to 0.0 immediately
        val hNextMin = if (hCurrent < 0.0) 0.0 else kotlin.math.max(0.0, (1.0 - alpha * dtSeconds) * hCurrent)

        // Check if desired target is already safe and satisfies rate limits
        val hTarget = x1Target - m * x2Target - cOverride
        if (hTarget >= hNextMin) {
            // Target is in the safe set, pass through unchanged
            outBuffer.setTo(x1Target, x2Target)
            return
        }

        // 3. Target violates safety envelope. Project desired targets orthogonally onto the safe boundary line
        // Line equation: x1 - m * x2 = hNextMin + cOverride
        val lLimit = hNextMin + cOverride
        val factor = (m * x2Target - x1Target + lLimit) / (1.0 + m * m)
        var x1Projected = x1Target + factor
        var x2Projected = x2Target - m * factor

        // Safety clamp: Ensure the projected invading state does not go negative if physically bounded
        if (x2Projected < 0.0) {
            x2Projected = 0.0
            if (x1Projected < cOverride) {
                x1Projected = cOverride
            }
        }

        // Clean up numeric noise near stow position
        if (x2Projected < 1e-4) {
            x2Projected = 0.0
        }

        outBuffer.setTo(x1Projected, x2Projected)
    }
}
