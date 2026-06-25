package com.areslib.action

data class StartCalibrationSweep(
    val startHeading: Double,
    val cameraIndex: Int,
    override val timestampMs: Long = com.areslib.util.RobotClock.currentTimeMillis()
) : RobotAction

data class CalibrationFrameLogged(
    val gyroHeading: Double,
    val tagId: Int,
    val cameraIndex: Int,
    val cameraToTagTransform: DoubleArray,
    override val timestampMs: Long = com.areslib.util.RobotClock.currentTimeMillis()
) : RobotAction
