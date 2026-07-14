@file:Suppress("UNUSED_PARAMETER")
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
    @Volatile
    var displayLines: List<String> = emptyList()
        private set

    override fun addData(caption: String, value: Any?): Telemetry.Item? {
        synchronized(buffer) {
            buffer.add("$caption: $value")
        }
        return null
    }
    
    override fun addData(caption: String, format: String, vararg args: Any?): Telemetry.Item? {
        synchronized(buffer) {
            try {
                buffer.add("$caption: ${String.format(format, *args)}")
            } catch (e: Exception) {
                buffer.add("$caption: [Format Error]")
            }
        }
        return null
    }
    
    override fun update(): Boolean {
        synchronized(buffer) {
            displayLines = buffer.toList()
            buffer.clear()
        }
        return true
    }
}
