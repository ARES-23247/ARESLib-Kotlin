package com.areslib.telemetry

import com.areslib.networktables.NT4Instance
import com.areslib.networktables.NT4Server
import org.junit.jupiter.api.Test

class NT4ServerTest {
    @Test
    fun testServer() {
        val inst = NT4Instance.defaultInstance
        println("Server initially: ${inst.defaultServer}")
        try {
            if (inst.defaultServer == null) {
                inst.startServer("0.0.0.0", 5810)
            }
            println("Server after start: ${inst.defaultServer}")
            NT4Server.publishTopic("Test/Key", 123.45)
            val valOut = NT4Server.getDouble("Test/Key", 0.0)
            assert(valOut == 123.45)
        } catch (e: Exception) {
            println("Exception: ${e.message}")
        }
    }
}
