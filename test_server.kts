import com.areslib.logging.LogManagerServer
val server = LogManagerServer(5002)
server.start(fi.iki.elonen.NanoHTTPD.SOCKET_READ_TIMEOUT, false)
println("Server started")
Thread.sleep(60000)
