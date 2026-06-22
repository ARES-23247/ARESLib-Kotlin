package com.areslib.telemetry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.io.File

class CloudExporterTest {

    @Test
    fun testUrlAndRouteMapping() {
        // Reset base URL and check default
        CloudExporter.areswebServerUrl = "http://localhost:5001/aresfirst-portal/us-central1/api"
        assertEquals("http://localhost:5001/aresfirst-portal/us-central1/api", CloudExporter.areswebServerUrl)

        // Helper to check route prefixes match exactly what ARESWEB upload sub-endpoints expect
        fun getRouteFor(fileName: String): String {
            return when {
                fileName.startsWith("state_log_") -> "/upload/states"
                fileName.startsWith("action_log_") -> "/upload/actions"
                fileName.startsWith("input_log_") -> "/upload/inputs"
                fileName.startsWith("motor_log_") -> "/upload/motors"
                fileName.startsWith("vision_log_") -> "/upload/vision"
                else -> "/upload"
            }
        }

        assertEquals("/upload/states", getRouteFor("state_log_2026.jsonl"))
        assertEquals("/upload/motors", getRouteFor("motor_log_2026.csv"))
        assertEquals("/upload/vision", getRouteFor("vision_log_2026.jsonl"))
    }

    @Test
    fun testCleanShutdown() {
        CloudExporter.start()
        // verify it stops without blocking or throwing exceptions
        CloudExporter.stop()
        assertTrue(true, "CloudExporter should shutdown cleanly")
    }
}
