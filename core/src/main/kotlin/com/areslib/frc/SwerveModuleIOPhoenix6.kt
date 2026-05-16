package com.areslib.frc

import com.ctre.phoenix6.hardware.TalonFX
import com.ctre.phoenix6.BaseStatusSignal
import com.areslib.action.RobotAction

class SwerveModuleIOPhoenix6(
    private val driveMotor: TalonFX,
    private val steerMotor: TalonFX,
    private val storeDispatch: (RobotAction) -> Unit
) : SwerveModuleIO {

    private val drivePosition = driveMotor.position
    private val driveVelocity = driveMotor.velocity
    private val steerPosition = steerMotor.position

    override fun updateInputs(inputs: SwerveInputs) {
        // FRC CANivore "Airlock" - block until all signals are perfectly synchronized
        BaseStatusSignal.waitForAll(20.0, drivePosition, driveVelocity, steerPosition)
        
        // Populate inputs mutably locally just for transport
        inputs.drivePositionRads = drivePosition.valueAsDouble
        inputs.driveVelocityRadsPerSec = driveVelocity.valueAsDouble
        inputs.steerAbsolutePositionRads = steerPosition.valueAsDouble
        
        // This timestamp is perfectly synced across all devices on the CANivore
        inputs.timestampMs = (drivePosition.timestamp.time * 1000).toLong()
    }
    
    fun dispatchHardwareUpdate() {
        val inputs = SwerveInputs()
        updateInputs(inputs)
        
        // Convert to immutable action and dispatch to central store
        // (Simplified action for demonstration)
        val action = RobotAction.DriveHardwareUpdate(
            xVelocity = 0.0, 
            yVelocity = 0.0,
            angularVelocity = 0.0,
            deltaX = inputs.driveVelocityRadsPerSec * 0.02, // mock delta
            deltaY = 0.0,
            deltaHeading = inputs.steerAbsolutePositionRads,
            timestampMs = inputs.timestampMs
        )
        storeDispatch(action)
    }
}
