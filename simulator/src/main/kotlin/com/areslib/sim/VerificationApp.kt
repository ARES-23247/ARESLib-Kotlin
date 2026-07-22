package com.areslib.sim

import edu.wpi.first.networktables.NetworkTableInstance
import kotlin.concurrent.thread
import kotlin.math.abs

import com.areslib.math.wrapAngle

/**
 * main declaration.
 *
 * @param args Standard arguments (if applicable).
 * @return Corresponding output value or Unit.
 */
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
    val startConnectTime = com.areslib.util.RobotClock.currentTimeMillis()
    while (com.areslib.util.RobotClock.currentTimeMillis() - startConnectTime < 10000) {
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
    ntInst.flush()

    // 3. Command INIT
    selectPub.set("org.firstinspires.ftc.teamcode.opmodes.ARESMecanumTeleOp")
    cmdPub.set("INIT")
    ntInst.flush()
    println("Sent INIT command for ARESMecanumTeleOp.")
    Thread.sleep(3000) // Wait for init loop

    // 4. Command START
    cmdPub.set("START")
    ntInst.flush()
    println("Sent START command.")

    fun getPose(): Triple<Double, Double, Double> {
        val arr = estPoseSub.get()
        if (arr.size >= 3) {
            return Triple(arr[0], arr[1], arr[2])
        }
        return Triple(0.0, 0.0, 0.0)
    }

    // Wait for EKF starting pose sync (wait for simulator to start and reset EKF pose)
    println("Waiting for EKF starting pose sync from simulator...")
    val startSyncTime = com.areslib.util.RobotClock.currentTimeMillis()
    var synced = false
    while (com.areslib.util.RobotClock.currentTimeMillis() - startSyncTime < 25000) {
        val (_, y, _) = getPose()
        if (abs(y) > 0.5) {
            synced = true
            break
        }
        Thread.sleep(100)
    }
    if (!synced) {
        System.err.println("Verification Failed: Simulator did not sync starting pose in 25s! Current pose Y: " + getPose().second)
        System.exit(1)
    }
    println("EKF starting pose synced successfully.")

    /**
     * rotateToTarget declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun rotateToTarget(targetRad: Double, toleranceRad: Double = 0.03, timeoutMs: Long = 6000): Boolean {
        println("Rotating to target: %.3f rad...".format(targetRad))
        val startTime = com.areslib.util.RobotClock.currentTimeMillis()
        var settledTicks = 0
        var lastH = getPose().third
        var lastTime = com.areslib.util.RobotClock.currentTimeMillis()
        while (com.areslib.util.RobotClock.currentTimeMillis() - startTime < timeoutMs) {
            val (_, _, currentH) = getPose()
            val now = com.areslib.util.RobotClock.currentTimeMillis()
            val dt = (now - lastTime) / 1000.0
            val velocity = if (dt > 0.001) wrapAngle(currentH - lastH) / dt else 0.0
            lastH = currentH
            lastTime = now

            val error = wrapAngle(targetRad - currentH)

            if (abs(error) < toleranceRad && abs(velocity) < 0.25) {
                settledTicks++
                if (settledTicks >= 4) { // Settle for ~80ms
                    omegaPub.set(0.0)
                    ntInst.flush()
                    Thread.sleep(250)
                    val (_, _, finalH) = getPose()
                    if (abs(wrapAngle(targetRad - finalH)) < toleranceRad + 0.015) {
                        println("Successfully stabilized at target: %.3f rad (actual: %.3f)".format(targetRad, finalH))
                        return true
                    }
                }
            } else {
                settledTicks = 0
            }

            val kP = 2.5
            val kD = 0.12
            var cmdOmega = error * kP - velocity * kD
            cmdOmega = cmdOmega.coerceIn(-1.5, 1.5)
            omegaPub.set(cmdOmega)
            ntInst.flush()
            Thread.sleep(20)
        }
        omegaPub.set(0.0)
        ntInst.flush()
        return false
    }

    // Measure starting pose
    val (startX, startY, startH) = getPose()
    println("Starting Pose: X=%.3f, Y=%.3f, Heading=%.3f".format(startX, startY, startH))

    // 5. Test 1: Square Drive Test (Translation in all 4 directions)
    println("Test 1: Starting Square Drive Test...")
    
    // Segment 1: Drive +Y (Forward on field)
    println("Square Drive - Segment 1: Pushing +Y...")
    vyPub.set(1.2)
    ntInst.flush()
    Thread.sleep(1200)
    vyPub.set(0.0)
    ntInst.flush()
    Thread.sleep(1000)
    val (p1X, p1Y, _) = getPose()
    val dX1 = p1X - startX
    val dY1 = p1Y - startY
    println("Segment 1 Delta: dX=%.3f, dY=%.3f".format(dX1, dY1))
    if (dY1 < 0.2 || abs(dX1) > 0.15) {
        running.set(false)
        System.err.println("Square Drive Failed: Segment 1 (+Y) failed! dX=%.3f, dY=%.3f".format(dX1, dY1))
        System.exit(1)
    }
    
    // Segment 2: Drive +X (Right on field)
    println("Square Drive - Segment 2: Pushing +X...")
    vxPub.set(1.2)
    ntInst.flush()
    Thread.sleep(1200)
    vxPub.set(0.0)
    ntInst.flush()
    Thread.sleep(1000)
    val (p2X, p2Y, _) = getPose()
    val dX2 = p2X - p1X
    val dY2 = p2Y - p1Y
    println("Segment 2 Delta: dX=%.3f, dY=%.3f".format(dX2, dY2))
    if (dX2 < 0.2 || abs(dY2) > 0.15) {
        running.set(false)
        System.err.println("Square Drive Failed: Segment 2 (+X) failed! dX=%.3f, dY=%.3f".format(dX2, dY2))
        System.exit(1)
    }

    // Segment 3: Drive -Y (Backward on field)
    println("Square Drive - Segment 3: Pushing -Y...")
    vyPub.set(-1.2)
    ntInst.flush()
    Thread.sleep(1200)
    vyPub.set(0.0)
    ntInst.flush()
    Thread.sleep(1000)
    val (p3X, p3Y, _) = getPose()
    val dX3 = p3X - p2X
    val dY3 = p3Y - p2Y
    println("Segment 3 Delta: dX=%.3f, dY=%.3f".format(dX3, dY3))
    if (dY3 > -0.2 || abs(dX3) > 0.15) {
        running.set(false)
        System.err.println("Square Drive Failed: Segment 3 (-Y) failed! dX=%.3f, dY=%.3f".format(dX3, dY3))
        System.exit(1)
    }

    // Segment 4: Drive -X (Left on field)
    println("Square Drive - Segment 4: Pushing -X...")
    vxPub.set(-1.2)
    ntInst.flush()
    Thread.sleep(1200)
    vxPub.set(0.0)
    ntInst.flush()
    Thread.sleep(1000)
    val (p4X, p4Y, _) = getPose()
    val dX4 = p4X - p3X
    val dY4 = p4Y - p3Y
    println("Segment 4 Delta: dX=%.3f, dY=%.3f".format(dX4, dY4))
    if (dX4 > -0.2 || abs(dY4) > 0.15) {
        running.set(false)
        System.err.println("Square Drive Failed: Segment 4 (-X) failed! dX=%.3f, dY=%.3f".format(dX4, dY4))
        System.exit(1)
    }

    println("Test 1 Passed: Square Drive completed successfully!")

    // 6. Test 2: Rotate robot to facing ~0.0 heading
    println("Test 2: Rotating robot to 0.0 heading...")
    val rotatedOk = rotateToTarget(0.0, toleranceRad = 0.02, timeoutMs = 8000)
    if (!rotatedOk) {
        running.set(false)
        System.err.println("Verification Failed: Robot failed to rotate to 0.0 heading! Current heading: %.3f".format(getPose().third))
        System.exit(1)
    }
    println("Test 2 Passed: Robot successfully rotated to 0.0 heading.")
    
    Thread.sleep(2000) // Wait for heading lock PID to fully settle at 0.0
    val (pos2X, pos2Y, pos2H) = getPose()
    println("Settle Pose: X=%.3f, Y=%.3f, Heading=%.3f".format(pos2X, pos2Y, pos2H))

    // 7. Test 3: Command forward (+Y) again while facing 0.0 heading
    println("Test 3: Pushing forward (+Y) while rotated to 0.0 heading...")
    vyPub.set(1.5)
    ntInst.flush()
    Thread.sleep(1500)
    vyPub.set(0.0)
    ntInst.flush()
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

    if (!(deltaY2 > 0.2 && ratio > 2.0)) {
        running.set(false)
        if (deltaX2 > 0.2 && ratio < 0.5) {
            System.err.println("VERIFICATION FAILED: Robot is driving ROBOT-CENTRIC! Rotated forward command moved in X (longitudinal) instead of Y (field-centric).")
        } else {
            System.err.println("VERIFICATION FAILED: Inconclusive movement. dX=%.3f, dY=%.3f".format(deltaX2, deltaY2))
        }
        System.exit(1)
    }
    println("Test 3 Passed: Field-centric translation verified.")

    // 8. Test 4: Multi-Target Rotation Test (Dedicated Rotation Verification)
    println("Test 4: Starting Multi-Target Rotation Test...")
    
    // Target 1: Rotate to +90 degrees (+PI/2 rad)
    val rot1Ok = rotateToTarget(Math.PI / 2, toleranceRad = 0.035, timeoutMs = 8000)
    if (!rot1Ok) {
        running.set(false)
        System.err.println("Verification Failed: Robot failed to rotate to +90 degrees! Current heading: %.3f".format(getPose().third))
        System.exit(1)
    }
    Thread.sleep(1000)

    // Target 2: Rotate to -90 degrees (-PI/2 rad)
    val rot2Ok = rotateToTarget(-Math.PI / 2, toleranceRad = 0.03, timeoutMs = 8000)
    if (!rot2Ok) {
        running.set(false)
        System.err.println("Verification Failed: Robot failed to rotate to -90 degrees! Current heading: %.3f".format(getPose().third))
        System.exit(1)
    }
    Thread.sleep(1000)

    // Target 3: Rotate back to 0.0 rad
    val rot3Ok = rotateToTarget(0.0, toleranceRad = 0.03, timeoutMs = 8000)
    if (!rot3Ok) {
        running.set(false)
        System.err.println("Verification Failed: Robot failed to rotate back to 0.0 rad! Current heading: %.3f".format(getPose().third))
        System.exit(1)
    }

    println("Test 4 Passed: Multi-target rotation verified.")
    println("VERIFICATION SUCCESSFUL: Drivetrain translation and rotation function perfectly!")
    running.set(false)
    System.exit(0)
}
