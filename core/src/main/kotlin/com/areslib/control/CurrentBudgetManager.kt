package com.areslib.control

import com.areslib.hardware.MotorIO

/**
 * System-level current budget manager for FTC robots.
 *
 * Prevents blowing the 20A main fuse by estimating total robot current draw
 * and applying graduated power scaling when the budget is exceeded.
 *
 * ## Why estimation instead of direct reads?
 * On REV hubs, `motor.getCurrent()` is a **blocking I2C call** (~2-3ms per motor).
 * With 4+ motors, that's 8-16ms — nearly the entire 20ms loop budget at 50Hz.
 * Instead, we use the DC motor model to estimate current from power and velocity,
 * which are already available from the bulk cache at zero additional cost.
 *
 * ## Motor Model
 * ```
 * I_estimated = (V_battery × |power| - Kv × |velocity_tps|) / R_motor
 * ```
 * Where:
 * - `V_battery` = current battery voltage (from VoltageSensor, already read for BrownoutGuard)
 * - `power` = commanded motor power [-1.0, 1.0]
 * - `velocity_tps` = encoder velocity in ticks/sec (from bulk cache)
 * - `Kv` = back-EMF constant (volts per tick/sec)
 * - `R_motor` = winding resistance (ohms)
 *
 * ## Optional Calibration
 * One motor's actual current is read per loop cycle (round-robin) to calibrate
 * the model. This adds only ~2ms per loop instead of reading all motors.
 *
 * Zero allocations in the hot path.
 */
