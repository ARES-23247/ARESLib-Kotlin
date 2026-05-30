package com.qualcomm.hardware.gobilda

open class GoBildaPinpointDriver {
    @JvmField var posX: Double = 0.0
    @JvmField var posY: Double = 0.0
    @JvmField var heading: Double = 0.0
    @JvmField var velX: Double = 0.0
    @JvmField var velY: Double = 0.0
    @JvmField var headingVelocity: Double = 0.0
    
    fun update() {}
    fun resetPosAndIMU() {}
}
