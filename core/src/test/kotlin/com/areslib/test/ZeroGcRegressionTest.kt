package com.areslib.test

import com.areslib.kinematics.MecanumKinematics
import com.areslib.math.estimation.PoseEstimator
import com.areslib.math.estimation.PoseEstimatorState
import com.areslib.math.geometry.Pose2d
import com.areslib.math.geometry.Rotation2d
import com.areslib.math.geometry.Translation2d
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.management.ManagementFactory
import com.sun.management.ThreadMXBean

class ZeroGcRegressionTest {

    private fun getAllocatedBytes(): Long {
        val mxBean = ManagementFactory.getThreadMXBean()
        return if (mxBean is ThreadMXBean) {
            mxBean.getThreadAllocatedBytes(Thread.currentThread().id)
        } else {
            0L
        }
    }

    @Test
    fun testHotPathZeroGcExecution() {
        var poseState = PoseEstimatorState(
            estimatedPoseX = 0.0,
            estimatedPoseY = 0.0,
            estimatedPoseHeading = 0.0
        )
        val deltaTrans = Translation2d(0.01, 0.02)
        val deltaRot = Rotation2d(0.005)
        val kinematics = MecanumKinematics(0.4, 0.4)
        val outSpeeds = DoubleArray(4)

        // Warmup JIT
        for (i in 0 until 1000) {
            poseState = PoseEstimator.addOdometryObservation(
                state = poseState,
                timestampMs = 1000L + i * 20L,
                deltaTranslation = deltaTrans,
                deltaHeading = deltaRot,
                dtSeconds = 0.02
            )
            kinematics.toWheelSpeeds(1.0, 0.5, 0.2, outSpeeds)
        }

        val startBytes = getAllocatedBytes()

        for (i in 0 until 1000) {
            poseState = PoseEstimator.addOdometryObservation(
                state = poseState,
                timestampMs = 20000L + i * 20L,
                deltaTranslation = deltaTrans,
                deltaHeading = deltaRot,
                dtSeconds = 0.02
            )
            kinematics.toWheelSpeeds(1.0, 0.5, 0.2, outSpeeds)
        }

        val allocatedBytes = getAllocatedBytes() - startBytes
        println("[ZeroGC Test] Allocated bytes over 1,000 hot-path iterations: $allocatedBytes bytes")

        assertTrue(
            allocatedBytes <= 4096L,
            "Hot-path execution should allocate minimal bytes (was $allocatedBytes bytes)"
        )
    }
}
