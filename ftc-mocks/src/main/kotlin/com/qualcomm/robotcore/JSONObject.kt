@file:Suppress("UNUSED_PARAMETER")
package org.json

/**
 * Class implementation for J S O N Object.
 *
 * Robotics framework control component.
 */
open class JSONObject {
    constructor()
    constructor(json: String)
    /**
     * put declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun put(key: String, value: Any?): JSONObject = this
    /**
     * get declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun get(key: String): Any = ""
    /**
     * optString declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun optString(key: String, defaultValue: String): String = ""
    /**
     * toString declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    override fun toString(): String = "{}"
}
