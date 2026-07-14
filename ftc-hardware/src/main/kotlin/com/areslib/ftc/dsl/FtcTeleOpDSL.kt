package com.areslib.ftc.dsl

import com.areslib.telemetry.AresGamepad
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode
import com.areslib.telemetry.GamepadState
import org.firstinspires.ftc.robotcore.external.Telemetry
import com.areslib.ftc.util.update

/**
 * Generic DSL Builder class for constructing a TeleOp.
 * @param R The type of the Robot facade.
 */
class FtcTeleOpBuilder<R> {
    internal var onInitBlock: ((R, Telemetry) -> Unit)? = null
    internal var onConfigureBlock: ((R, AresGamepad) -> Unit)? = null
    internal var onLoopBlock: ((R, AresGamepad, Telemetry) -> Unit)? = null

    fun onInit(block: (robot: R, telemetry: Telemetry) -> Unit) {
        onInitBlock = block
    }
    
    fun onConfigure(block: (robot: R, driver: AresGamepad) -> Unit) {
        onConfigureBlock = block
    }

    fun onLoop(block: (robot: R, driver: AresGamepad, telemetry: Telemetry) -> Unit) {
        onLoopBlock = block
    }
}

/**
 * Generic Base class for declarative student OpModes.
 * Manages lifecycle loops, proxy starting, log uploading, Redux loops, and telemetry flushing.
 */
abstract class FtcTeleOpBase<R> : LinearOpMode() {
    /**
     * Define the DSL layout for this OpMode.
     */
    abstract fun define(): FtcTeleOpBuilder<R>

    /**
     * Provide a method to build your team's specific robot wrapper.
     */
    abstract fun buildRobot(): R

    /**
     * Define how your team's robot handles periodic updates during the loop.
     */
    abstract fun updateRobot(robot: R, g1: GamepadState, g2: GamepadState)
    
    /**
     * Define how your team's robot handles shutdown sequences.
     */
    abstract fun closeRobot(robot: R)

    override fun runOpMode() {
        val builder = define()
        
        try {
            com.areslib.ftc.photon.AresPhotonCore.enable()
        } catch(e: Exception) {
            // Ignore in simulation
        }
        
        // Configure the EKF with the tag positions of the selected field layout
        com.areslib.math.PoseEstimator.activeTags = com.areslib.math.FieldLayouts.getTagsForLayout(com.areslib.math.FieldLayout.SQUARE_STANDARD)

        val robot = buildRobot()

        val driver = AresGamepad()
        driver.leftStick.label("Field-centric Translation (X/Y)")
        driver.rightStickX.label("Robot Rotation")
        driver.y.label("Reset Field Centric Pose")
        driver.x.label("Drive to TestWaypoint")

        builder.onConfigureBlock?.invoke(robot, driver)

        try {
            while (opModeInInit() && !Thread.currentThread().isInterrupted) {
                // Initial update pass with empty gamepad state
                updateRobot(robot, GamepadState(), GamepadState())
                builder.onInitBlock?.invoke(robot, telemetry)
                telemetry.update()
                sleep(20)
            }
            if (isStopRequested || Thread.currentThread().isInterrupted) return

            com.areslib.telemetry.RobotStatusTracker.activeOpMode = "TeleOp"

            // NOTE: Hardware specific init code (like vision tracker flags) should be handled by the team's buildRobot/wrapper logic
            com.areslib.ftc.telemetry.LimelightProxyAutoStart.stop()
            
            val g1State = GamepadState()
            val g2State = GamepadState()
            
            while (opModeIsActive() && !Thread.currentThread().isInterrupted) {
                g1State.update(gamepad1)
                g2State.update(gamepad2)
                
                driver.update(g1State)
                
                // Allow the user DSL loop to dispatch inputs
                builder.onLoopBlock?.invoke(robot, driver, telemetry)
                
                // Update physical hardware and sensors via the provided robot interface
                updateRobot(robot, g1State, g2State)
            }
        } finally {
            closeRobot(robot)
            try {
                com.areslib.ftc.photon.AresPhotonCore.disable()
            } catch (e: Exception) {}
        }
    }
}
