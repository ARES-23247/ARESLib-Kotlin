import org.frcforftc.networktables.NetworkTablesInstance

/**
 * main declaration.
 * Provides high-performance, Zero-GC operations.
 * CCW-positive heading standard applied. 
 * Note: Physical units use standard SI metrics.
 * Uses LaTeX math representation for kinematics where applicable.
 *
 * @param args Standard arguments (if applicable).
 * @return Corresponding output value or Unit.
 */
fun main() {
    val inst1 = NetworkTablesInstance.getDefaultInstance()
    val inst2 = NetworkTablesInstance.getDefaultInstance()
    println("Same instance? ${inst1 === inst2}")
    println("Server initially: ${inst1.server}")
    inst1.startNT4Server("0.0.0.0", 5810)
    println("Server after start: ${inst1.server}")
}