class CurrentBudgetManager(
    /** Maximum total current draw before graduated scaling begins (Amps) */
    val warningCurrentAmps: Double = 15.0,
    /** Maximum total current draw before full power cutoff (Amps) */
    val criticalCurrentAmps: Double = 18.0,
    /** Minimum power scale at the critical boundary */
    val minPowerScale: Double = 0.2,
    /** Hysteresis band in Amps to prevent oscillation */
    val hysteresisAmps: Double = 1.5
) {
    /**
     * Registered motor slots. Each slot tracks a motor, its model parameters,
     * and its estimated current draw.
     */
    private val slots = ArrayList<MotorSlot>(8)
    private var calibrationIndex = 0

    /** Current computed power scale factor (0.0 to 1.0) */
    var powerScale: Double = 1.0
        private set

    /** Current budget state */
    var state: CurrentBudgetState = CurrentBudgetState.HEALTHY
        private set

    /** Total estimated current draw across all motors (Amps) */
    var totalEstimatedAmps: Double = 0.0
        private set

    /** Number of times the budget has been exceeded since last reset */
    var tripCount: Int = 0
        private set

    /**
     * Register a motor with its electrical characteristics for current estimation.
     *
     * Common FTC motor parameters:
     * - **GoBilda 5203 (312 RPM)**: stallCurrentAmps=9.2, freeSpeedTps=2786, motorResistanceOhms=1.3
     * - **GoBilda 5203 (435 RPM)**: stallCurrentAmps=9.2, freeSpeedTps=3892, motorResistanceOhms=1.3
     * - **REV HD Hex (40:1)**: stallCurrentAmps=11.2, freeSpeedTps=2240, motorResistanceOhms=1.07
     * - **AndyMark NeveRest**: stallCurrentAmps=11.5, freeSpeedTps=2506, motorResistanceOhms=1.04
     *
     * @param motor The MotorIO instance to monitor
     * @param stallCurrentAmps Motor stall current at 12V (from datasheet)
     * @param freeSpeedTps Motor free speed in encoder ticks per second
     * @param nominalVoltage Motor rated voltage (typically 12.0V)
     */
    fun register(
        motor: MotorIO,
        stallCurrentAmps: Double = 9.2,
        freeSpeedTps: Double = 2786.0,
        nominalVoltage: Double = 12.0
    ) {
        val stall = if (stallCurrentAmps > 0.0 && stallCurrentAmps.isFinite()) stallCurrentAmps else 9.2
        val speed = if (freeSpeedTps > 0.0 && freeSpeedTps.isFinite()) freeSpeedTps else 2786.0
        val volt = if (nominalVoltage > 0.0 && nominalVoltage.isFinite()) nominalVoltage else 12.0

        val resistance = volt / stall
        val kv = volt / speed
        slots.add(MotorSlot(motor, resistance, kv, volt))
    }

    /**
     * Update current estimates and apply power budget scaling.
     * Call once per loop iteration.
     *
     * @param batteryVoltage Current battery voltage (already read for BrownoutGuard — no extra I2C)
     * @param enableCalibration If true, reads one actual motor current per cycle for model calibration
     */
    fun update(batteryVoltage: Double, enableCalibration: Boolean = false) {
        if (slots.isEmpty()) return

        val vBat = if (batteryVoltage > 0.1) batteryVoltage else 12.0

        // 1. Estimate current for each motor from the DC motor model + learned calibrationOffset
        var totalAmps = 0.0
        for (i in slots.indices) {
            val slot = slots[i]
            val motor = slot.motor
            val appliedVoltage = vBat * kotlin.math.abs(motor.power * slot.motor.powerScale)
            val backEmf = slot.kv * kotlin.math.abs(motor.velocity)
            val rawEstimate = ((appliedVoltage - backEmf) / slot.resistance).coerceAtLeast(0.0)
            val estimatedCurrent = (rawEstimate + slot.calibrationOffset).coerceAtLeast(0.0)

            slot.estimatedAmps = estimatedCurrent
            totalAmps += estimatedCurrent
        }

        // 2. Optional staggered calibration: read ONE motor's actual current per cycle
        if (enableCalibration && slots.isNotEmpty()) {
            val idx = calibrationIndex % slots.size
            val slot = slots[idx]
            try {
                val actualAmps = slot.motor.currentAmps
                if (actualAmps.isFinite() && actualAmps >= 0.0) {
                    slot.lastCalibratedAmps = actualAmps
                    
                    val motor = slot.motor
                    val appliedVoltage = vBat * kotlin.math.abs(motor.power * slot.motor.powerScale)
                    val backEmf = slot.kv * kotlin.math.abs(motor.velocity)
                    val rawEstimate = ((appliedVoltage - backEmf) / slot.resistance).coerceAtLeast(0.0)
                    
                    // Blend error offset: difference between actual and raw model estimate
                    val currentError = actualAmps - rawEstimate
                    slot.calibrationOffset = (slot.calibrationOffset * 0.3 + currentError * 0.7)
                    
                    // Recalculate this slot's estimate and the total
                    slot.estimatedAmps = (rawEstimate + slot.calibrationOffset).coerceAtLeast(0.0)
                    
                    totalAmps = 0.0
                    for (s in slots) totalAmps += s.estimatedAmps
                }
            } catch (_: Exception) {
                // Current read failed — stick with estimate
            }
            calibrationIndex++
        }

        totalEstimatedAmps = totalAmps

        // 3. State machine with hysteresis
        val previousState = state
        state = when (state) {
            CurrentBudgetState.HEALTHY -> when {
                totalAmps > criticalCurrentAmps -> CurrentBudgetState.CRITICAL
                totalAmps > warningCurrentAmps -> CurrentBudgetState.WARNING
                else -> CurrentBudgetState.HEALTHY
            }
            CurrentBudgetState.WARNING -> when {
                totalAmps > criticalCurrentAmps -> CurrentBudgetState.CRITICAL
                totalAmps < warningCurrentAmps - hysteresisAmps -> CurrentBudgetState.HEALTHY
                else -> CurrentBudgetState.WARNING
            }
            CurrentBudgetState.CRITICAL -> when {
                totalAmps < criticalCurrentAmps - hysteresisAmps -> CurrentBudgetState.WARNING
                else -> CurrentBudgetState.CRITICAL
            }
        }

        if (state != CurrentBudgetState.HEALTHY && previousState == CurrentBudgetState.HEALTHY) {
            tripCount++
        }

        // 4. Calculate power scale
        powerScale = when (state) {
            CurrentBudgetState.HEALTHY -> 1.0
            CurrentBudgetState.CRITICAL -> minPowerScale
            CurrentBudgetState.WARNING -> {
                val range = criticalCurrentAmps - warningCurrentAmps
                if (range <= 0.0) {
                    minPowerScale
                } else {
                    val ratio = 1.0 - ((totalAmps - warningCurrentAmps) / range)
                    minPowerScale + ratio * (1.0 - minPowerScale)
                }
            }
        }
    }

    /** Returns estimated current draw for a specific motor slot (0-indexed) */
    fun getMotorAmps(index: Int): Double {
        return if (index in slots.indices) slots[index].estimatedAmps else 0.0
    }

    /** Returns the number of registered motors */
    val motorCount: Int get() = slots.size

    /** Resets state and trip counter */
    fun reset() {
        state = CurrentBudgetState.HEALTHY
        powerScale = 1.0
        totalEstimatedAmps = 0.0
        tripCount = 0
        calibrationIndex = 0
        for (slot in slots) {
            slot.estimatedAmps = 0.0
            slot.lastCalibratedAmps = 0.0
            slot.calibrationOffset = 0.0
        }
    }

    fun isRegistered(motor: MotorIO): Boolean {
        for (i in 0 until slots.size) {
            if (slots[i].motor === motor) return true
        }
        return false
    }

    fun clear() {
        slots.clear()
        reset()
    }

    companion object {
        /** Pre-configured for standard FTC robot (20A fuse) */
        fun ftcDefaults(): CurrentBudgetManager = CurrentBudgetManager(
            warningCurrentAmps = 15.0,
            criticalCurrentAmps = 18.0,
            minPowerScale = 0.2,
            hysteresisAmps = 1.5
        )
    }
}

/** Internal tracking for a single motor */
internal class MotorSlot(
    val motor: MotorIO,
    val resistance: Double,
    val kv: Double,
    val nominalVoltage: Double,
    var estimatedAmps: Double = 0.0,
    var lastCalibratedAmps: Double = 0.0,
    var calibrationOffset: Double = 0.0
)

/** Current budget state machine states */
enum class CurrentBudgetState {
    /** Total current is within budget — full power allowed */
    HEALTHY,
    /** Total current exceeds warning — graduated power reduction active */
    WARNING,
    /** Total current at or near fuse limit — aggressive power reduction */
    CRITICAL
}
