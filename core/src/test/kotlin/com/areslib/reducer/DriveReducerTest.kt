package com.areslib.reducer

import com.areslib.action.RobotAction
import com.areslib.state.RobotState
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

/**
 * DriveReducerTest declaration.
 *
 * @param args Standard arguments (if applicable).
 * @return Corresponding output value or Unit.
 */
class DriveReducerTest {

    @Test
    fun `test drive hardware update`() {
        val initialState = RobotState()
        
        val action = RobotAction.DriveHardwareUpdate(
            xVelocity = 1.2,
            yVelocity = 0.5,
            angularVelocity = 0.2,
            deltaX = 0.05,
            deltaY = 0.02,
            deltaHeading = 0.01,
            timestampMs = 2000L
        )
        
        val newState = rootReducer(initialState, action)
        
        assertNotSame(initialState, newState)
        assertEquals(0.05, newState.drive.odometryX)
        assertEquals(0.02, newState.drive.odometryY)
        assertEquals(0.01, newState.drive.odometryHeading)
        assertEquals(2000L, newState.timestampMs)
    }

    @Test
    fun `test pose update without reset`() {
        val initialState = RobotState()
        
        val action = RobotAction.PoseUpdate(
            xMeters = 1.0,
            yMeters = 2.0,
            headingRadians = 0.5,
            timestampMs = 2050L,
            isReset = false
        )
        
        val newState = rootReducer(initialState, action)
        
        assertEquals(1.0, newState.drive.poseEstimator.estimatedPose.x)
        assertEquals(2.0, newState.drive.poseEstimator.estimatedPose.y)
        assertEquals(0.5, newState.drive.poseEstimator.estimatedPose.heading.radians)
        assertEquals(2050L, newState.timestampMs)
    }
}
