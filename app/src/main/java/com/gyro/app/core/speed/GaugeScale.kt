package com.gyro.app.core.speed

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round

/**
 * Auto-ranging full-scale value for the speed gauge (in the display unit), so walking speed is
 * readable on the same gauge that later shows motorway speed.
 *
 * Grows as soon as the value needs more headroom; shrinks only once the value is comfortably
 * inside a smaller scale, so the gauge does not flip back and forth around a boundary.
 */
object GaugeScale {
    private val STEPS = floatArrayOf(10f, 20f, 40f, 60f, 100f, 150f, 200f, 300f)
    private const val GROW_AT = 0.85f
    private const val SHRINK_BELOW = 0.7f
    private const val EXTENDED_STEP = 500f

    val minimum: Float get() = STEPS.first()

    fun select(currentScale: Float?, value: Float): Float {
        val v = value.coerceAtLeast(0f)
        val target = STEPS.firstOrNull { v <= it * GROW_AT }
            ?: (ceil(v / GROW_AT / EXTENDED_STEP) * EXTENDED_STEP)
        if (currentScale == null || target >= currentScale) return target
        // Candidate is smaller: only shrink once well inside it.
        return if (v < target * SHRINK_BELOW) target else currentScale
    }

    /** A "nice" labelled-tick spacing that divides [scale] evenly into roughly 4–8 intervals. */
    fun majorTickStep(scale: Float): Float {
        val raw = scale / 6f
        val magnitude = 10f.pow(floor(log10(raw)))
        val candidates = listOf(1f, 2f, 2.5f, 5f, 10f, 20f, 25f, 50f).map { it * magnitude }
        return candidates.firstOrNull { it >= raw * 0.99f && dividesEvenly(scale, it) }
            ?: candidates.first { it >= raw * 0.99f }
    }

    private fun dividesEvenly(scale: Float, step: Float): Boolean {
        val ratio = scale / step
        return abs(ratio - round(ratio)) < 1e-3f
    }
}
