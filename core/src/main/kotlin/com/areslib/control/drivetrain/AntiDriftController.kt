package com.areslib.control.drivetrain

import com.areslib.control.feedback.PIDController
import com.areslib.hardware.drive.OdometryIO
import com.areslib.hardware.drive.OdometryInputs
import com.areslib.math.geometry.ChassisSpeeds
import com.areslib.math.geometry.Pose2d

class AntiDriftController(
    private val baseOdometry: OdometryIO,
    val xPid: PIDController = PIDController(1.5, 0.0, 0.1),
    val yPid: PIDController = PIDController(1.5, 0.0, 0.1)
) : OdometryIO {

    var isCalibrationActive: Boolean = false
        set(value) {
            field = value
            if (value) {
                xPid.reset()
                yPid.reset()
            }
        }
    
    var correctionVx: Double = 0.0
        private set
    var correctionVy: Double = 0.0
        private set

    override fun initialize(startPose: Pose2d) {
        baseOdometry.initialize(startPose)
        xPid.reset()
        yPid.reset()
        correctionVx = 0.0
        correctionVy = 0.0
    }

    override fun updateInputs(inputs: OdometryInputs) {
        baseOdometry.updateInputs(inputs)
        
        if (isCalibrationActive) {
            // Target linear velocity is 0.0 (pin the center of rotation)
            // Feed the negative of the velocity deviation into the PID loops to calculate counter-strafe
            correctionVx = xPid.calculate(inputs.velX, 0.0, 0.00067) // 1500Hz loop time is approx 0.00067s
            correctionVy = yPid.calculate(inputs.velY, 0.0, 0.00067)
        } else {
            correctionVx = 0.0
            correctionVy = 0.0
        }
    }

    /**
     * Applies the calculated anti-drift corrections to the commanded robot-centric velocities.
     */
    fun applyCorrection(original: ChassisSpeeds): ChassisSpeeds {
        if (!isCalibrationActive) return original
        return ChassisSpeeds(
            vxMetersPerSecond = original.vxMetersPerSecond + correctionVx,
            vyMetersPerSecond = original.vyMetersPerSecond + correctionVy,
            omegaRadiansPerSecond = original.omegaRadiansPerSecond
        )
    }
}
