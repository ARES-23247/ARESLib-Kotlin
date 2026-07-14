package com.areslib.math




/**
 * Safely wraps an angle in radians to the interval [-PI, PI].
 * Guarantees O(1) execution and instantly returns 0.0 on NaN or Infinity inputs.
 */
fun wrapAngle(angleRad: Double): Double {
    if (angleRad.isNaN() || angleRad.isInfinite()) {
        return 0.0 // Return safe default instead of looping infinitely
    }
    val wrapped = (angleRad + Math.PI) % (2.0 * Math.PI)
    return if (wrapped < 0.0) wrapped + Math.PI else wrapped - Math.PI
}
