package org.firstinspires.ftc.robotcore.external

interface Telemetry {
    interface Item
    fun addData(caption: String, value: Any?): Item?
    fun addData(caption: String, format: String, vararg args: Any?): Item?
    fun update(): Boolean
}

class MockTelemetry : Telemetry {
    override fun addData(caption: String, value: Any?): Telemetry.Item? = null
    override fun addData(caption: String, format: String, vararg args: Any?): Telemetry.Item? = null
    override fun update(): Boolean = true
}
