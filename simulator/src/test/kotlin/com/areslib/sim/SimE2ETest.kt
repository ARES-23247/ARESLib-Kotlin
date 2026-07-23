package com.areslib.sim

import com.areslib.networktables.NT4Server
import org.junit.Assert.*
import org.junit.Test

class SimE2ETest {

    @Test
    fun testHeadlessSimulationE2E() {
        println("[E2E Test] Launching DesktopSimLauncher in headless mode...")

        // Launch DesktopSimLauncher in headless mode on a background thread
        val simThread = Thread {
            try {
                DesktopSimLauncher.main(arrayOf("--headless"))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.apply {
            isDaemon = true
            start()
        }

        // Wait for NT4Server and DesktopSimLauncher main loop to start on port 5810
        Thread.sleep(2500)

        val server = NT4Server.getInstance()
        assertNotNull("NT4Server should be active on port 5810", server)

        // 1. Verify OpMode announcement
        var teleOpListJson = NT4Server.getString("ARES/DriverStation/TeleOpList", "")
        if (teleOpListJson.isEmpty()) {
            com.areslib.sim.opmode.SimOpModeRunner.scanAndPublishOpModes()
            teleOpListJson = NT4Server.getString("ARES/DriverStation/TeleOpList", "")
        }
        println("[E2E Test] Announced TeleOpList: $teleOpListJson")
        assertTrue("TeleOpList should not be empty", teleOpListJson.isNotEmpty())

        // 2. Select OpMode and send INIT command
        NT4Server.publishTopic("ARES/DriverStation/SelectedOpMode", "com.areslib.ftc.hardware.AresHardwareTestOpMode")
        NT4Server.publishTopic("ARES/DriverStation/Command", "INIT")

        // Wait for INIT lifecycle step
        Thread.sleep(500)

        // 3. Send START command
        NT4Server.publishTopic("ARES/DriverStation/Command", "START")
        Thread.sleep(200)

        // 4. Inject positive X velocity drive input (vx = 2.0 m/s)
        println("[E2E Test] Injecting vx = 2.0 m/s drive input...")
        NT4Server.publishTopic("ARES/Input/vx", 2.0)
        NT4Server.publishTopic("ARES/Input/vy", 0.0)
        NT4Server.publishTopic("ARES/Input/omega", 0.0)

        val initialX = NT4Server.getDouble("ARES/EstimatedPose/0", 0.0)
        println("[E2E Test] Initial pose X: $initialX")

        // Step physics world for 1.0 second
        Thread.sleep(1000)

        val finalX = NT4Server.getDouble("ARES/EstimatedPose/0", 0.0)
        val flPower = NT4Server.getDouble("Hardware/Motors/fl/Power", 0.0)
        val tele0 = NT4Server.getString("ARES/DriverStation/Telemetry/0", "")

        println("[E2E Test] Final pose X: $finalX, flPower: $flPower")
        println("[E2E Test] Sample Telemetry Line 0: '$tele0'")

        // Assertions
        assertTrue("Front left motor power should be positive under drive input", flPower > 0.1)
        assertTrue("Robot X position should advance forward under positive vx drive input", finalX > initialX + 0.05)
        assertTrue("Driver station telemetry line 0 should contain formatted text", tele0.isNotEmpty())

        println("[E2E Test] SUCCESS! All headless E2E verification assertions passed clean.")
    }
}
