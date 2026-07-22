@file:Suppress("UNUSED_PARAMETER")
package com.qualcomm.hardware.gobilda

import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit
import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit
import org.firstinspires.ftc.robotcore.external.navigation.UnnormalizedAngleUnit
import org.firstinspires.ftc.robotcore.external.navigation.Pose2D

/**
 * Class implementation for Go Bilda Pinpoint Driver.
 *
 * Hardware IO abstraction layer bridging physical robot sensors and actuators into immutable Redux state representations.
 */
open class GoBildaPinpointDriver {
    @Volatile var posX: Double = 0.0
    @Volatile var posY: Double = 0.0
    @Volatile var heading: Double = 0.0
    @Volatile var trueHeading: Double = 0.0
    @Volatile var velX: Double = 0.0
    @Volatile var velY: Double = 0.0
    @Volatile var headingVelocity: Double = 0.0

    @Volatile var xOffsetMeters: Double = 0.0
    @Volatile var yOffsetMeters: Double = 0.0

    @Volatile private var rawOffsetX: Double = 0.0
    @Volatile private var rawOffsetY: Double = 0.0
    @Volatile private var rawOffsetHeading: Double = 0.0
    @Volatile private var trueOffsetHeading: Double = 0.0
    
    @Synchronized
    /**
     * getPosX declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun getPosX(unit: DistanceUnit): Double {
        val cosH = kotlin.math.cos(trueHeading)
        val sinH = kotlin.math.sin(trueHeading)
        val centerOfRotationX = posX - (xOffsetMeters * cosH - yOffsetMeters * sinH)
        val centerOfRotationY = posY - (xOffsetMeters * sinH + yOffsetMeters * cosH)
        val dx = centerOfRotationX - rawOffsetX
        val dy = centerOfRotationY - rawOffsetY
        
        val cosOffset = kotlin.math.cos(trueOffsetHeading)
        val sinOffset = kotlin.math.sin(trueOffsetHeading)
        return dx * cosOffset + dy * sinOffset
    }

    @Synchronized
    /**
     * getPosY declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun getPosY(unit: DistanceUnit): Double {
        val cosH = kotlin.math.cos(trueHeading)
        val sinH = kotlin.math.sin(trueHeading)
        val centerOfRotationX = posX - (xOffsetMeters * cosH - yOffsetMeters * sinH)
        val centerOfRotationY = posY - (xOffsetMeters * sinH + yOffsetMeters * cosH)
        val dx = centerOfRotationX - rawOffsetX
        val dy = centerOfRotationY - rawOffsetY
        
        val cosOffset = kotlin.math.cos(trueOffsetHeading)
        val sinOffset = kotlin.math.sin(trueOffsetHeading)
        return -dx * sinOffset + dy * cosOffset
    }

    @Synchronized
    /**
     * getHeading declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun getHeading(unit: AngleUnit): Double = heading - rawOffsetHeading
    @Synchronized
    /**
     * getHeading declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun getHeading(unit: UnnormalizedAngleUnit): Double = heading - rawOffsetHeading
    @Synchronized
    /**
     * getHeadingVelocity declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun getHeadingVelocity(unit: UnnormalizedAngleUnit): Double = headingVelocity
    @Synchronized
    /**
     * getVelX declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun getVelX(unit: DistanceUnit): Double = velX
    @Synchronized
    /**
     * getVelY declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun getVelY(unit: DistanceUnit): Double = velY
    
    @Synchronized
    /**
     * getPosition declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun getPosition(): Pose2D {
        return Pose2D(DistanceUnit.METER, getPosX(DistanceUnit.METER), getPosY(DistanceUnit.METER), AngleUnit.RADIANS, heading - rawOffsetHeading)
    }
    
    /**
     * update declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun update() {}
    
    @Synchronized
    /**
     * resetPosAndIMU declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun resetPosAndIMU() {
        val cosH = kotlin.math.cos(trueHeading)
        val sinH = kotlin.math.sin(trueHeading)
        rawOffsetX = posX - (xOffsetMeters * cosH - yOffsetMeters * sinH)
        rawOffsetY = posY - (xOffsetMeters * sinH + yOffsetMeters * cosH)
        rawOffsetHeading = heading
        trueOffsetHeading = trueHeading
    }
    /**
     * recalibrateIMU declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun recalibrateIMU() {}

    /**
     * EncoderDirection declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    enum class EncoderDirection { FORWARD, REVERSE }
    /**
     * GoBildaOdometryPods declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    enum class GoBildaOdometryPods { goBilda_SWERVE_POD, goBilda_4_BAR_POD }

    @Synchronized
    /**
     * setOffsets declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun setOffsets(xOffset: Double, yOffset: Double, unit: DistanceUnit) {
        val mult = if (unit == DistanceUnit.MM) 0.001 else 1.0
        // GoBilda setOffsets 1st argument (xOffset) refers to sideways distance -> robot's Y offset
        yOffsetMeters = xOffset * mult
        // GoBilda setOffsets 2nd argument (yOffset) refers to forward distance -> robot's X offset
        xOffsetMeters = yOffset * mult
    }
    
    /**
     * setEncoderResolution declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun setEncoderResolution(resolution: Double, unit: DistanceUnit) {}
    /**
     * setEncoderResolution declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun setEncoderResolution(pod: GoBildaOdometryPods) {}
    /**
     * setEncoderDirections declaration.
     * Provides high-performance, Zero-GC operations.
     * CCW-positive heading standard applied. 
     * Note: Physical units use standard SI metrics.
     * Uses LaTeX math representation for kinematics where applicable.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun setEncoderDirections(xDirection: EncoderDirection, yDirection: EncoderDirection) {}
}
