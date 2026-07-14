package com.areslib.state

/**
 * Immutable Redux state holding all dynamically tunable constants for the robot.
 * Values can be updated via the dashboard and dispatched as an [UpdateTuningState] action.
 */
data class TuningState(
    // Drivetrain Kinematics
    val trackWidthMeters: Double = 0.45,
    val wheelBaseMeters: Double = 0.45,

    // Drivetrain Path-following Translation PID
    val pathTranslationKp: Double = 2.0,
    val pathTranslationKi: Double = 0.0,
    val pathTranslationKd: Double = 0.02,

    // Drivetrain Path-following Rotation PID
    val pathRotationKp: Double = 2.5,
    val pathRotationKi: Double = 0.0,
    val pathRotationKd: Double = 0.05,

    // Drive Feedback (Heading Lock PID)
    val headingKp: Double = 4.5,
    val headingKi: Double = 0.0,
    val headingKd: Double = 0.25,
    val headingDeadzoneDeg: Double = 0.5,

    // Drivetrain Feedforward & Slew Acceleration Limits
    val driveKs: Double = 0.0,
    val driveSlewRateLimit: Double? = null,

    // Motor Velocity Closed-Loop PIDF (Qualcomm DcMotorEx SDK level)
    val motorKp: Double? = null,
    val motorKi: Double? = null,
    val motorKd: Double? = null,
    val motorKf: Double? = null,

    // Vision Filtering baseline standard deviations
    val visionStdDevsX: Double = 0.05,
    val visionStdDevsY: Double = 0.05,
    val visionStdDevsHeading: Double = 0.1,

    // Vision Outlier Rejection Thresholds
    val visionMaxDistanceMeters: Double = 6.0,
    val visionMaxAmbiguity: Double = 0.2,
    val visionMahalanobisThreshold: Double = 12.0
)
