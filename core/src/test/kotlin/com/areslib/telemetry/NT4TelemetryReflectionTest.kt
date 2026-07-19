package com.areslib.telemetry

import edu.wpi.first.networktables.DoublePublisher
import edu.wpi.first.networktables.BooleanPublisher
import edu.wpi.first.networktables.StringPublisher
import edu.wpi.first.networktables.DoubleArrayPublisher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NT4TelemetryReflectionTest {

    @Test
    fun testReflectionWpilibTelemetryIntegration() {
        val telemetry = NT4Telemetry()
        
        // 1. Write double telemetry
        telemetry.putNumber("Test/DoubleValue", 42.1)
        assertEquals(42.1, DoublePublisher.lastValues["/Test/DoubleValue"])

        // 2. Write boolean telemetry
        telemetry.putBoolean("Test/BooleanValue", true)
        assertEquals(true, BooleanPublisher.lastValues["/Test/BooleanValue"])

        // 3. Write string telemetry
        telemetry.putString("Test/StringValue", "ARES-Simulation")
        assertEquals("ARES-Simulation", StringPublisher.lastValues["/Test/StringValue"])

        // 4. Write double array telemetry
        val arr = doubleArrayOf(1.0, 2.0, 3.0)
        telemetry.putDoubleArray("Test/ArrayValue", arr)
        val resultArr = DoubleArrayPublisher.lastValues["/Test/ArrayValue"]
        assertTrue(resultArr != null)
        assertEquals(3, resultArr.size)
        assertEquals(1.0, resultArr[0])
        assertEquals(2.0, resultArr[1])
        assertEquals(3.0, resultArr[2])
    }
}
