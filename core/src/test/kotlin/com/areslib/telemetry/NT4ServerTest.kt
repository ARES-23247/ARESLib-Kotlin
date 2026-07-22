package com.areslib.telemetry

import org.frcforftc.networktables.NetworkTablesInstance
import org.junit.jupiter.api.Test

/**
 * NT4ServerTest declaration.
 *
 * @param args Standard arguments (if applicable).
 * @return Corresponding output value or Unit.
 */
class NT4ServerTest {
    @Test
    /**
     * testServer declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun testServer() {
        val inst = NetworkTablesInstance.getDefaultInstance()
        println("Server initially: ${inst.server}")
        try {
            inst.startNT4Server("0.0.0.0", 5810)
            println("Server after start: ${inst.server}")
            inst.startNT4Server("0.0.0.0", 5810)
            println("Started again without exception!")
        } catch (e: Exception) {
            println("Exception: ${e.message}")
        }
    }
}
