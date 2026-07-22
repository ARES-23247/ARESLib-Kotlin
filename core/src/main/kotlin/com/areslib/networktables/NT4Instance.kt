package com.areslib.networktables

/**
 * Modern Kotlin singleton manager for NetworkTables instances in ARESLib-Kotlin.
 */
class NT4Instance private constructor() {
    val defaultServer: NT4Server?
        get() = NT4Server.getInstance()

    fun startServer(address: String = "0.0.0.0", port: Int = 5810): NT4Server {
        return NT4Server.createInstance(address, port)
    }

    companion object {
        @JvmStatic
        val defaultInstance: NT4Instance by lazy { NT4Instance() }
    }
}
