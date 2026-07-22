package com.areslib.control.assist

import com.areslib.action.CalibrationFrameLogged
import com.areslib.action.StartCalibrationSweep
import com.areslib.math.geometry.ChassisSpeeds
import com.areslib.math.geometry.Pose2d
import com.areslib.math.geometry.Rotation2d
import com.areslib.state.VisionMeasurement
import com.areslib.Store
import com.areslib.telemetry.ARESNetworkStatePublisher
import com.areslib.control.drivetrain.HolonomicDriveController

/**
 * Class implementation for Vision Extrinsic Calibration Controller.
 *
 * Hardware IO abstraction layer bridging physical robot sensors and actuators into immutable Redux state representations.
 */
class VisionExtrinsicCalibrationController(
    private val store: Store,
    private val holonomicDriveController: HolonomicDriveController,
    private val publisher: ARESNetworkStatePublisher,
    private val sweepSpeedRadPerSec: Double = 0.5
) {
    var isActive: Boolean = false
        private set

    var cameraIndex: Int = 0
        private set

    private var accumulatedRotation: Double = 0.0
    private var currentTargetHeading: Double = 0.0

    fun start(cameraIndex: Int, currentHeading: Double) {
        isActive = true
        this.cameraIndex = cameraIndex
        this.accumulatedRotation = 0.0
        this.currentTargetHeading = currentHeading
        store.dispatch(StartCalibrationSweep(currentHeading, cameraIndex))
        publishState(true, currentHeading, -1, cameraIndex, doubleArrayOf(0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0))
    }

    fun update(
        currentPose: Pose2d,
        measurements: List<VisionMeasurement>,
        dtSeconds: Double
    ): ChassisSpeeds {
        if (!isActive) {
            publishState(false, currentPose.heading.radians, -1, cameraIndex, doubleArrayOf(0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0))
            return ChassisSpeeds(0.0, 0.0, 0.0)
        }

        // Increment heading target
        val headingDelta = sweepSpeedRadPerSec * dtSeconds
        currentTargetHeading += headingDelta
        accumulatedRotation += kotlin.math.abs(headingDelta)

        if (accumulatedRotation >= 2.0 * Math.PI) {
            isActive = false
            publishState(false, currentPose.heading.radians, -1, cameraIndex, doubleArrayOf(0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0))
            return ChassisSpeeds(0.0, 0.0, 0.0)
        }

        // Call HolonomicDriveController with translation locked to currentPose (so translation error is 0)
        val speeds = holonomicDriveController.calculate(
            currentPose = currentPose,
            targetPose = currentPose,
            targetVelocityMps = 0.0,
            targetHeading = Rotation2d(currentTargetHeading),
            dtSeconds = dtSeconds
        )

        // Process any detected vision targets for calibration
        for (m in measurements) {
            if (m.tagId != -1) {
                val q = m.robotPoseTargetSpace.rotation.q
                val t = m.robotPoseTargetSpace.translation
                val transformArray = doubleArrayOf(t.x, t.y, t.z, q.w, q.x, q.y, q.z)

                // Dispatch calibration frame logged
                store.dispatch(
                    CalibrationFrameLogged(
                        gyroHeading = currentPose.heading.radians,
                        tagId = m.tagId,
                        cameraIndex = cameraIndex,
                        cameraToTagTransform = transformArray
                    )
                )

                // Publish to NT4 via our network state publisher
                publishState(
                    isActive = true,
                    gyroHeading = currentPose.heading.radians,
                    tagIndex = m.tagId,
                    cameraIndex = cameraIndex,
                    cameraToTag = transformArray
                )
            }
        }

        return speeds
    }

    private fun publishState(
        isActive: Boolean,
        gyroHeading: Double,
        tagIndex: Int,
        cameraIndex: Int,
        cameraToTag: DoubleArray
    ) {
        publisher.publishCalibration(
            isActive = isActive,
            gyroHeading = gyroHeading,
            tagIndex = tagIndex,
            cameraIndex = cameraIndex,
            cameraToTag = cameraToTag
        )
    }
}
