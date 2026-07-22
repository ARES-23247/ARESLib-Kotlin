package com.areslib.telemetry

/**
 * Lightweight abstraction for human-readable driver station telemetry.
 * Prevents reflection overhead in hardware facades while maintaining FTC OpMode independence.
 */
interface LocalTelemetry {
    /**
     * addData declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun addData(caption: String, value: Any?)
    /**
     * update declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun update()
}
