package com.areslib.state

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.File

enum class FieldType {
    @SerializedName("ftc") FTC,
    @SerializedName("frc") FRC
}

enum class AxisDirection {
    @SerializedName("up") UP,
    @SerializedName("down") DOWN,
    @SerializedName("left") LEFT,
    @SerializedName("right") RIGHT
}

enum class DriverStationSide {
    @SerializedName("north") NORTH,
    @SerializedName("south") SOUTH,
    @SerializedName("east") EAST,
    @SerializedName("west") WEST
}

enum class ObstacleType {
    @SerializedName("blocking") BLOCKING,
    @SerializedName("ramp") RAMP
}

data class RobotFieldPoint(
    val x: Double = 0.0,
    val y: Double = 0.0
)

data class RobotFieldObstacle(
    val id: String = "",
    val name: String = "",
    val x: Double = 0.0,
    val y: Double = 0.0,
    val width: Double = 0.1,
    val height: Double = 0.1,
    val isBlocking: Boolean = true,
    val obstacleType: ObstacleType = ObstacleType.BLOCKING,
    val rampDirection: AxisDirection? = null,
    val shape: String = "rectangle", // "rectangle" or "polygon"
    val points: List<RobotFieldPoint> = emptyList()
)

data class RobotFieldAprilTag(
    val id: Int = 0,
    val x: Double = 0.0,
    val y: Double = 0.0,
    val z: Double = 0.0,
    val yaw: Double = 0.0 // Yaw rotation in degrees
)

data class RobotFieldConfig(
    val id: String = "",
    val name: String = "",
    val gameYear: String = "",
    val fieldType: FieldType = FieldType.FTC,
    val xAxisDirection: AxisDirection = AxisDirection.UP,
    val yAxisDirection: AxisDirection = AxisDirection.LEFT,
    val redDriverStation: DriverStationSide = DriverStationSide.SOUTH,
    val blueDriverStation: DriverStationSide = DriverStationSide.NORTH,
    val obstacles: List<RobotFieldObstacle> = emptyList(),
    val apriltags: List<RobotFieldAprilTag> = emptyList()
) {
    /**
     * Resolves the starting pose based on the alliance's driver station wall.
     * Starts adjacent to the wall facing the field center.
     */
    fun getInitialPose(alliance: Alliance): com.areslib.math.Pose2d {
        val side = if (alliance == Alliance.BLUE) blueDriverStation else redDriverStation
        
        // Calculate coordinate based on which side is the driver wall
        val startX = when (side) {
            DriverStationSide.EAST -> 1.8
            DriverStationSide.WEST -> -1.8
            else -> 0.0
        }
        val startY = when (side) {
            DriverStationSide.NORTH -> 1.8
            DriverStationSide.SOUTH -> -1.8
            else -> 0.0
        }
        
        // Determine initial heading facing the field center
        val headingRad = when (side) {
            DriverStationSide.EAST -> Math.PI
            DriverStationSide.WEST -> 0.0
            DriverStationSide.NORTH -> -Math.PI / 2.0
            DriverStationSide.SOUTH -> Math.PI / 2.0
        }
        
        return com.areslib.math.Pose2d(startX, startY, com.areslib.math.Rotation2d(headingRad))
    }

    /**
     * Maps raw driver joystick commands to absolute EKF coordinates based on the configured driver station side.
     */
    fun mapJoystickIntents(joystickForward: Double, joystickLeft: Double, alliance: Alliance): Pair<Double, Double> {
        val side = if (alliance == Alliance.BLUE) blueDriverStation else redDriverStation
        
        var vx = 0.0
        var vy = 0.0
        
        when (side) {
            DriverStationSide.SOUTH -> {
                // Forward is +Y, Left is -X
                vy = joystickForward
                vx = -joystickLeft
            }
            DriverStationSide.NORTH -> {
                // Forward is -Y, Left is +X
                vy = -joystickForward
                vx = joystickLeft
            }
            DriverStationSide.WEST -> {
                // Forward is +X, Left is +Y
                vx = joystickForward
                vy = joystickLeft
            }
            DriverStationSide.EAST -> {
                // Forward is -X, Left is -Y
                vx = -joystickForward
                vy = -joystickLeft
            }
        }
        return Pair(vx, vy)
    }
}

object RobotFieldManager {
    private val gson = Gson()
    
    // Default fallback layout
    var activeConfig: RobotFieldConfig = RobotFieldConfig(
        name = "Default FTC Field",
        fieldType = FieldType.FTC
    )
        private set

    /**
     * Loads the field config from a JSON file.
     * Useful for loading dynamic configurations copied directly from ARESWEB.
     */
    fun loadFromJsonFile(filePath: String): Boolean {
        return try {
            val file = File(filePath)
            if (!file.exists()) return false
            val jsonContent = file.readText()
            val loaded = gson.fromJson(jsonContent, RobotFieldConfig::class.java)
            if (loaded != null) {
                activeConfig = loaded
                return true
            }
            false
        } catch (e: Exception) {
            println("ARES Field Manager Error: Failed to load field json from $filePath: ${e.message}")
            false
        }
    }

    /**
     * Sets the active configuration manually from compiled code.
     */
    fun setActiveConfig(config: RobotFieldConfig) {
        activeConfig = config
    }
}
