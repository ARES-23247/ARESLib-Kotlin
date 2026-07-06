package com.areslib.ftc.telemetry

import fi.iki.elonen.NanoHTTPD

fun main() {
    val server = object : NanoHTTPD(5002) {}
    server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
    println("Server started")
    Thread.sleep(1000)
    println("Stopping server...")
    server.stop()
    println("Server stopped")
}
