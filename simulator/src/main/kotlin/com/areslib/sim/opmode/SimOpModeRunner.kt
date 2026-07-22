package com.areslib.sim.opmode

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode
import java.io.File
import java.util.jar.JarFile

/**
 * Handles fast, zero-dependency OpMode discovery (scanning classpath for @TeleOp and @Autonomous annotations),
 * publishing lists to NetworkTables, and executing OpModes on background threads.
 */
object SimOpModeRunner {

    /**
     * Scans classpath for OpModes and publishes JSON lists to NT4 for the Driver Station UI.
     */
    fun scanAndPublishOpModes() {
        try {
            val disabledClass = try {
                @Suppress("UNCHECKED_CAST")
                Class.forName("com.qualcomm.robotcore.eventloop.opmode.Disabled") as Class<out Annotation>
            } catch (_: Exception) { null }

            val disabledOpModes = disabledClass?.let { findAnnotatedClasses(it).map { c -> c.name }.toSet() } ?: emptySet()

            val teleOpClass = try {
                @Suppress("UNCHECKED_CAST")
                Class.forName("com.qualcomm.robotcore.eventloop.opmode.TeleOp") as Class<out Annotation>
            } catch (_: Exception) { null }

            val autonomousClass = try {
                @Suppress("UNCHECKED_CAST")
                Class.forName("com.qualcomm.robotcore.eventloop.opmode.Autonomous") as Class<out Annotation>
            } catch (_: Exception) { null }

            var teleops = teleOpClass?.let {
                findAnnotatedClasses(it)
                    .filter { opMode -> !opMode.name.startsWith("org.firstinspires.ftc.robotcontroller") }
                    .filter { opMode -> disabledClass == null || (!opMode.isAnnotationPresent(disabledClass) && opMode.name !in disabledOpModes) }
                    .map { opMode -> opMode.name }
            } ?: emptyList()

            var autos = autonomousClass?.let {
                findAnnotatedClasses(it)
                    .filter { opMode -> !opMode.name.startsWith("org.firstinspires.ftc.robotcontroller") }
                    .filter { opMode -> disabledClass == null || (!opMode.isAnnotationPresent(disabledClass) && opMode.name !in disabledOpModes) }
                    .map { opMode -> opMode.name }
            } ?: emptyList()

            // Include default ARESLib integration test opmode if list is empty
            if (teleops.isEmpty()) {
                teleops = listOf("com.areslib.ftc.hardware.AresHardwareTestOpMode")
            }

            val gson = com.google.gson.Gson()
            val teleOpJson = gson.toJson(teleops)
            val autoJson = gson.toJson(autos)

            // Ensure NT4Server is active
            if (com.areslib.networktables.NT4Server.getInstance() == null) {
                com.areslib.networktables.NT4Instance.defaultInstance.startServer("0.0.0.0", 5810)
            }

            // Publish to pure Java NT4Server for ARES-Analytics dashboard
            com.areslib.networktables.NT4Server.publishTopic("ARES/DriverStation/TeleOpList", teleOpJson)
            com.areslib.networktables.NT4Server.publishTopic("ARES/DriverStation/AutonomousList", autoJson)

            // Publish to WPILib NT4 instance for AdvantageScope compatibility if active
            try {
                val ntInst = edu.wpi.first.networktables.NetworkTableInstance.getDefault()
                val teleOpTopic = ntInst.getStringTopic("ARES/DriverStation/TeleOpList")
                teleOpTopic.publish().set(teleOpJson)
                teleOpTopic.setRetained(true)

                val autoTopic = ntInst.getStringTopic("ARES/DriverStation/AutonomousList")
                autoTopic.publish().set(autoJson)
                autoTopic.setRetained(true)
            } catch (_: Throwable) {}

            println("[Simulator] Published ${teleops.size} TeleOps and ${autos.size} Autos to NT4")
        } catch (e: Exception) {
            println("[Simulator] Error scanning OpModes: ${e.message}")
        }
    }

    private fun findAnnotatedClasses(annotationClass: Class<out Annotation>): List<Class<*>> {
        val result = ArrayList<Class<*>>()
        val classPath = System.getProperty("java.class.path", "")
        val entries = classPath.split(File.pathSeparator).filter { it.isNotEmpty() }

        for (entry in entries) {
            val file = File(entry)
            if (!file.exists()) continue
            if (file.isDirectory) {
                scanDir(file, file, annotationClass, result)
            } else if (file.name.endsWith(".jar")) {
                scanJar(file, annotationClass, result)
            }
        }
        return result
    }

    private fun scanDir(baseDir: File, currentDir: File, annotationClass: Class<out Annotation>, result: MutableList<Class<*>>) {
        val files = currentDir.listFiles() ?: return
        for (f in files) {
            if (f.isDirectory) {
                scanDir(baseDir, f, annotationClass, result)
            } else if (f.name.endsWith(".class") && !f.name.contains('$')) {
                val relativePath = baseDir.toURI().relativize(f.toURI()).path
                val className = relativePath.removeSuffix(".class").replace('/', '.').replace('\\', '.')
                tryLoadClass(className, annotationClass, result)
            }
        }
    }

    private fun scanJar(jarFile: File, annotationClass: Class<out Annotation>, result: MutableList<Class<*>>) {
        try {
            JarFile(jarFile).use { jar ->
                val entries = jar.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name
                    if (name.endsWith(".class") && !name.contains('$')) {
                        val className = name.removeSuffix(".class").replace('/', '.')
                        tryLoadClass(className, annotationClass, result)
                    }
                }
            }
        } catch (_: Throwable) {}
    }

    private fun tryLoadClass(className: String, annotationClass: Class<out Annotation>, result: MutableList<Class<*>>) {
        if (className.startsWith("java.") || className.startsWith("javax.") || className.startsWith("jdk.") || className.startsWith("org.lwjgl") || className.startsWith("org.dyn4j")) return
        try {
            val clazz = Class.forName(className, false, Thread.currentThread().contextClassLoader)
            if (clazz.isAnnotationPresent(annotationClass)) {
                result.add(clazz)
            }
        } catch (_: Throwable) {}
    }

    /**
     * Dynamically instantiates an OpMode class by name, with robust package name resolution fallback.
     */
    fun createOpModeInstance(
        opModeArg: LinearOpMode?,
        opModeClassName: String?
    ): LinearOpMode? {
        if (opModeArg != null) return opModeArg
        if (opModeClassName.isNull_or_blank()) return null
        
        val candidates = listOf(
            opModeClassName!!,
            "org.firstinspires.ftc.teamcode.opmodes.$opModeClassName",
            "org.firstinspires.ftc.teamcode.$opModeClassName",
            "com.areslib.ftc.hardware.$opModeClassName"
        )
        
        for (candidate in candidates) {
            try {
                val clazz = Class.forName(candidate)
                val instance = clazz.getDeclaredConstructor().newInstance() as? LinearOpMode
                if (instance != null) {
                    println("[Simulator] Successfully instantiated OpMode class: $candidate")
                    return instance
                }
            } catch (_: Exception) {}
        }

        println("[Simulator] Class $opModeClassName not found. Falling back to AresHardwareTestOpMode.")
        return try {
            com.areslib.ftc.hardware.AresHardwareTestOpMode()
        } catch (e: Exception) {
            System.err.println("Failed to instantiate fallback OpMode: ${e.message}")
            null
        }
    }

    private fun String?.isNull_or_blank(): Boolean = this == null || this.trim().isEmpty()
}
