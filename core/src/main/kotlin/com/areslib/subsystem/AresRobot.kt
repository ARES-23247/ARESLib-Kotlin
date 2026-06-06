package com.areslib.subsystem

import com.areslib.state.RobotState
import com.areslib.action.RobotAction
import com.areslib.reducer.rootReducer

open class AresRobot(
    initialState: RobotState = RobotState(),
    reducer: (RobotState, RobotAction) -> RobotState = ::rootReducer
) {
    val store = Store(initialState, reducer)

    val drive = DriveSubsystem(store)
    val mecanumDrive = MecanumDriveFacade(store)
    val swerveDrive = SwerveDriveFacade(store)
    val shooter = ShooterSubsystem(store)
    val intake = IntakeSubsystem(store)
}
