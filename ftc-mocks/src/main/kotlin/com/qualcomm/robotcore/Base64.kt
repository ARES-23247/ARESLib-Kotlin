@file:Suppress("UNUSED_PARAMETER")
package android.util

/**
 * Object implementation for Base64.
 *
 * Robotics framework control component.
 */
object Base64 {
    @JvmField
    val NO_WRAP = 2
    
    @JvmStatic
    /**
     * encodeToString declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun encodeToString(input: ByteArray, flags: Int): String {
        return java.util.Base64.getEncoder().encodeToString(input)
    }
}
