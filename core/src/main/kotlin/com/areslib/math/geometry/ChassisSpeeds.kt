package com.areslib.math.geometry

data class ChassisSpeeds(
    var vxMetersPerSecond: Double = 0.0,
    var vyMetersPerSecond: Double = 0.0,
    var omegaRadiansPerSecond: Double = 0.0
) {
    companion object {
        /**
         * Converts field-relative velocities into robot-centric ChassisSpeeds.
         * The math rotates the field vector by the inverse of the robot's current heading.
         */
        fun fromFieldRelativeSpeeds(
            vxMetersPerSecond: Double,
            vyMetersPerSecond: Double,
            omegaRadiansPerSecond: Double,
            robotHeading: Rotation2d
        ): ChassisSpeeds {
            val cos = robotHeading.cos
            val sin = robotHeading.sin
            // Inverse rotation
            val robotX = vxMetersPerSecond * cos + vyMetersPerSecond * sin
            val robotY = -vxMetersPerSecond * sin + vyMetersPerSecond * cos
            
            return ChassisSpeeds(robotX, robotY, omegaRadiansPerSecond)
        }
    }
}
