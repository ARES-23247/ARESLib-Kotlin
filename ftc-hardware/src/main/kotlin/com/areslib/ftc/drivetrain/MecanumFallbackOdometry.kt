package com.areslib.ftc.drivetrain

import com.areslib.action.RobotAction

/**
 * Manages wheel encoder mecanum forward kinematics pose estimation
 * as a fallback when primary odometry (e.g. GoBilda Pinpoint) is unavailable or offline.
 */
class MecanumFallbackOdometry {
    private var fallbackX = 0.0
    private var fallbackY = 0.0
    private var lastFlPos = 0.0
    private var lastFrPos = 0.0
    private var lastRlPos = 0.0
    private var lastRrPos = 0.0
    private var isFallbackInitialized = false

    /**
     * Calculates field-centric pose update using mecanum forward kinematics on wheel encoders.
     */
    fun getFallbackPoseUpdate(
        timestampMs: Long,
        flPosTicks: Double,
        frPosTicks: Double,
        rlPosTicks: Double,
        rrPosTicks: Double,
        ticksPerMeterSetting: Double,
        defaultTicksPerMeter: Double,
        headingRadians: Double
    ): RobotAction.PoseUpdate {
        val ticks = if (ticksPerMeterSetting > 0.0) ticksPerMeterSetting else defaultTicksPerMeter

        val flMeters = flPosTicks / ticks
        val frMeters = frPosTicks / ticks
        val rlMeters = rlPosTicks / ticks
        val rrMeters = rrPosTicks / ticks

        if (!isFallbackInitialized) {
            lastFlPos = flMeters
            lastFrPos = frMeters
            lastRlPos = rlMeters
            lastRrPos = rrMeters
            isFallbackInitialized = true

            return RobotAction.PoseUpdate(
                xMeters = 0.0,
                yMeters = 0.0,
                headingRadians = headingRadians,
                timestampMs = timestampMs
            )
        }

        val dFl = flMeters - lastFlPos
        val dFr = frMeters - lastFrPos
        val dRl = rlMeters - lastRlPos
        val dRr = rrMeters - lastRrPos

        lastFlPos = flMeters
        lastFrPos = frMeters
        lastRlPos = rlMeters
        lastRrPos = rrMeters

        // Mecanum forward kinematics: robot-centric dx and dy
        val dx = (dFl + dFr + dRl + dRr) / 4.0
        val dy = (-dFl + dFr + dRl - dRr) / 4.0

        // Field-centric rotation transform
        val cos = kotlin.math.cos(headingRadians)
        val sin = kotlin.math.sin(headingRadians)

        val deltaFieldX = dx * cos - dy * sin
        val deltaFieldY = dx * sin + dy * cos

        fallbackX += deltaFieldX
        fallbackY += deltaFieldY

        return RobotAction.PoseUpdate(
            xMeters = fallbackX,
            yMeters = fallbackY,
            headingRadians = headingRadians,
            timestampMs = timestampMs
        )
    }

    /**
     * Resets internal pose tracking to origin.
     */
    fun reset() {
        fallbackX = 0.0
        fallbackY = 0.0
        lastFlPos = 0.0
        lastFrPos = 0.0
        lastRlPos = 0.0
        lastRrPos = 0.0
        isFallbackInitialized = false
    }
}
