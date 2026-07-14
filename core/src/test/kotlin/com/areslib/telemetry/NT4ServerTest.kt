package com.areslib.telemetry

import org.frcforftc.networktables.NetworkTablesInstance
import org.junit.jupiter.api.Test

class NT4ServerTest {
    @Test
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
