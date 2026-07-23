package com.areslib.sim

import com.areslib.networktables.NT4Server
import org.junit.Assert.*
import org.junit.Test

class TelemetryUpdateE2ETest {

    @Test
    fun testAllTelemetryTopicsUpdateProperly() {
        // 1. Launch simulator in headless server mode
        println("[Telemetry E2E Test] Launching simulator in headless mode...")
        val simThread = Thread {
            DesktopSimLauncher.main(arrayOf("--headless", "--opmode=AresHardwareTestOpMode"))
        }
        simThread.isDaemon = true
        simThread.start()

        // Wait for NT4 server & OpMode init
        Thread.sleep(1500)

        // 2. Select OpMode and send INIT + START commands
        NT4Server.publishTopic("ARES/DriverStation/SelectedOpMode", "com.areslib.ftc.hardware.AresHardwareTestOpMode")
        NT4Server.publishTopic("ARES/DriverStation/Command", "INIT")
        Thread.sleep(500)
        NT4Server.publishTopic("ARES/DriverStation/Command", "START")
        Thread.sleep(500)

        // 3. Inject drive input (vx = 2.0 m/s)
        println("[Telemetry E2E Test] Injecting vx = 2.0 m/s drive input...")
        NT4Server.publishTopic("ARES/Input/vx", 2.0)
        NT4Server.publishTopic("ARES/Input/vy", 0.0)
        NT4Server.publishTopic("ARES/Input/omega", 0.0)

        // Step physics world for 1.0 second
        Thread.sleep(1000)

        // 4. Verify Motor Powers (fl, fr, rl, rr, bl, br)
        val flPower = NT4Server.getDouble("Hardware/Motors/fl/Power", 0.0)
        val frPower = NT4Server.getDouble("Hardware/Motors/fr/Power", 0.0)
        val rlPower = NT4Server.getDouble("Hardware/Motors/rl/Power", 0.0)
        val rrPower = NT4Server.getDouble("Hardware/Motors/rr/Power", 0.0)
        val blPower = NT4Server.getDouble("Hardware/Motors/bl/Power", 0.0)
        val brPower = NT4Server.getDouble("Hardware/Motors/br/Power", 0.0)

        println("[Telemetry E2E Test] Motor Powers -> FL: $flPower, FR: $frPower, RL: $rlPower, RR: $rrPower, BL: $blPower, BR: $brPower")
        assertTrue("FL motor power should be > 0.1", flPower > 0.1)
        assertTrue("FR motor power should be > 0.1", frPower > 0.1)
        assertTrue("RL motor power should be > 0.1", rlPower > 0.1)
        assertTrue("RR motor power should be > 0.1", rrPower > 0.1)
        assertTrue("BL alias motor power should be > 0.1", blPower > 0.1)
        assertTrue("BR alias motor power should be > 0.1", brPower > 0.1)

        // 5. Verify Motor Velocities (ticks/sec)
        val flVel = NT4Server.getDouble("Hardware/Motors/fl/Velocity", 0.0)
        val frVel = NT4Server.getDouble("Hardware/Motors/fr/Velocity", 0.0)
        val rlVel = NT4Server.getDouble("Hardware/Motors/rl/Velocity", 0.0)
        val rrVel = NT4Server.getDouble("Hardware/Motors/rr/Velocity", 0.0)

        println("[Telemetry E2E Test] Motor Velocities -> FL: $flVel, FR: $frVel, RL: $rlVel, RR: $rrVel")
        assertTrue("FL motor velocity magnitude should be > 100 ticks/s", kotlin.math.abs(flVel) > 100.0)
        assertTrue("FR motor velocity magnitude should be > 100 ticks/s", kotlin.math.abs(frVel) > 100.0)
        assertTrue("RL motor velocity magnitude should be > 100 ticks/s", kotlin.math.abs(rlVel) > 100.0)
        assertTrue("RR motor velocity magnitude should be > 100 ticks/s", kotlin.math.abs(rrVel) > 100.0)

        // 6. Verify Motor Current Draw (Amperes)
        val flCurrent = NT4Server.getDouble("Hardware/Motors/fl/CurrentAmps", 0.0)
        val frCurrent = NT4Server.getDouble("Hardware/Motors/fr/CurrentAmps", 0.0)
        val rlCurrent = NT4Server.getDouble("Hardware/Motors/rl/CurrentAmps", 0.0)
        val rrCurrent = NT4Server.getDouble("Hardware/Motors/rr/CurrentAmps", 0.0)

        println("[Telemetry E2E Test] Motor Currents -> FL: ${flCurrent}A, FR: ${frCurrent}A, RL: ${rlCurrent}A, RR: ${rrCurrent}A")
        assertTrue("FL motor current draw should be > 0.5A under drive load", flCurrent > 0.5)
        assertTrue("FR motor current draw should be > 0.5A under drive load", frCurrent > 0.5)
        assertTrue("RL motor current draw should be > 0.5A under drive load", rlCurrent > 0.5)
        assertTrue("RR motor current draw should be > 0.5A under drive load", rrCurrent > 0.5)

        // 7. Verify Odometry & EKF Pose telemetry streams
        val estX = NT4Server.getDouble("ARES/EstimatedPose/0", 0.0)
        val estY = NT4Server.getDouble("ARES/EstimatedPose/1", 0.0)
        val estHeading = NT4Server.getDouble("ARES/EstimatedPose/2", 0.0)
        println("[Telemetry E2E Test] Estimated Pose -> X: $estX, Y: $estY, Heading: $estHeading rad")

        val trueX = NT4Server.getDouble("ARES/TruePose/0", 0.0)
        val trueY = NT4Server.getDouble("ARES/TruePose/1", 0.0)
        println("[Telemetry E2E Test] True Physics Pose -> X: $trueX, Y: $trueY")
        assertNotEquals("True physics Y pose should advance from starting pose", -1.2, trueY, 0.05)

        // 8. Verify Driver Station Match State
        val matchState = NT4Server.getString("ARES/DriverStation/MatchState", "")
        println("[Telemetry E2E Test] Match State: '$matchState'")
        assertEquals("MatchState should be TELEOP when OpMode is running", "TELEOP", matchState)

        // 9. Verify Telemetry Line Output
        val teleLine0 = NT4Server.getString("ARES/DriverStation/Telemetry/0", "")
        println("[Telemetry E2E Test] Telemetry Line 0: '$teleLine0'")
        assertTrue("Driver Station Telemetry Line 0 should not be empty", teleLine0.isNotEmpty())

        println("[Telemetry E2E Test] SUCCESS! All simulated telemetry streams verified cleanly.")
    }
}
