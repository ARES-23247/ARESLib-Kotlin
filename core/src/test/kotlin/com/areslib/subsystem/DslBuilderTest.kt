package com.areslib.subsystem

import com.areslib.state.RobotState
import com.areslib.state.SuperstructureMode
import com.areslib.state.SuperstructureState
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class DslBuilderTest {

    @Test
    fun `test standard aresRobot builder`() {
        val robot = aresRobot {
            // Default initialization
        }
        
        assertEquals(0.0, robot.store.state.drive.xVelocityMetersPerSecond)
        assertEquals(SuperstructureMode.IDLE, robot.store.state.superstructure.mode)
    }

    @Test
    fun `test aresRobot builder with custom initial state`() {
        val robot = aresRobot {
            initialState = RobotState(
                superstructure = SuperstructureState(
                    flywheelRPM = 1500.0,
                    inventoryCount = 3
                )
            )
        }
        
        assertEquals(1500.0, robot.store.state.superstructure.flywheelRPM)
        assertEquals(3, robot.store.state.superstructure.inventoryCount)
    }

    @Test
    fun `test aresRobot builder with lambda block initial state`() {
        val robot = aresRobot {
            initialState {
                // Since RobotState has read-only fields, the block compiles but doesn't change properties.
                // This validates that the builder's lambda DSL function runs without exception.
            }
        }
        
        assertEquals(0.0, robot.store.state.superstructure.flywheelRPM)
    }
}
