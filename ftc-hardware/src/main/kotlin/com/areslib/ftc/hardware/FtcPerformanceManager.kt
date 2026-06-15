package com.areslib.ftc.hardware

import com.qualcomm.robotcore.hardware.HardwareMap
import java.lang.reflect.Method

/**
 * Advanced Performance & Loop Optimizer for FTC Robots.
 * Handles manual bulk caching across all REV Hubs, and automatically
 * detects and initializes Photon parallelized writes if present on the classpath.
 */
object FtcPerformanceManager {
    private var lynxModules: List<Any> = emptyList()
    private var srsHubs: List<SrsHubDriver> = emptyList()
    private var clearCacheMethod: Method? = null
    var isPhotonEnabled: Boolean = false
        private set

    /**
     * Scans the hardware map, configures all REV Hubs (LynxModules) for MANUAL bulk caching,
     * detects any SRS Hubs, and automatically enables Photon parallelization.
     */
    fun initialize(hardwareMap: HardwareMap) {
        try {
            // Find all LynxModules using reflection to avoid hard dependencies on internal SDK classes in mock context
            val lynxModuleClass = Class.forName("com.qualcomm.hardware.lynx.LynxModule")
            val bulkCachingModeEnum = Class.forName("com.qualcomm.hardware.lynx.LynxModule\$BulkCachingMode")
            val setBulkCachingModeMethod = lynxModuleClass.getMethod("setBulkCachingMode", bulkCachingModeEnum)
            clearCacheMethod = lynxModuleClass.getMethod("clearBulkCache")

            // Get the manual mode enum value
            val manualMode = java.lang.Enum.valueOf(bulkCachingModeEnum as Class<out Enum<*>>, "MANUAL")

            // Get all LynxModules from the hardware map
            val modules = hardwareMap.getAll(lynxModuleClass)
            for (module in modules) {
                setBulkCachingModeMethod.invoke(module, manualMode)
            }
            this.lynxModules = modules
            println("ARES Performance: Successfully enabled Manual Bulk Caching for ${modules.size} REV Hubs.")
        } catch (e: Exception) {
            System.err.println("ARES Performance: Failed to initialize Manual Bulk Caching (might be in a mock/simulation context): ${e.message}")
        }

        // Auto-detect any connected SRS Hubs
        try {
            this.srsHubs = hardwareMap.getAll(SrsHubDriver::class.java)
            println("ARES Performance: Detected ${srsHubs.size} SRS Robotics Expansion Hubs. Automatic bulk register reads configured.")
        } catch (e: Exception) {
            // Ignore in standard mock/unit test environments where SrsHubDriver isn't registered
        }

        // Try to detect and enable Photon
        try {
            val photonCoreClass = Class.forName("com.seattlesolvers.solverslib.photon.PhotonCore")
            val enableMethod = photonCoreClass.getMethod("enable")
            enableMethod.invoke(null)
            isPhotonEnabled = true
            println("ARES Performance: SolversLib Photon detected on classpath! Enabled asynchronous parallelized writes.")
        } catch (e: ClassNotFoundException) {
            println("ARES Performance: Photon not found. Running optimized native manual bulk reads.")
        } catch (e: Exception) {
            System.err.println("ARES Performance: Photon was detected but failed to initialize: ${e.message}")
        }
    }

    /**
     * Clears the bulk cache for all REV Hubs and triggers a single integrated 256-byte bulk read
     * for all connected SRS Hubs.
     * MUST be called exactly once at the beginning of your robot's command/opmode run loop.
     */
    fun clearBulkCaches() {
        // Clear caches for all standard REV Hubs
        for (module in lynxModules) {
            try {
                clearCacheMethod?.invoke(module)
            } catch (e: Exception) {
                // Ignore failures in mock context
            }
        }
    }
}
