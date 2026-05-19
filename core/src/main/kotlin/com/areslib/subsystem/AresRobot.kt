package com.areslib.subsystem

import com.areslib.state.RobotState

open class AresRobot(
    initialState: RobotState = RobotState()
) {
    val store = Store(initialState)

    val drive = DriveSubsystem(store)
    val shooter = ShooterSubsystem(store)
    val intake = IntakeSubsystem(store)
}
