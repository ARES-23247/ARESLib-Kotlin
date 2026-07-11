package com.areslib.subsystem

/**
 * A highly simplified, student-facing modular facade for a Mecanum drivetrain subsystem.
 *
 * Inherits all coordinate transformation, heading holding, path following, and drive logic
 * from [HolonomicDriveFacade].
 */
class MecanumDriveFacade @kotlin.jvm.JvmOverloads constructor(
    store: Store,
    headingKp: Double = 4.5,
    headingKi: Double = 0.0,
    headingKd: Double = 0.25,
    headingDeadzoneDeg: Double = 0.5
) : HolonomicDriveFacade(store, headingKp, headingKi, headingKd, headingDeadzoneDeg)
