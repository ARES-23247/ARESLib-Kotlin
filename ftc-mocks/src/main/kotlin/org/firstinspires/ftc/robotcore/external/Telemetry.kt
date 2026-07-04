package org.firstinspires.ftc.robotcore.external

interface Telemetry {
    interface Item
    fun addData(caption: String, value: Any?): Item?
    fun addData(caption: String, format: String, vararg args: Any?): Item?
    fun update(): Boolean
}

class MockTelemetry : Telemetry {
    private val buffer = mutableListOf<String>()
    
    // Exposed so DesktopSimLauncher can read and publish to NT4
    val displayLines = mutableListOf<String>()

    override fun addData(caption: String, value: Any?): Telemetry.Item? {
        buffer.add("$caption: $value")
        return null
    }
    
    override fun addData(caption: String, format: String, vararg args: Any?): Telemetry.Item? {
        try {
            buffer.add("$caption: ${String.format(format, *args)}")
        } catch (e: Exception) {
            buffer.add("$caption: [Format Error]")
        }
        return null
    }
    
    override fun update(): Boolean {
        displayLines.clear()
        displayLines.addAll(buffer)
        buffer.clear()
        return true
    }
}
