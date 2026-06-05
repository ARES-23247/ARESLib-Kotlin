package com.areslib.ftc

import com.areslib.hardware.vision.VisionIO
import com.areslib.hardware.vision.VisionIOInputs
import com.areslib.hardware.vision.CompositeVisionIO
import com.areslib.state.VisionMeasurement
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CompositeVisionIOTest {

    class MockVisionIO(
        private val connected: Boolean,
        private val measurementsList: List<VisionMeasurement>
    ) : VisionIO {
        override fun updateInputs(inputs: VisionIOInputs) {
            inputs.isConnected = connected
            inputs.measurements = measurementsList
        }
    }

    @Test
    fun testAggregateMeasurements() {
        val measurement1 = VisionMeasurement(timestampMs = 100L, tagId = 1)
        val measurement2 = VisionMeasurement(timestampMs = 200L, tagId = 2)
        val measurement3 = VisionMeasurement(timestampMs = 300L, tagId = 3)

        val io1 = MockVisionIO(connected = true, measurementsList = listOf(measurement1, measurement2))
        val io2 = MockVisionIO(connected = false, measurementsList = emptyList())
        val io3 = MockVisionIO(connected = true, measurementsList = listOf(measurement3))

        val composite = CompositeVisionIO(listOf(io1, io2, io3))
        val inputs = VisionIOInputs()
        composite.updateInputs(inputs)

        assertTrue(inputs.isConnected)
        assertEquals(3, inputs.measurements.size)
        assertEquals(listOf(measurement1, measurement2, measurement3), inputs.measurements)
    }

    @Test
    fun testNoneConnected() {
        val io1 = MockVisionIO(connected = false, measurementsList = emptyList())
        val io2 = MockVisionIO(connected = false, measurementsList = emptyList())

        val composite = CompositeVisionIO(listOf(io1, io2))
        val inputs = VisionIOInputs()
        composite.updateInputs(inputs)

        assertFalse(inputs.isConnected)
        assertTrue(inputs.measurements.isEmpty())
    }
}
