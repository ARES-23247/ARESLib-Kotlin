package org.frcforftc.networktables

import com.areslib.networktables.NT4Server as KotlinNT4Server
import com.areslib.networktables.NT4Entry as KotlinNT4Entry
import com.areslib.networktables.NT4Instance as KotlinNT4Instance

@Deprecated(
    message = "Use com.areslib.networktables.NT4Server instead",
    replaceWith = ReplaceWith("NT4Server", "com.areslib.networktables.NT4Server")
)
typealias NT4Server = KotlinNT4Server

@Deprecated(
    message = "Use com.areslib.networktables.NT4Entry instead",
    replaceWith = ReplaceWith("NT4Entry", "com.areslib.networktables.NT4Entry")
)
typealias NetworkTablesEntry = KotlinNT4Entry

@Deprecated(
    message = "Use com.areslib.networktables.NT4Instance instead",
    replaceWith = ReplaceWith("NT4Instance", "com.areslib.networktables.NT4Instance")
)
typealias NetworkTablesInstance = KotlinNT4Instance
