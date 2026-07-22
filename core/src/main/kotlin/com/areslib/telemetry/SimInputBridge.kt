package com.areslib.telemetry

import com.areslib.networktables.NT4Server

/**
 * Thread-safe bridge for virtual driver station / NT4 inputs.
 * Allows simulator tools to inject gamepad stick inputs into OpMode loops
 * without forcing hardware modules to depend on the simulator package.
 */
object SimInputBridge {
    @Volatile var rawWebVx: Double = 0.0
    @Volatile var rawWebVy: Double = 0.0
    @Volatile var rawWebOmega: Double = 0.0

    val webVx: Double
        get() = if (rawWebVx != 0.0) rawWebVx else NT4Server.getDouble("ARES/Input/vx", 0.0)

    val webVy: Double
        get() = if (rawWebVy != 0.0) rawWebVy else NT4Server.getDouble("ARES/Input/vy", 0.0)

    val webOmega: Double
        get() = if (rawWebOmega != 0.0) rawWebOmega else NT4Server.getDouble("ARES/Input/omega", 0.0)
}
