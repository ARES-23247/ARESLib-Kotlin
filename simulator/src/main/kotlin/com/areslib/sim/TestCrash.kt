package com.areslib.sim

import edu.wpi.first.wpilibj.DataLogManager
import edu.wpi.first.networktables.NetworkTableInstance

/**
 * main declaration.
 *
 * @param args Standard arguments (if applicable).
 * @return Corresponding output value or Unit.
 */
fun main() {
    println("Starting test...")
    NetworkTableInstance.getDefault().startServer()
    println("Stopping DataLogManager...")
    DataLogManager.stop()
    println("Starting DataLogManager...")
    DataLogManager.start("logs", "sim_", 0.25)
    println("Done!")
}
