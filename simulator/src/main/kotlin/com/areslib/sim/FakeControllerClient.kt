package com.areslib.sim

import edu.wpi.first.networktables.NetworkTableInstance

object FakeControllerClient {
    @JvmStatic
    fun main(args: Array<String>) {
        println("Starting Fake Controller Client...")
        val ntInst = NetworkTableInstance.getDefault()
        
        // Start client and connect to local server
        ntInst.startClient4("FakeController")
        ntInst.setServer("127.0.0.1")
        
        println("Connecting to NetworkTables server...")
        var connected = false
        for (i in 0 until 50) {
            if (ntInst.isConnected) {
                connected = true
                break
            }
            Thread.sleep(100)
        }
        
        if (!connected) {
            System.err.println("Error: Failed to connect to local NetworkTables server. Make sure the simulator is running.")
            return
        }
        
        println("Connected to NetworkTables successfully!")
        
        val heartbeatPub = ntInst.getIntegerTopic("ARES/Input/heartbeat").publish()
        val vxPub = ntInst.getDoubleTopic("ARES/Input/vx").publish()
        val vyPub = ntInst.getDoubleTopic("ARES/Input/vy").publish()
        val omegaPub = ntInst.getDoubleTopic("ARES/Input/omega").publish()
        val teleopPub = ntInst.getBooleanTopic("ARES/Input/isTeleopMode").publish()
        
        val poseSub = ntInst.getDoubleArrayTopic("ARES/EstimatedPose").subscribe(doubleArrayOf(0.0, 0.0, 0.0))
        
        teleopPub.set(true)
        
        var heartbeat = 0L
        
        for (tick in 1..60) {
            heartbeat++
            heartbeatPub.set(heartbeat)
            
            // Set inputs:
            // Ticks 1-20: Drive forward (vx = 0.5)
            // Ticks 21-40: Strafe right (vy = 0.5)
            // Ticks 41-60: Rotate CCW (omega = 0.5)
            val vx = when (tick) {
                in 1..20 -> 0.5
                else -> 0.0
            }
            val vy = when (tick) {
                in 21..40 -> 0.5
                else -> 0.0
            }
            val omega = when (tick) {
                in 41..60 -> 0.5
                else -> 0.0
            }
            
            vxPub.set(vx)
            vyPub.set(vy)
            omegaPub.set(omega)
            
            Thread.sleep(100)
            
            val pose = poseSub.get()
            println(String.format("[Fake Controller] Tick %02d | Commanded: vx=%.2f, vy=%.2f, omega=%.2f | Estimated Pose: X=%.3f m, Y=%.3f m, Heading=%.3f rad", 
                tick, vx, vy, omega, pose.getOrNull(0) ?: 0.0, pose.getOrNull(1) ?: 0.0, pose.getOrNull(2) ?: 0.0))
        }
        
        println("Fake Controller simulation completed. Stopping NT4 client...")
        ntInst.stopClient()
    }
}

