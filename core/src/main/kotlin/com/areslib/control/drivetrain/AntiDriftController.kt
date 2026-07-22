package com.areslib.control.drivetrain

import com.areslib.control.feedback.PIDController
import com.areslib.hardware.drive.OdometryIO
import com.areslib.hardware.drive.OdometryInputs
import com.areslib.math.geometry.ChassisSpeeds
import com.areslib.math.geometry.Pose2d

/**
 * Closed-loop active anti-drift controller for high-frequency odometry calibration (e.g. Pinpoint/IMU calibration).
 *
 * During active calibration spins, robot-centric linear velocities ($v_x, v_y$) should theoretically be zero.
 * Any non-zero linear velocity measured during a pure spin represents mechanical scrub or off-center odometry mounting.
 * This controller feeds velocity errors into PID loops to generate counter-strafe corrections ($v_{x,corr}, v_{y,corr}$).
 *
 * ### Physical Units & Coordinates:
 * - Linear Velocity: Meters per second ($m/s$)
 * - Angular Velocity: Radians per second ($rad/s$), counter-clockwise positive
 * - Time step: ~1500Hz loop time ($0.00067s$)
 *
 * @param baseOdometry Underlying odometry IO sensor instance.
 * @param xPid PID controller for X-axis (forward/backward) velocity drift correction.
 * @param yPid PID controller for Y-axis (strafe) velocity drift correction.
 */
/**
 * Class implementation for Anti Drift Controller.
 *
 * Hardware IO abstraction layer bridging physical robot sensors and actuators into immutable Redux state representations.
 */
class AntiDriftController(
    private val baseOdometry: OdometryIO,
    val xPid: PIDController = PIDController(1.5, 0.0, 0.1),
    val yPid: PIDController = PIDController(1.5, 0.0, 0.1)
) : OdometryIO {

    /**
     * Enables active anti-drift correction when set to true. Resets PID loop accumulators on state activation.
     */
    var isCalibrationActive: Boolean = false
        set(value) {
            field = value
            if (value) {
                xPid.reset()
                yPid.reset()
            }
        }

    /** Active counter-strafe correction velocity along X axis (m/s). */
    var correctionVx: Double = 0.0
        private set

    /** Active counter-strafe correction velocity along Y axis (m/s). */
    var correctionVy: Double = 0.0
        private set

    /**
     * initialize declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun initialize(startPose: Pose2d) {
        baseOdometry.initialize(startPose)
        xPid.reset()
        yPid.reset()
        correctionVx = 0.0
        correctionVy = 0.0
    }

    /**
     * updateInputs declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun updateInputs(inputs: OdometryInputs) {
        baseOdometry.updateInputs(inputs)

        if (isCalibrationActive) {
            // Target linear velocity is 0.0 (pin the center of rotation)
            // Feed the negative of the velocity deviation into the PID loops to calculate counter-strafe
            correctionVx = xPid.calculate(inputs.velX, 0.0, 0.00067)
            correctionVy = yPid.calculate(inputs.velY, 0.0, 0.00067)
        } else {
            correctionVx = 0.0
            correctionVy = 0.0
        }
    }

    /**
     * Applies the calculated anti-drift corrections to the commanded robot-centric velocities.
     *
     * @param original Desired robot-centric chassis speeds.
     * @return Corrected [ChassisSpeeds] with counter-strafe velocity offsets applied.
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
