package com.areslib.ftc

import com.areslib.state.RobotState
import com.areslib.state.DriveState
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull

class FtcDashboardAdapterTest {

    @Test
    fun `createPacket generates packet without exception`() {
        val state = RobotState(
            drive = DriveState(
                odometryX = 1.0,
                odometryY = 2.0,
                odometryHeading = Math.PI / 2
            )
        )
        
        val packet = FtcDashboardAdapter.createPacket(state)
        assertNotNull(packet)
        assertNotNull(packet.fieldOverlay)
    }
}
