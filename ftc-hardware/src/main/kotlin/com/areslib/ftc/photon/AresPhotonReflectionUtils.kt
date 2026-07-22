package com.areslib.ftc.photon

import java.lang.reflect.Field

/**
 * Object implementation for Ares Photon Reflection Utils.
 *
 * Hardware IO abstraction layer bridging physical robot sensors and actuators into immutable Redux state representations.
 */
object AresPhotonReflectionUtils {
    /**
     * getField declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun getField(clazz: Class<*>, fieldName: String): Field? {
        try {
            val f = clazz.getDeclaredField(fieldName)
            f.isAccessible = true
            return f
        } catch (e: NoSuchFieldException) {
            val superClass = clazz.superclass
            if (superClass != null) {
                return getField(superClass, fieldName)
            }
        }
        return null
    }

    /**
     * getField declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun getField(clazz: Class<*>, target: Class<*>): Field? {
        for (f in clazz.declaredFields) {
            if (f.type == target) {
                f.isAccessible = true
                return f
            }
        }
        val superClass = clazz.superclass
        if (superClass != null) {
            return getField(superClass, target)
        }
        return null
    }

    /**
     * deepCopy declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun deepCopy(org: Any, target: Any) {
        val fields = org.javaClass.declaredFields
        for (f in fields) {
            f.isAccessible = true
            val f2 = getField(target.javaClass, f.name)
            if (f2 != null) {
                f2.isAccessible = true
                try {
                    f2.set(target, f.get(org))
                } catch (e: IllegalAccessException) {
                    e.printStackTrace()
                }
            }
        }
    }
}
