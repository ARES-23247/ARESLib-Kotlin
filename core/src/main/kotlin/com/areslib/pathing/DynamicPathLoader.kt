package com.areslib.pathing

import java.io.File
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.IOException

/**
 * Highly resilient, cross-platform PathPlanner path loader.
 * Prioritizes dynamic filesystem scanning (ideal for wireless ADB tuning pushes on Android FTC hubs
 * or FRC RoboRIOs) before gracefully falling back to embedded classpath resources or throwing
 * clean diagnostic errors.
 */
object DynamicPathLoader {

    private val SEARCH_PATHS = listOf(
        "/sdcard/FIRST/tuning/paths",
        "/sdcard/FIRST/paths",
        "src/main/deploy/pathplanner/paths",
        "deploy/pathplanner/paths",
        "src/main/resources/deploy/pathplanner/paths",
        "../deploy/pathplanner/paths",
        "../../deploy/pathplanner/paths",
        "src/main/assets/pathplanner/paths",
        "TeamCode/src/main/assets/pathplanner/paths",
        "../TeamCode/src/main/assets/pathplanner/paths",
        "../../TeamCode/src/main/assets/pathplanner/paths"
    )

    /**
     * Attempts to find and parse a PathPlanner .path file dynamically by its name.
     * Searches standard filesystem locations first, falling back to classpath streams if missing.
     *
     * @param pathName The name of the path (without the .path extension).
     * @return The constructed [Path] mathematical trajectory.
     * @throws IOException If the path file cannot be found in any search target or is unreadable.
     */
    fun loadPath(pathName: String): Path {
        var jsonString: String? = null
        val fileName = "$pathName.path"

        // 1. Filesystem Search Pass
        for (dirPath in SEARCH_PATHS) {
            val file = File(dirPath, fileName)
            if (file.exists() && file.isFile) {
                try {
                    jsonString = file.readText(Charsets.UTF_8)
                    break
                } catch (e: Exception) {
                    // Log or print warning, then proceed to next candidate
                    System.err.println("WARN: Failed to read filesystem path at ${file.absolutePath}: ${e.message}")
                }
            }
        }

        // 2. Classpath Fallback Pass
        if (jsonString == null) {
            val classpathCandidates = listOf(
                "/deploy/pathplanner/paths/$fileName",
                "/$fileName",
                "deploy/pathplanner/paths/$fileName"
            )

            for (resourcePath in classpathCandidates) {
                val inputStream = javaClass.getResourceAsStream(resourcePath)
                if (inputStream != null) {
                    try {
                        jsonString = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { it.readText() }
                        break
                    } catch (e: Exception) {
                        System.err.println("WARN: Failed to read classpath resource at $resourcePath: ${e.message}")
                    }
                }
            }
        }

        // 3. Complete Fallback or Validation Failure
        if (jsonString == null) {
            val scannedLocations = SEARCH_PATHS.map { File(it, fileName).absolutePath } + 
                                   listOf("/deploy/pathplanner/paths/$fileName", "/$fileName")
            throw IOException(
                "Could not locate path '$pathName' anywhere in search space!\n" +
                "Scanned locations:\n" + scannedLocations.joinToString("\n") { "  - $it" }
            )
        }

        return PathPlannerParser.parsePath(jsonString)
    }
}
