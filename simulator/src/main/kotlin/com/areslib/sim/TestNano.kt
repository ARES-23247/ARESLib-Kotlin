package com.areslib.sim

import fi.iki.elonen.NanoHTTPD

object TestNano {
    @JvmStatic
    fun main(args: Array<String>) {
        println("Testing NanoHTTPD...")
        val server = object : NanoHTTPD(5003) {}
        server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
        println("Started. Waiting 1s...")
        Thread.sleep(1000)
        println("Stopping...")
        server.stop()
        println("Stopped successfully!")
    }
}
