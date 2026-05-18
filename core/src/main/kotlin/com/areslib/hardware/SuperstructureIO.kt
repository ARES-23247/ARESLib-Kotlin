package com.areslib.hardware

/**
 * Pure abstraction for reading and writing to the physical dual-motor Flywheel shooter.
 * De-couples the pure math state engine from the CTRE Phoenix 6 libraries.
 */
interface FlywheelIO {
    /** Sets the target velocity of the flywheel using closed-loop controller on the motor */
    fun setVelocityRpm(rpm: Double)

    /** Sets the applied voltage of the flywheel motors directly (-12.0 to 12.0 volts) */
    fun setAppliedVoltage(volts: Double)

    /** Gets the measured rotational velocity of the flywheel in RPM */
    val velocityRpm: Double
        get() = 0.0

    /** Gets the measured stator current of the flywheel motors in Amperes */
    val currentAmps: Double
        get() = 0.0

    /** Gets the temperature of the master motor in Celsius */
    val tempCelsius: Double
        get() = 0.0
}

/**
 * Pure abstraction for the adjustable angle cowl/hood.
 */
interface CowlIO {
    /** Sets the target absolute position angle in degrees */
    fun setTargetAngle(degrees: Double)

    /** Sets the applied voltage directly (-12.0 to 12.0 volts) */
    fun setAppliedVoltage(volts: Double)

    /** Gets the current absolute angle in degrees */
    val angleDegrees: Double
        get() = 0.0

    /** Gets the stator current draw in Amperes */
    val currentAmps: Double
        get() = 0.0
}

/**
 * Pure abstraction for the deployed pivot-arm intake and active rollers.
 */
interface IntakeIO {
    /** Sets the target absolute angle of the pivot arm in degrees */
    fun setPivotAngle(degrees: Double)

    /** Sets the applied voltage of the pivot motor directly (-12.0 to 12.0 volts) */
    fun setPivotVoltage(volts: Double)

    /** Sets the applied voltage of the intake rollers directly (-12.0 to 12.0 volts) */
    fun setRollerVoltage(volts: Double)

    /** Gets the current absolute angle of the pivot arm in degrees */
    val pivotAngleDegrees: Double
        get() = 0.0

    /** Gets the measured current of the pivot motor in Amperes */
    val pivotCurrentAmps: Double
        get() = 0.0

    /** Gets the measured current of the roller motor in Amperes */
    val rollerCurrentAmps: Double
        get() = 0.0
}

/**
 * Pure abstraction for the transfer/feeder rollers.
 */
interface FeederIO {
    /** Sets the applied voltage of the feeder motor directly (-12.0 to 12.0 volts) */
    fun setAppliedVoltage(volts: Double)

    /** Gets the status of the infrared beam break sensor detecting note presence */
    val isBeamBroken: Boolean
        get() = false

    /** Gets the stator current draw in Amperes */
    val currentAmps: Double
        get() = 0.0
}

/**
 * Pure abstraction for the fast-climber vertical elevator.
 */
interface ClimberIO {
    /** Sets the target extension position in meters */
    fun setTargetExtension(meters: Double)

    /** Sets the applied voltage of the climber motor directly (-12.0 to 12.0 volts) */
    fun setAppliedVoltage(volts: Double)

    /** Gets the current measured extension in meters */
    val extensionMeters: Double
        get() = 0.0

    /** Gets the stator current draw in Amperes */
    val currentAmps: Double
        get() = 0.0
}
