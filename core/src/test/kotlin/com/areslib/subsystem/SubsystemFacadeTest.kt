package com.areslib.subsystem

import com.areslib.state.RobotState
import com.areslib.state.SuperstructureMode
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class SubsystemFacadeTest {

    @Test
    fun `test drive subsystem facade updates velocity`() {
        val robot = AresRobot()
        
        assertEquals(0.0, robot.drive.xVelocity)
        assertEquals(0.0, robot.drive.yVelocity)
        
        robot.drive.joystickDrive(2.0, -1.0, 0.5)
        
        assertEquals(2.0, robot.drive.xVelocity)
        assertEquals(-1.0, robot.drive.yVelocity)
        assertEquals(0.5, robot.drive.angularVelocity)
    }

    @Test
    fun `test shooter subsystem facade updates state`() {
        val robot = AresRobot()
        
        assertEquals(SuperstructureMode.IDLE, robot.shooter.mode)
        assertEquals(0.0, robot.shooter.flywheelRPM)
        
        robot.shooter.spinUp(3800.0)
        
        assertEquals(3800.0, robot.shooter.flywheelTargetRPM)
        
        // Prepare the state so we can successfully transition to shooting:
        // Set flywheel RPM at speed and load a game piece
        val timestamp = System.currentTimeMillis()
        robot.store.dispatch(com.areslib.action.RobotAction.UpdateFlywheelRPM(4000.0, timestamp))
        robot.store.dispatch(com.areslib.action.RobotAction.SetInventoryCount(1, timestamp))
        
        robot.shooter.shoot()
        assertTrue(robot.shooter.transferActive)
        
        robot.shooter.stop()
        assertFalse(robot.shooter.transferActive)
    }

    @Test
    fun `test intake subsystem facade updates state`() {
        val robot = AresRobot()
        
        assertFalse(robot.intake.isDeployed)
        
        robot.intake.deploy()
        assertTrue(robot.store.state.superstructure.intakeActive)
        assertTrue(robot.intake.isDeployed)
        
        robot.intake.retract()
        assertFalse(robot.store.state.superstructure.intakeActive)
        assertFalse(robot.intake.isDeployed)
    }
}
