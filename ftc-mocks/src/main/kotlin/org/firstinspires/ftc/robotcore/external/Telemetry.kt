package org.firstinspires.ftc.robotcore.external

open class Telemetry {
    interface Item
    open fun addData(caption: String, value: Any?): Item? = null
    open fun addData(caption: String, format: String, vararg args: Any?): Item? = null
    open fun update(): Boolean = true
}
