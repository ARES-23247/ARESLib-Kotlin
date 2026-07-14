package com.areslib.sim

import edu.wpi.first.networktables.NetworkTableInstance
import kotlin.concurrent.thread
import kotlin.math.abs

import com.areslib.math.wrapAngle

fun main(args: Array<String>) {
    println("=================================================================")
    println("STARTING PROGRAMMATIC INTEGRATION VERIFICATION")
    println("=================================================================")

    // 1. Start simulator in background thread
    thread {
        try {
            DesktopSimLauncher.launch(
                args = arrayOf("--opmode", "org.firstinspires.ftc.teamcode.opmodes.ARESMecanumTeleOp", "--headless"),
                interactionModel = NoOpInteractionModel()
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 2. Setup NT4 client
    val ntInst = NetworkTableInstance.create()
    ntInst.startClient4("VerificationClient")
    ntInst.setServer("127.0.0.1")

    // Wait for connection
    var connected = false
    val startConnectTime = System.currentTimeMillis()
    while (System.currentTimeMillis() - startConnectTime < 10000) {
        if (ntInst.isConnected) {
            connected = true
            break
        }
        Thread.sleep(100)
    }

    if (!connected) {
        System.err.println("Verification Failed: Could not connect to simulator NT4 server!")
        System.exit(1)
    }
    println("Connected to simulator NT4 server.")

    // Get topics
    val cmdPub = ntInst.getStringTopic("ARES/DriverStation/Command").publish()
    val selectPub = ntInst.getStringTopic("ARES/DriverStation/SelectedOpMode").publish()
    
    val vxPub = ntInst.getDoubleTopic("ARES/Input/vx").publish()
    val vyPub = ntInst.getDoubleTopic("ARES/Input/vy").publish()
    val omegaPub = ntInst.getDoubleTopic("ARES/Input/omega").publish()
    val heartbeatPub = ntInst.getIntegerTopic("ARES/Input/heartbeat").publish()
    val teleopPub = ntInst.getBooleanTopic("ARES/Input/isTeleopMode").publish()

    // Start background heartbeat publisher to keep inputs active
    val running = java.util.concurrent.atomic.AtomicBoolean(true)
    thread {
        var count = 0L
        while (running.get()) {
            heartbeatPub.set(count++)
            teleopPub.set(true)
            try {
                Thread.sleep(50)
            } catch (_: InterruptedException) {
                break
            }
        }
    }

    // EKF pose subscribers
    val estPoseSub = ntInst.getDoubleArrayTopic("ARES/EstimatedPose").subscribe(
        doubleArrayOf(0.0, 0.0, 0.0),
        edu.wpi.first.networktables.PubSubOption.periodic(0.01)
    )

    // 3. Command INIT
    selectPub.set("org.firstinspires.ftc.teamcode.opmodes.ARESMecanumTeleOp")
    cmdPub.set("INIT")
    println("Sent INIT command for ARESMecanumTeleOp.")
    Thread.sleep(3000) // Wait for init loop

    // 4. Command START
    cmdPub.set("START")
    println("Sent START command.")
    Thread.sleep(1500) // Wait for match start sync

    fun getPose(): Triple<Double, Double, Double> {
        val arr = estPoseSub.get()
        return if (arr.size >= 3) {
            Triple(arr[0], arr[1], arr[2])
        } else {
            Triple(0.0, 0.0, 0.0)
        }
    }

    // Measure starting pose
    val (startX, startY, startH) = getPose()
    println("Starting Pose: X=%.3f, Y=%.3f, Heading=%.3f".format(startX, startY, startH))

    // 5. Test 1: Command forward (+Y from alliance perspective)
    println("Test 1: Pushing forward (+Y)...")
    vxPub.set(1.5)
    Thread.sleep(1500)
    vxPub.set(0.0)
    Thread.sleep(1000) // Wait to settle

    val (pos1X, pos1Y, pos1H) = getPose()
    println("Pose after Test 1: X=%.3f, Y=%.3f, Heading=%.3f".format(pos1X, pos1Y, pos1H))
    
    val deltaY1 = pos1Y - startY
    val deltaX1 = pos1X - startX
    println("Test 1 Delta: dX=%.3f, dY=%.3f".format(deltaX1, deltaY1))

    if (deltaY1 < 0.2) {
        running.set(false)
        System.err.println("Verification Failed: Robot did not move forward (+Y) in Test 1! DeltaY=%.3f".format(deltaY1))
        System.exit(1)
    }
    println("Test 1 Passed: Robot successfully moved forward in Y.")

    // 6. Test 2: Rotate robot to facing ~0.0 heading
    println("Test 2: Rotating robot to 0.0 heading...")
    val rotateStartTime = System.currentTimeMillis()
    var rotatedOk = false
    var lastH = getPose().third
    var lastTime = System.currentTimeMillis()
    while (System.currentTimeMillis() - rotateStartTime < 8000) {
        val (_, _, currentH) = getPose()
        val now = System.currentTimeMillis()
        val dt = (now - lastTime) / 1000.0
        
        // Calculate velocity dynamically to predict stopping drift
        val velocity = if (dt > 0.001) wrapAngle(currentH - lastH) / dt else 0.0
        lastH = currentH
        lastTime = now
        
        // Stop deceleration time is ~140ms. Drift is velocity * decelTime / 2 = velocity * 0.07.
        val predictedH = currentH + velocity * 0.07
        val error = wrapAngle(0.0 - currentH)
        
        if (abs(wrapAngle(0.0 - predictedH)) < 0.012) {
            omegaPub.set(0.0)
            Thread.sleep(600)
            val (_, _, finalH) = getPose()
            if (abs(wrapAngle(0.0 - finalH)) < 0.02) {
                rotatedOk = true
                break
            }
        }
        
        val kP = 4.0
        var cmdOmega = error * kP
        // Keep cmdOmega just barely above 0.20 (which maps to 0.05 on the gamepad stick)
        // to prevent premature heading lock and keep rotation extremely slow/controlled
        if (abs(cmdOmega) < 0.205) {
            val sign = if (error > 0.0) 1.0 else -1.0
            cmdOmega = sign * 0.205
        }
        cmdOmega = cmdOmega.coerceIn(-3.0, 3.0)
        omegaPub.set(cmdOmega)
        Thread.sleep(20)
    }

    if (!rotatedOk) {
        omegaPub.set(0.0)
        running.set(false)
        System.err.println("Verification Failed: Robot failed to rotate to 0.0 heading! Current heading: %.3f".format(getPose().third))
        System.exit(1)
    }
    
    Thread.sleep(2000) // Wait for heading lock PID to fully settle at 0.0
    val (pos2X, pos2Y, pos2H) = getPose()
    println("Settle Pose: X=%.3f, Y=%.3f, Heading=%.3f".format(pos2X, pos2Y, pos2H))

    // 7. Test 3: Command forward (+Y) again while facing 0.0 heading
    println("Test 3: Pushing forward (+Y) while rotated to 0.0 heading...")
    vxPub.set(1.5)
    Thread.sleep(1500)
    vxPub.set(0.0)
    Thread.sleep(1000) // Settle

    val (finalX, finalY, finalH) = getPose()
    println("Final Pose: X=%.3f, Y=%.3f, Heading=%.3f".format(finalX, finalY, finalH))

    val deltaY2 = finalY - pos2Y
    val deltaX2 = finalX - pos2X
    println("Test 3 Delta: dX=%.3f, dY=%.3f".format(deltaX2, deltaY2))

    // If it is field-centric: robot moves in +Y.
    // If it is robot-centric: robot moves in +X (since it's facing 0.0 heading).
    val ratio = abs(deltaY2) / (abs(deltaX2) + 1e-6)
    println("Ratio of Y movement to X movement: %.3f".format(ratio))

    running.set(false)
    if (deltaY2 > 0.2 && ratio > 2.0) {
        println("VERIFICATION SUCCESSFUL: Drivetrain is field-centric! Robot moved in +Y, not +X.")
        System.exit(0)
    } else if (deltaX2 > 0.2 && ratio < 0.5) {
        System.err.println("VERIFICATION FAILED: Robot is driving ROBOT-CENTRIC! Rotated forward command moved in X (longitudinal) instead of Y (field-centric).")
        System.exit(1)
    } else {
        System.err.println("VERIFICATION FAILED: Inconclusive movement. dX=%.3f, dY=%.3f".format(deltaX2, deltaY2))
        System.exit(1)
    }
}
