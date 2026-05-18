package com.areslib.control

import com.areslib.math.Pose2d
import com.areslib.math.Rotation2d
import com.areslib.state.Obstacle
import org.junit.jupiter.api.Test
import kotlin.math.hypot
import kotlin.test.assertTrue

class ClosedLoopAvoidanceSimTest {

    @Test
    fun `test closed loop swerve simulation avoids intermediate obstacle`() {
        val xController = PIDController(1.5, 0.0, 0.1)
        val yController = PIDController(1.5, 0.0, 0.1)
        val thetaController = PIDController(1.0, 0.0, 0.0)
        val controller = HolonomicDriveController(xController, yController, thetaController)

        var robotPose = Pose2d(0.0, 0.0, Rotation2d.fromDegrees(0.0))
        val targetPose = Pose2d(3.0, 0.0, Rotation2d.fromDegrees(0.0))
        
        // Place an obstacle at (1.5, 0.0) directly between start and target
        val obstacle = Obstacle(1.5, 0.0, radius = 0.25)
        val obstacles = listOf(obstacle)

        val dt = 0.05
        var time = 0.0
        var minDistanceToObstacle = Double.MAX_VALUE

        // Run simulation for 8 seconds
        while (time < 8.0) {
            val distToTarget = hypot(targetPose.x - robotPose.x, targetPose.y - robotPose.y)
            if (distToTarget < 0.25) break

            val speeds = controller.calculate(
                currentPose = robotPose,
                targetPose = targetPose,
                targetVelocityMps = 1.0,
                targetHeading = Rotation2d.fromDegrees(0.0),
                dtSeconds = dt,
                curvature = 0.0,
                maxCentripetalAccel = 2.5,
                obstacles = obstacles
            )

            // Simplistic kinodynamic update (Euler integration) with physical speed limits
            val maxSpeed = 1.5
            val vx = speeds.vxMetersPerSecond.coerceIn(-maxSpeed, maxSpeed)
            val vy = speeds.vyMetersPerSecond.coerceIn(-maxSpeed, maxSpeed)

            val newX = robotPose.x + vx * dt
            val newY = robotPose.y + vy * dt
            val newHeading = robotPose.heading.radians + speeds.omegaRadiansPerSecond * dt

            robotPose = Pose2d(newX, newY, Rotation2d(newHeading))

            val distToObstacle = hypot(obstacle.x - robotPose.x, obstacle.y - robotPose.y)
            println("Time: ${String.format("%.2f", time)}s | Pose: (${String.format("%.2f", robotPose.x)}, ${String.format("%.2f", robotPose.y)}) | Dist to Obs: ${String.format("%.2f", distToObstacle)}m | Speeds: vx=${String.format("%.2f", vx)}, vy=${String.format("%.2f", vy)}")
            if (distToObstacle < minDistanceToObstacle) {
                minDistanceToObstacle = distToObstacle
            }

            time += dt
        }

        // Verify safety: robot must never collide with obstacle (distance > obstacle radius)
        assertTrue(minDistanceToObstacle > 0.26, "Collision detected! Closest distance: $minDistanceToObstacle")
        
        // Verify completion: robot successfully reached near the target
        val finalDistToTarget = hypot(targetPose.x - robotPose.x, targetPose.y - robotPose.y)
        assertTrue(finalDistToTarget < 0.35, "Failed to reach target. Remaining distance: $finalDistToTarget")
    }
}
