package com.areslib.subsystem

/**
 * A highly simplified, student-facing modular facade for a Mecanum drivetrain subsystem.
 *
 * Inherits all coordinate transformation, heading holding, path following, and drive logic
 * from [HolonomicDriveFacade].
 */
class MecanumDriveFacade(store: Store) : HolonomicDriveFacade(store)
