package com.areslib.sim.opmode

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode
import edu.wpi.first.networktables.NetworkTableInstance
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
     * scanAndPublishOpModes declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun scanAndPublishOpModes() {
        try {
            val urls = mutableListOf<java.net.URL>()
            urls.addAll(ClasspathHelper.forJavaClassPath())
            urls.addAll(ClasspathHelper.forClassLoader(Thread.currentThread().contextClassLoader))

            val reflections = Reflections(
                ConfigurationBuilder()
                    .setUrls(urls)
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

            val teleops = teleOpClass?.let {
                reflections.getTypesAnnotatedWith(it)
                    .filter { opMode -> !opMode.name.startsWith("org.firstinspires.ftc.robotcontroller") }
                    .filter { opMode -> disabledClass == null || (!opMode.isAnnotationPresent(disabledClass) && opMode.name !in disabledOpModes) }
                    .map { opMode -> opMode.name }
            } ?: emptyList()

            val autos = autonomousClass?.let {
                reflections.getTypesAnnotatedWith(it)
                    .filter { opMode -> !opMode.name.startsWith("org.firstinspires.ftc.robotcontroller") }
                    .filter { opMode -> disabledClass == null || (!opMode.isAnnotationPresent(disabledClass) && opMode.name !in disabledOpModes) }
                    .map { opMode -> opMode.name }
            } ?: emptyList()

            val gson = com.google.gson.Gson()
            val ntInst = NetworkTableInstance.getDefault()

            val teleOpTopic = ntInst.getStringTopic("ARES/DriverStation/TeleOpList")
            val teleOpListPub = teleOpTopic.publish()
            teleOpListPub.set(gson.toJson(teleops))
            teleOpTopic.setRetained(true)

            val autoTopic = ntInst.getStringTopic("ARES/DriverStation/AutonomousList")
            val autoListPub = autoTopic.publish()
            autoListPub.set(gson.toJson(autos))
            autoTopic.setRetained(true)

            println("[Simulator] Published ${teleops.size} TeleOps and ${autos.size} Autos to NT4")
        } catch (e: Exception) {
            println("[Simulator] Error scanning OpModes: ${e.message}")
        }
    }

    /**
     * createOpModeInstance declaration.
     *
     * @param args Standard arguments (if applicable).
     * @return Corresponding output value or Unit.
     */
    fun createOpModeInstance(
        opModeArg: LinearOpMode?,
        opModeClassName: String?
    ): LinearOpMode? {
        if (opModeArg != null) return opModeArg
        if (opModeClassName == null) return null
        return try {
            val clazz = Class.forName(opModeClassName)
            clazz.getDeclaredConstructor().newInstance() as LinearOpMode
        } catch (e: Exception) {
            System.err.println("Failed to instantiate OpMode $opModeClassName: ${e.message}")
            null
        }
    }
}
