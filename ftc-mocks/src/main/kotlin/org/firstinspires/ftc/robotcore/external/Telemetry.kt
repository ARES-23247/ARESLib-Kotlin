@file:Suppress("UNUSED_PARAMETER")
package org.firstinspires.ftc.robotcore.external

/**
 * Interface implementation for Telemetry.
 *
 * Real-time telemetry streaming, diagnostic logging, and NetworkTables 4 communication handler.
 */
interface Telemetry {
    /**
     * Interface implementation for Item.
     *
     * Real-time telemetry streaming, diagnostic logging, and NetworkTables 4 communication handler.
     */
    interface Item
    /**
     * addData declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun addData(caption: String, value: Any?): Item?
    /**
     * addData declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun addData(caption: String, format: String, vararg args: Any?): Item?
    /**
     * update declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun update(): Boolean
}

/**
 * Class implementation for Mock Telemetry.
 *
 * Real-time telemetry streaming, diagnostic logging, and NetworkTables 4 communication handler.
 */
class MockTelemetry : Telemetry {
    private val buffer = mutableListOf<String>()
    
    // Exposed so DesktopSimLauncher can read and publish to NT4
    @Volatile
    var displayLines: List<String> = emptyList()
        private set

    /**
     * addData declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun addData(caption: String, value: Any?): Telemetry.Item? {
        synchronized(buffer) {
            buffer.add("$caption: $value")
        }
        return null
    }
    
    /**
     * addData declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
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
    
    /**
     * update declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun update(): Boolean {
        synchronized(buffer) {
            displayLines = buffer.toList()
            buffer.clear()
        }
        return true
    }
}
