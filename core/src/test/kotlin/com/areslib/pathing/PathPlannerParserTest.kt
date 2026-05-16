package com.areslib.pathing

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PathPlannerParserTest {

    @Test
    fun `parsePath extracts waypoints and calculates distance`() {
        val mockJson = """
            {
              "waypoints": [
                {"anchor": {"x": 0.0, "y": 0.0}},
                {"anchor": {"x": 3.0, "y": 4.0}}
              ]
            }
        """.trimIndent()
        
        val path = PathPlannerParser.parsePath(mockJson)
        
        assertNotNull(path)
        assertEquals(2, path.points.size)
        
        val p1 = path.points[0]
        assertEquals(0.0, p1.pose.x)
        assertEquals(0.0, p1.pose.y)
        assertEquals(0.0, p1.distanceMeters)
        
        val p2 = path.points[1]
        assertEquals(3.0, p2.pose.x)
        assertEquals(4.0, p2.pose.y)
        assertEquals(5.0, p2.distanceMeters, 0.001) // hypot(3, 4) = 5
    }
}
