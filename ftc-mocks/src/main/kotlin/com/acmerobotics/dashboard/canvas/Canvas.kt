package com.acmerobotics.dashboard.canvas

/**
 * Mock representation of FTC Dashboard [Canvas].
 */
open class Canvas {
    /**
     * drawCircle declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun drawCircle(x: Double, y: Double, radius: Double) {}
    /**
     * drawLine declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun drawLine(x1: Double, y1: Double, x2: Double, y2: Double) {}
    /**
     * setStroke declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun setStroke(color: String) {}
}
