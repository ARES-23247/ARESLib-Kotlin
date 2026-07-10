package com.areslib.telemetry

/**
 * Platform-agnostic telemetry interface used by the functional core to publish
 * variables, tuning parameters, and robot state.
 */
interface ITelemetry {
    fun putNumber(key: String, value: Double)
    fun putBoolean(key: String, value: Boolean)
    fun putString(key: String, value: String)
    fun putDoubleArray(key: String, value: DoubleArray)
    
    fun getNumber(key: String, defaultValue: Double): Double
    fun getBoolean(key: String, defaultValue: Boolean): Boolean
    fun getString(key: String, defaultValue: String): String
    
    /**
     * Optional method to process periodic updates.
     */
    fun update() {}
}

/**
 * Extension to log drive motor telemetry using the exact ARES drive key conventions.
 */
fun ITelemetry.logDriveMotor(name: String, motor: com.areslib.hardware.MotorIO) {
    putNumber("Drive/MotorPower_$name", motor.power * motor.powerScale)
    putNumber("Drive/MotorEncoder_$name", motor.position)
    putNumber("Drive/MotorVelocity_$name", motor.velocity)
    putNumber("Drive/MotorCurrent_$name", motor.currentAmps)
}

/**
 * Extension to log a 2D pose with format formatting.
 */
fun ITelemetry.logPose2d(prefix: String, pose: com.areslib.math.Pose2d, useUnderscores: Boolean = false, lowercase: Boolean = false) {
    val sep = if (useUnderscores) "_" else "/"
    val xStr = if (lowercase) "x" else "X"
    val yStr = if (lowercase) "y" else "Y"
    val hStr = if (lowercase) "heading" else "Heading"
    putNumber("$prefix$sep$xStr", pose.x)
    putNumber("$prefix$sep$yStr", pose.y)
    putNumber("$prefix$sep$hStr", pose.heading.radians)
}

/**
 * Extension to log a 2D pose as a flat double array of [x, y, headingRad].
 */
fun ITelemetry.logPoseArray2d(key: String, pose: com.areslib.math.Pose2d) {
    putDoubleArray(key, doubleArrayOf(pose.x, pose.y, pose.heading.radians))
}

/**
 * Extension to log a 3D pose array (for AdvantageScope) from x, y, and heading.
 */
fun ITelemetry.logPose3d(key: String, x: Double, y: Double, headingRad: Double) {
    val halfH = headingRad / 2.0
    putDoubleArray(key, doubleArrayOf(
        x,
        y,
        0.0,
        Math.cos(halfH),
        0.0,
        0.0,
        Math.sin(halfH)
    ))
}

/**
 * Extension to log a 3D pose array (for AdvantageScope) from a Pose2d.
 */
fun ITelemetry.logPose3d(key: String, pose: com.areslib.math.Pose2d) {
    logPose3d(key, pose.x, pose.y, pose.heading.radians)
}

/**
 * Extension to log brownout guard state and diagnostics.
 */
fun ITelemetry.logBrownout(brownoutGuard: com.areslib.control.BrownoutGuard, batteryVoltage: Double) {
    putNumber("Robot/BatteryVoltage", batteryVoltage)
    putNumber("Robot/BrownoutPowerScale", brownoutGuard.powerScale)
    putString("Robot/BrownoutState", brownoutGuard.state.name)
    putNumber("Robot/BatteryPercent", brownoutGuard.batteryPercent)
    putNumber("Diagnostics/Power/BrownoutCount", brownoutGuard.tripCount.toDouble())
}

/**
 * Extension to log gamepad state without code duplication.
 */
fun ITelemetry.logGamepad(prefix: String, gamepad: GamepadState) {
    putNumber("$prefix/LeftStick_X", gamepad.leftStickX.toDouble())
    putNumber("$prefix/LeftStick_Y", gamepad.leftStickY.toDouble())
    putNumber("$prefix/RightStick_X", gamepad.rightStickX.toDouble())
    putNumber("$prefix/RightStick_Y", gamepad.rightStickY.toDouble())
    putNumber("$prefix/LeftTrigger", gamepad.leftTrigger.toDouble())
    putNumber("$prefix/RightTrigger", gamepad.rightTrigger.toDouble())
    putBoolean("$prefix/A", gamepad.a)
    putBoolean("$prefix/B", gamepad.b)
    putBoolean("$prefix/X", gamepad.x)
    putBoolean("$prefix/Y", gamepad.y)
    putBoolean("$prefix/DpadUp", gamepad.dpadUp)
    putBoolean("$prefix/DpadDown", gamepad.dpadDown)
    putBoolean("$prefix/DpadLeft", gamepad.dpadLeft)
    putBoolean("$prefix/DpadRight", gamepad.dpadRight)
    putBoolean("$prefix/LeftBumper", gamepad.leftBumper)
    putBoolean("$prefix/RightBumper", gamepad.rightBumper)
}

/**
 * Extension to log FRC CANbus status diagnostics.
 */
fun ITelemetry.logCanBusStatus(
    busName: String,
    busUtilization: Double,
    errorCount: Int,
    txErrors: Int,
    rxErrors: Int,
    busOffs: Int,
    signalLatencyMs: Double
) {
    putNumber("Diagnostics/CANBus/$busName/Utilization", busUtilization)
    putNumber("Diagnostics/CANBus/$busName/ErrorCount", errorCount.toDouble())
    putNumber("Diagnostics/CANBus/$busName/TxErrors", txErrors.toDouble())
    putNumber("Diagnostics/CANBus/$busName/RxErrors", rxErrors.toDouble())
    putNumber("Diagnostics/CANBus/$busName/BusOffCount", busOffs.toDouble())
    putNumber("Diagnostics/CANBus/$busName/SignalLatencyMs", signalLatencyMs)
}
