package com.qualcomm.hardware.limelightvision

open class LLResult {
    val ta: Double = 0.0
    val tx: Double = 0.0
    val ty: Double = 0.0
    // botpose in meters and degrees: [x, y, z, roll, pitch, yaw]
    val botpose: DoubleArray = DoubleArray(6)
}

open class Limelight3A {
    fun start() {}
    fun getLatestResult(): LLResult? = null
}
