package com.areslib.reducer

import com.areslib.action.RobotAction
import com.areslib.state.RobotState
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

class RootReducerTest {

    @Test
    fun `test drive hardware update modifies odometry purely`() {
        val initialState = RobotState()
        
        val action = RobotAction.DriveHardwareUpdate(
            xVelocity = 1.0,
            yVelocity = 0.0,
            angularVelocity = 0.0,
            deltaX = 0.1,
            deltaY = 0.0,
            deltaHeading = 0.0,
            timestampMs = 1000L
        )
        
        val newState = rootReducer(initialState, action)
        
        // Ensure state was copied, not mutated
        assertNotSame(initialState, newState)
        assertNotSame(initialState.drive, newState.drive)
        
        // Verify values
        assertEquals(0.0, initialState.drive.odometryX)
        assertEquals(0.1, newState.drive.odometryX)
        assertEquals(1000L, newState.timestampMs)
    }
}
