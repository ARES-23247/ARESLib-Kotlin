package com.areslib.state

import com.areslib.Store
import com.areslib.action.RobotAction
import com.areslib.math.estimation.HistoryBuffer
import com.areslib.math.estimation.PoseEstimatorState
import com.areslib.math.geometry.Pose3d
import com.areslib.math.geometry.Rotation3d
import com.areslib.math.geometry.Translation3d
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Test

class StoreSnapshotImmutabilityTest {
    @Test
    fun `odometry reductions never mutate or retain estimator arrays from older snapshots`() {
        val initialGain = DoubleArray(9) { index -> index + 0.25 }
        val initialCovariance = DoubleArray(9) { index -> index + 1.0 }
        val initialState = RobotState(
            drive = DriveState(
                poseEstimator = PoseEstimatorState(
                    covarianceArray = initialCovariance.copyOf(),
                    lastKalmanGain = initialGain.copyOf()
                ),
                covarianceMatrix = initialCovariance.copyOf(),
                lastKalmanGain = initialGain.copyOf()
            )
        )
        val store = Store(initialState)
        val retainedInitial = store.state

        store.dispatch(
            RobotAction.PoseUpdate(
                xMeters = 0.0,
                yMeters = 0.0,
                headingRadians = 0.0,
                timestampMs = 1L,
                isReset = true
            )
        )
        val retainedReset = store.state
        val resetEstimatorCovariance = retainedReset.drive.poseEstimator.covarianceArray.copyOf()
        val resetEstimatorGain = retainedReset.drive.poseEstimator.lastKalmanGain.copyOf()
        val resetDriveCovariance = retainedReset.drive.covarianceMatrix.copyOf()
        val resetDriveGain = retainedReset.drive.lastKalmanGain.copyOf()
        val resetHistory = retainedReset.drive.poseEstimator.history.snapshot()

        assertNotSame(retainedInitial.drive.poseEstimator.covarianceArray, retainedReset.drive.poseEstimator.covarianceArray)
        assertNotSame(retainedInitial.drive.poseEstimator.lastKalmanGain, retainedReset.drive.poseEstimator.lastKalmanGain)

        store.dispatch(
            RobotAction.PoseUpdate(
                xMeters = 0.25,
                yMeters = -0.10,
                headingRadians = 0.05,
                timestampMs = 21L
            )
        )
        val latest = store.state

        assertArrayEquals(initialCovariance, retainedInitial.drive.poseEstimator.covarianceArray)
        assertArrayEquals(initialGain, retainedInitial.drive.poseEstimator.lastKalmanGain)
        assertArrayEquals(resetEstimatorCovariance, retainedReset.drive.poseEstimator.covarianceArray)
        assertArrayEquals(resetEstimatorGain, retainedReset.drive.poseEstimator.lastKalmanGain)
        assertArrayEquals(resetDriveCovariance, retainedReset.drive.covarianceMatrix)
        assertArrayEquals(resetDriveGain, retainedReset.drive.lastKalmanGain)
        assertEquals(resetHistory, retainedReset.drive.poseEstimator.history.snapshot())
        assertNotSame(retainedReset.drive.poseEstimator.covarianceArray, latest.drive.poseEstimator.covarianceArray)
        assertNotSame(retainedReset.drive.poseEstimator.history, latest.drive.poseEstimator.history)
        assertNotSame(retainedReset.drive.poseEstimator.lastKalmanGain, latest.drive.poseEstimator.lastKalmanGain)
        assertNotSame(retainedReset.drive.covarianceMatrix, latest.drive.covarianceMatrix)
        assertNotSame(retainedReset.drive.lastKalmanGain, latest.drive.lastKalmanGain)
    }

    @Test
    fun `odometry and delayed vision preserve every retained estimator history`() {
        val store = Store()
        store.dispatch(
            RobotAction.DriveHardwareUpdate(
                xVelocity = 1.0,
                yVelocity = 0.0,
                angularVelocity = 0.0,
                deltaX = 1.0,
                deltaY = 0.0,
                deltaHeading = 0.0,
                timestampMs = 100L
            )
        )
        val retainedAfterFirstOdometry = store.state
        val firstHistory = retainedAfterFirstOdometry.drive.poseEstimator.history.snapshot()

        store.dispatch(
            RobotAction.DriveHardwareUpdate(
                xVelocity = 1.0,
                yVelocity = 0.0,
                angularVelocity = 0.0,
                deltaX = 1.0,
                deltaY = 0.0,
                deltaHeading = 0.0,
                timestampMs = 150L
            )
        )
        val retainedAfterSecondOdometry = store.state
        val secondHistory = retainedAfterSecondOdometry.drive.poseEstimator.history.snapshot()

        assertEquals(firstHistory, retainedAfterFirstOdometry.drive.poseEstimator.history.snapshot())
        assertNotSame(
            retainedAfterFirstOdometry.drive.poseEstimator.history,
            retainedAfterSecondOdometry.drive.poseEstimator.history
        )

        store.dispatch(
            RobotAction.VisionMeasurementsReceived(
                measurements = listOf(
                    VisionMeasurement(
                        timestampMs = 100L,
                        targetPose = Pose3d(
                            Translation3d(1.5, 0.0, 0.0),
                            Rotation3d()
                        ),
                        tagId = 2,
                        ambiguity = 0.01
                    )
                ),
                timestampMs = 170L
            )
        )
        val latest = store.state

        assertEquals(firstHistory, retainedAfterFirstOdometry.drive.poseEstimator.history.snapshot())
        assertEquals(secondHistory, retainedAfterSecondOdometry.drive.poseEstimator.history.snapshot())
        assertNotSame(
            retainedAfterSecondOdometry.drive.poseEstimator.history,
            latest.drive.poseEstimator.history
        )
    }

    private fun HistoryBuffer.snapshot(): List<HistoryEntrySnapshot> =
        List(size) { index ->
            val entry = this[index]
            HistoryEntrySnapshot(
                timestampMs = entry.timestampMs,
                x = entry.x,
                y = entry.y,
                headingRadians = entry.headingRad,
                covariance = doubleArrayOf(
                    entry.covariance.m00,
                    entry.covariance.m01,
                    entry.covariance.m02,
                    entry.covariance.m10,
                    entry.covariance.m11,
                    entry.covariance.m12,
                    entry.covariance.m20,
                    entry.covariance.m21,
                    entry.covariance.m22
                ).toList(),
                qScale = entry.qScale,
                qHeadingScale = entry.qHeadingScale,
                deltaXRobot = entry.deltaXRobot,
                deltaYRobot = entry.deltaYRobot,
                deltaHeadingRadians = entry.deltaHeadingRad,
                hasMotion = entry.hasMotion
            )
        }

    private data class HistoryEntrySnapshot(
        val timestampMs: Long,
        val x: Double,
        val y: Double,
        val headingRadians: Double,
        val covariance: List<Double>,
        val qScale: Double,
        val qHeadingScale: Double,
        val deltaXRobot: Double,
        val deltaYRobot: Double,
        val deltaHeadingRadians: Double,
        val hasMotion: Boolean
    )
}
