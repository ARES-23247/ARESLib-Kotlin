package com.areslib.math

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

        /**
         * In-place conversion of field-relative velocities into the provided output ChassisSpeeds instance.
         */
        fun fromFieldRelativeSpeeds(
            vxMetersPerSecond: Double,
            vyMetersPerSecond: Double,
            omegaRadiansPerSecond: Double,
            robotHeading: Rotation2d,
            out: ChassisSpeeds
        ): ChassisSpeeds {
            val cos = robotHeading.cos
            val sin = robotHeading.sin
            val robotX = vxMetersPerSecond * cos + vyMetersPerSecond * sin
            val robotY = -vxMetersPerSecond * sin + vyMetersPerSecond * cos
            out.vxMetersPerSecond = robotX
            out.vyMetersPerSecond = robotY
            out.omegaRadiansPerSecond = omegaRadiansPerSecond
            return out
        }
    }
}
