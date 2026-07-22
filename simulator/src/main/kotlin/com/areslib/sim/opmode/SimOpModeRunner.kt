package com.areslib.sim.opmode

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode
import org.reflections.Reflections
import org.reflections.scanners.Scanners
import org.reflections.util.ClasspathHelper
import org.reflections.util.ConfigurationBuilder

/**
 * Handles reflection-based OpMode discovery (scanning for @TeleOp and @Autonomous annotations),
 * publishing lists to NetworkTables, and executing OpModes on background threads.
 */
object SimOpModeRunner {

    /**
     * Scans classpath for OpModes and publishes JSON lists to NT4 for the Driver Station UI.
     */
    fun scanAndPublishOpModes() {
        try {
            val rawUrls = mutableListOf<java.net.URL>()
            rawUrls.addAll(ClasspathHelper.forJavaClassPath())
            rawUrls.addAll(ClasspathHelper.forClassLoader(Thread.currentThread().contextClassLoader))

            val validUrls = rawUrls.filter { url ->
                try {
                    val file = java.io.File(url.toURI())
                    file.exists()
                } catch (_: Exception) {
                    false
                }
            }.distinct()

            val reflections = Reflections(
                ConfigurationBuilder()
                    .setUrls(validUrls)
                    .setScanners(Scanners.TypesAnnotated)
            )

            val disabledClass = try {
                @Suppress("UNCHECKED_CAST")
                Class.forName("com.qualcomm.robotcore.eventloop.opmode.Disabled") as Class<out Annotation>
            } catch (_: Exception) { null }

            val disabledOpModes = disabledClass?.let { reflections.getTypesAnnotatedWith(it).map { opMode -> opMode.name }.toSet() } ?: emptySet()

            val teleOpClass = try {
                @Suppress("UNCHECKED_CAST")
                Class.forName("com.qualcomm.robotcore.eventloop.opmode.TeleOp") as Class<out Annotation>
            } catch (_: Exception) { null }

            val autonomousClass = try {
                @Suppress("UNCHECKED_CAST")
                Class.forName("com.qualcomm.robotcore.eventloop.opmode.Autonomous") as Class<out Annotation>
            } catch (_: Exception) { null }

            var teleops = teleOpClass?.let {
                reflections.getTypesAnnotatedWith(it)
                    .filter { opMode -> !opMode.name.startsWith("org.firstinspires.ftc.robotcontroller") }
                    .filter { opMode -> disabledClass == null || (!opMode.isAnnotationPresent(disabledClass) && opMode.name !in disabledOpModes) }
                    .map { opMode -> opMode.name }
            } ?: emptyList()

            var autos = autonomousClass?.let {
                reflections.getTypesAnnotatedWith(it)
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
