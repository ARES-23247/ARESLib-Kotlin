package com.areslib.ftc

import com.qualcomm.robotcore.hardware.HardwareMap
import com.qualcomm.robotcore.hardware.DcMotor
import com.qualcomm.robotcore.hardware.DcMotorEx
import com.qualcomm.robotcore.hardware.DcMotorSimple
import com.areslib.kinematics.MecanumWheelSpeeds
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class MockDcMotorEx : DcMotorEx {
    override val currentPosition: Int = 0
    override val velocity: Double = 0.0
    override var direction: DcMotorSimple.Direction = DcMotorSimple.Direction.FORWARD
    override var mode: DcMotor.RunMode = DcMotor.RunMode.RUN_WITHOUT_ENCODER
    var currentPower: Double = 0.0
    
    override var power: Double
        get() = currentPower
        set(value) {
            currentPower = value
        }
}

class MecanumHardwareIOTest {
    @Test
    fun `apply sets power correctly on all four motors`() {
        val fl = MockDcMotorEx()
        val fr = MockDcMotorEx()
        val bl = MockDcMotorEx()
        val br = MockDcMotorEx()
        
        val hardwareMap = object : HardwareMap {
            @Suppress("UNCHECKED_CAST")
            override fun <T> get(classOrType: Class<out T>, deviceName: String): T {
                return when(deviceName) {
                    "fl" -> fl as T
                    "fr" -> fr as T
                    "bl" -> bl as T
                    "br" -> br as T
                    else -> throw IllegalArgumentException()
                }
            }
        }
        
        val io = MecanumHardwareIO(hardwareMap)
        
        // Assert init reversed right side
        assertEquals(DcMotorSimple.Direction.REVERSE, fr.direction)
        assertEquals(DcMotorSimple.Direction.REVERSE, br.direction)
        
        val speeds = MecanumWheelSpeeds(1.0, 0.5, -0.5, -1.0)
        io.apply(speeds)
        
        assertEquals(1.0, fl.currentPower, 0.001)
        assertEquals(0.5, fr.currentPower, 0.001)
        assertEquals(-0.5, bl.currentPower, 0.001)
        assertEquals(-1.0, br.currentPower, 0.001)
    }
}
