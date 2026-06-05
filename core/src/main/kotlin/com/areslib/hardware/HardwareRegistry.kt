package com.areslib.hardware

import com.areslib.telemetry.ITelemetry
import java.util.concurrent.ConcurrentHashMap

/**
 * Global registry for automatic hardware device logging and diagnostics.
 * Allows 100% automatic telemetry logging of all registered motors and servos.
 */
object HardwareRegistry {
    private val motors = ConcurrentHashMap<String, MotorIO>()
    private val servos = ConcurrentHashMap<String, ServoIO>()

    /**
     * Registers a motor with a unique diagnostic name.
     */
    fun registerMotor(name: String, motor: MotorIO) {
        motors[name] = motor
    }

    /**
     * Registers a servo with a unique diagnostic name.
     */
    fun registerServo(name: String, servo: ServoIO) {
        servos[name] = servo
    }

    /**
     * Clears all registered devices (useful between OpModes / tests).
     */
    fun clear() {
        motors.clear()
        servos.clear()
    }

    /**
     * Publishes the current state of all registered hardware devices to the telemetry provider.
     */
    fun publishAll(telemetry: ITelemetry) {
        for ((name, motor) in motors) {
            telemetry.putNumber("Hardware/Motors/$name/Power", motor.power * motor.powerScale)
            telemetry.putNumber("Hardware/Motors/$name/Position", motor.position)
            telemetry.putNumber("Hardware/Motors/$name/Velocity", motor.velocity)
            telemetry.putNumber("Hardware/Motors/$name/CurrentAmps", motor.currentAmps)
        }

        for ((name, servo) in servos) {
            telemetry.putNumber("Hardware/Servos/$name/Position", servo.position)
        }
    }
}
