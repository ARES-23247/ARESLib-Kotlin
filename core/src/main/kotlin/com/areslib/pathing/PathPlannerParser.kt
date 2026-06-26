package com.areslib.pathing

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.areslib.math.Pose2d
import com.areslib.math.Rotation2d
import kotlin.math.hypot

object PathPlannerParser {
    private val gson = Gson()

    /**
     * Parses a PathPlanner .path JSON string into an immutable Path.
     * Generates a dense array of interpolations using Cubic Bezier math and applies Trapezoidal Motion Profiling.
     */
    fun parsePath(jsonString: String, maxVelocityMps: Double = 2.0, maxAccelerationMps2: Double = 1.5): Path {
        val root = gson.fromJson(jsonString, JsonObject::class.java)
        
        val waypointsArray = root.getAsJsonArray("waypoints")
        val pathPoints = mutableListOf<PathPoint>()
        var accumulatedDistance = 0.0

        val parsedWaypoints = mutableListOf<WaypointData>()
        for (i in 0 until waypointsArray.size()) {
            val wp = waypointsArray.get(i).asJsonObject
            val anchorNode = wp.getAsJsonObject("anchor")
            val anchor = com.areslib.math.Translation2d(anchorNode.get("x").asDouble, anchorNode.get("y").asDouble)
            
            val prevNode = if (wp.has("prevControl") && !wp.get("prevControl").isJsonNull) wp.getAsJsonObject("prevControl") else null
            val prevControl = prevNode?.let { com.areslib.math.Translation2d(it.get("x").asDouble, it.get("y").asDouble) } ?: anchor
            
            val nextNode = if (wp.has("nextControl") && !wp.get("nextControl").isJsonNull) wp.getAsJsonObject("nextControl") else null
            val nextControl = nextNode?.let { com.areslib.math.Translation2d(it.get("x").asDouble, it.get("y").asDouble) } ?: anchor
            
            parsedWaypoints.add(WaypointData(anchor, prevControl, nextControl))
        }

        if (parsedWaypoints.isEmpty()) {
            return Path(emptyList())
        }

        // Parse advanced parameters
        val globalConstraints = if (root.has("globalConstraints") && !root.get("globalConstraints").isJsonNull) {
            root.getAsJsonObject("globalConstraints")
        } else null
        val defaultMaxVel = globalConstraints?.get("maxVelocity")?.asDouble ?: maxVelocityMps
        val defaultMaxAccel = globalConstraints?.get("maxAcceleration")?.asDouble ?: maxAccelerationMps2

        val idealStartingState = if (root.has("idealStartingState") && !root.get("idealStartingState").isJsonNull) {
            root.getAsJsonObject("idealStartingState")
        } else null
        val startVel = idealStartingState?.get("velocity")?.asDouble ?: 0.0
        val startRotDeg = idealStartingState?.get("rotation")?.asDouble ?: 0.0

        val goalEndState = if (root.has("goalEndState") && !root.get("goalEndState").isJsonNull) {
            root.getAsJsonObject("goalEndState")
        } else null
        val endVel = goalEndState?.get("velocity")?.asDouble ?: 0.0
        val endRotDeg = goalEndState?.get("rotation")?.asDouble ?: 0.0

        val parsedRotationTargets = mutableListOf<ParsedRotationTarget>()
        if (root.has("rotationTargets") && !root.get("rotationTargets").isJsonNull) {
            val arr = root.getAsJsonArray("rotationTargets")
            for (i in 0 until arr.size()) {
                val obj = arr.get(i).asJsonObject
                parsedRotationTargets.add(
                    ParsedRotationTarget(
                        waypointRelativePos = obj.get("waypointRelativePos").asDouble,
                        rotationDegrees = obj.get("rotationDegrees").asDouble
                    )
                )
            }
        }

        val parsedConstraintZones = mutableListOf<ParsedConstraintsZone>()
        if (root.has("constraintZones") && !root.get("constraintZones").isJsonNull) {
            val arr = root.getAsJsonArray("constraintZones")
            for (i in 0 until arr.size()) {
                val obj = arr.get(i).asJsonObject
                val minPos = obj.get("minWaypointRelativePos").asDouble
                val maxPos = obj.get("maxWaypointRelativePos").asDouble
                val cObj = obj.getAsJsonObject("constraints")
                val zMaxVel = cObj.get("maxVelocity").asDouble
                val zMaxAccel = cObj.get("maxAcceleration").asDouble
                parsedConstraintZones.add(ParsedConstraintsZone(minPos, maxPos, zMaxVel, zMaxAccel))
            }
        }

        val parsedPointTowardsZones = mutableListOf<ParsedPointTowardsZone>()
        if (root.has("pointTowardsZones") && !root.get("pointTowardsZones").isJsonNull) {
            val arr = root.getAsJsonArray("pointTowardsZones")
            for (i in 0 until arr.size()) {
                val obj = arr.get(i).asJsonObject
                val minPos = obj.get("minWaypointRelativePos").asDouble
                val maxPos = obj.get("maxWaypointRelativePos").asDouble
                val offset = if (obj.has("rotationOffset") && !obj.get("rotationOffset").isJsonNull) obj.get("rotationOffset").asDouble else 0.0
                val posObj = obj.getAsJsonObject("fieldPosition")
                val tx = posObj.get("x").asDouble
                val ty = posObj.get("y").asDouble
                parsedPointTowardsZones.add(ParsedPointTowardsZone(minPos, maxPos, offset, tx, ty))
            }
        }
        
        // Initial heading check
        var initialHeading = Rotation2d(0.0)
        if (parsedWaypoints.size > 1) {
            val wp1 = parsedWaypoints[0]
            val wp2 = parsedWaypoints[1]
            initialHeading = com.areslib.math.BezierSpline.evaluateHeading(wp1.anchor, wp1.nextControl, wp2.prevControl, wp2.anchor, 0.0)
        }

        val relativePositions = mutableListOf<Double>()
        relativePositions.add(0.0)

        // Add the very first point
        pathPoints.add(
            PathPoint(
                pose = Pose2d(parsedWaypoints[0].anchor.x, parsedWaypoints[0].anchor.y, initialHeading),
                velocityMps = defaultMaxVel,
                distanceMeters = 0.0
            )
        )

        // For each segment between two waypoints, generate the spline
        for (i in 0 until parsedWaypoints.size - 1) {
            val wp1 = parsedWaypoints[i]
            val wp2 = parsedWaypoints[i + 1]
            
            // P0 = wp1.anchor, P1 = wp1.nextControl, P2 = wp2.prevControl, P3 = wp2.anchor
            // Generate dense points along the curve
            val numSamples = 20
            for (step in 1..numSamples) {
                val t = step.toDouble() / numSamples
                val point = com.areslib.math.BezierSpline.evaluate(wp1.anchor, wp1.nextControl, wp2.prevControl, wp2.anchor, t)
                val heading = com.areslib.math.BezierSpline.evaluateHeading(wp1.anchor, wp1.nextControl, wp2.prevControl, wp2.anchor, t)
                
                // Calculate distance from previous point
                val prevPathPoint = pathPoints.last()
                val dx = point.x - prevPathPoint.pose.x
                val dy = point.y - prevPathPoint.pose.y
                accumulatedDistance += hypot(dx, dy)
                
                pathPoints.add(
                    PathPoint(
                        pose = Pose2d(point.x, point.y, heading),
                        velocityMps = defaultMaxVel,
                        distanceMeters = accumulatedDistance
                    )
                )
                relativePositions.add(i.toDouble() + t)
            }
        }

        // Calculate numerical curvature for each path point using central differences
        for (idx in 0 until pathPoints.size) {
            val nextIdx = if (idx < pathPoints.size - 1) idx + 1 else idx
            val prevIdx = if (idx > 0) idx - 1 else idx
            
            val pPrev = pathPoints[prevIdx]
            val pNext = pathPoints[nextIdx]
            
            val ds = pNext.distanceMeters - pPrev.distanceMeters
            val dTheta = pNext.pose.heading.radians - pPrev.pose.heading.radians
            val normDTheta = com.areslib.math.InputMath.wrapAngle(dTheta)
            
            val kappa = if (ds > 1e-4) normDTheta / ds else 0.0
            
            pathPoints[idx] = pathPoints[idx].copy(curvature = kappa)
        }

        // Decoupled Rotation & Point-Towards Zone heading interpolation
        val explicitRotations = arrayOfNulls<Double>(pathPoints.size)
        explicitRotations[0] = Math.toRadians(startRotDeg)
        explicitRotations[pathPoints.size - 1] = Math.toRadians(endRotDeg)

        // Point-towards zones get priority for explicit rotations
        for (idx in pathPoints.indices) {
            val pos = relativePositions[idx]
            for (zone in parsedPointTowardsZones) {
                if (pos >= zone.minWaypointRelativePos && pos <= zone.maxWaypointRelativePos) {
                    val dx = zone.x - pathPoints[idx].pose.x
                    val dy = zone.y - pathPoints[idx].pose.y
                    explicitRotations[idx] = Math.atan2(dy, dx) + Math.toRadians(zone.rotationOffset)
                    break
                }
            }
        }

        // Rotation targets map to closest point indices
        parsedRotationTargets.forEach { target ->
            var minDiff = Double.MAX_VALUE
            var bestIdx = -1
            for (k in pathPoints.indices) {
                val diff = Math.abs(relativePositions[k] - target.waypointRelativePos)
                if (diff < minDiff) {
                    minDiff = diff
                    bestIdx = k
                }
            }
            if (bestIdx != -1 && explicitRotations[bestIdx] == null) {
                explicitRotations[bestIdx] = Math.toRadians(target.rotationDegrees)
            }
        }

        // Sweep and interpolate headings
        for (idx in pathPoints.indices) {
            if (explicitRotations[idx] != null) {
                val p = pathPoints[idx]
                pathPoints[idx] = p.copy(pose = Pose2d(p.pose.x, p.pose.y, Rotation2d(explicitRotations[idx]!!)))
            } else {
                var prevIdx = 0
                for (k in idx - 1 downTo 0) {
                    if (explicitRotations[k] != null) {
                        prevIdx = k
                        break
                    }
                }
                var nextIdx = pathPoints.size - 1
                for (k in idx + 1 until pathPoints.size) {
                    if (explicitRotations[k] != null) {
                        nextIdx = k
                        break
                    }
                }
                val dCurr = pathPoints[idx].distanceMeters
                val dPrev = pathPoints[prevIdx].distanceMeters
                val dNext = pathPoints[nextIdx].distanceMeters
                val denom = dNext - dPrev
                val t = if (Math.abs(denom) < 1e-6) 0.0 else (dCurr - dPrev) / denom
                val t2 = (1.0 - Math.cos(t * Math.PI)) / 2.0
                val startAngle = explicitRotations[prevIdx]!!
                val endAngle = explicitRotations[nextIdx]!!
                val delta = com.areslib.math.InputMath.wrapAngle(endAngle - startAngle)
                val interpAngle = startAngle + delta * t2
                val p = pathPoints[idx]
                pathPoints[idx] = p.copy(pose = Pose2d(p.pose.x, p.pose.y, Rotation2d(interpAngle)))
            }
        }

        // Pass 1: Forward Sweep (Acceleration limit, Local Constraints, and Curvature limit)
        pathPoints[0] = pathPoints[0].copy(velocityMps = startVel)
        for (i in 1 until pathPoints.size) {
            val prev = pathPoints[i - 1]
            val curr = pathPoints[i]
            val dx = curr.distanceMeters - prev.distanceMeters

            // Retrieve active constraints for this segment
            val pos = relativePositions[i]
            var activeMaxVel = defaultMaxVel
            var activeMaxAccel = defaultMaxAccel
            for (zone in parsedConstraintZones) {
                if (pos >= zone.minWaypointRelativePos && pos <= zone.maxWaypointRelativePos) {
                    activeMaxVel = zone.maxVelocity
                    activeMaxAccel = zone.maxAcceleration
                    break
                }
            }

            // Apply centripetal curvature limit: a = v^2 / R => v = sqrt(a * R)
            val kappa = curr.curvature
            val pointMaxVel = if (Math.abs(kappa) > 1e-4) {
                val radius = 1.0 / Math.abs(kappa)
                minOf(activeMaxVel, Math.sqrt(activeMaxAccel * radius))
            } else {
                activeMaxVel
            }

            val maxReachable = com.areslib.math.KinematicsMath.finalVelocity(prev.velocityMps, activeMaxAccel, dx)
            val newVel = minOf(pointMaxVel, maxReachable)
            pathPoints[i] = curr.copy(velocityMps = newVel)
        }

        // Pass 2: Backward Sweep (Deceleration limit, Local Constraints, and Curvature limit)
        pathPoints[pathPoints.size - 1] = pathPoints[pathPoints.size - 1].copy(velocityMps = endVel)
        for (i in pathPoints.size - 2 downTo 0) {
            val next = pathPoints[i + 1]
            val curr = pathPoints[i]
            val dx = next.distanceMeters - curr.distanceMeters

            // Retrieve active constraints for this segment
            val pos = relativePositions[i]
            var activeMaxVel = defaultMaxVel
            var activeMaxAccel = defaultMaxAccel
            for (zone in parsedConstraintZones) {
                if (pos >= zone.minWaypointRelativePos && pos <= zone.maxWaypointRelativePos) {
                    activeMaxVel = zone.maxVelocity
                    activeMaxAccel = zone.maxAcceleration
                    break
                }
            }

            // Apply centripetal curvature limit
            val kappa = curr.curvature
            val pointMaxVel = if (Math.abs(kappa) > 1e-4) {
                val radius = 1.0 / Math.abs(kappa)
                minOf(activeMaxVel, Math.sqrt(activeMaxAccel * radius))
            } else {
                activeMaxVel
            }

            val maxReachable = com.areslib.math.KinematicsMath.finalVelocity(next.velocityMps, activeMaxAccel, dx)
            val newVel = minOf(curr.velocityMps, minOf(pointMaxVel, maxReachable))
            pathPoints[i] = curr.copy(velocityMps = newVel)
        }

        val pathEvents = mutableListOf<PathEvent>()
        if (root.has("eventMarkers") && !root.get("eventMarkers").isJsonNull) {
            val markersArray = root.getAsJsonArray("eventMarkers")
            val numSamples = 20
            
            for (i in 0 until markersArray.size()) {
                val marker = markersArray.get(i).asJsonObject
                if (!marker.has("waypointRelativePos") || marker.get("waypointRelativePos").isJsonNull) continue
                
                val pos = marker.get("waypointRelativePos").asDouble
                
                var commandName = "Unknown"
                if (marker.has("command") && !marker.get("command").isJsonNull) {
                    val cmd = marker.getAsJsonObject("command")
                    if (cmd.has("name") && !cmd.get("name").isJsonNull) {
                        commandName = cmd.get("name").asString
                    }
                }
                
                var targetIndex = (pos * numSamples).toInt()
                if (targetIndex >= pathPoints.size) targetIndex = pathPoints.size - 1
                if (targetIndex < 0) targetIndex = 0
                
                val triggerDist = pathPoints[targetIndex].distanceMeters
                pathEvents.add(PathEvent(commandName, triggerDist))
            }
        }

        return Path(pathPoints, pathEvents)
    }

    private data class WaypointData(
        val anchor: com.areslib.math.Translation2d,
        val prevControl: com.areslib.math.Translation2d,
        val nextControl: com.areslib.math.Translation2d
    )

    private data class ParsedRotationTarget(
        val waypointRelativePos: Double,
        val rotationDegrees: Double
    )

    private data class ParsedConstraintsZone(
        val minWaypointRelativePos: Double,
        val maxWaypointRelativePos: Double,
        val maxVelocity: Double,
        val maxAcceleration: Double
    )

    private data class ParsedPointTowardsZone(
        val minWaypointRelativePos: Double,
        val maxWaypointRelativePos: Double,
        val rotationOffset: Double,
        val x: Double,
        val y: Double
    )
}
