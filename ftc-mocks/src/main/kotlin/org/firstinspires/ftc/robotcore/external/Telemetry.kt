package org.firstinspires.ftc.robotcore.external

open class Telemetry {
    open fun addData(caption: String, value: Any?): Any? = null
    open fun addData(caption: String, format: String, vararg args: Any?): Any? = null
    open fun update(): Boolean = true
}
