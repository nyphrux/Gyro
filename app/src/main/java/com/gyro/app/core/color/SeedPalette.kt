package com.gyro.app.core.color

import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Builds Material-style tonal roles from one seed colour, for the custom accent.
 *
 * The named accents are pre-generated with Material Color Utilities; a custom colour can be
 * anything, so it is generated here at runtime. Colours are taken from CIE L*C*h at fixed L*
 * tones. Material's HCT "tone" is exactly CIE L*, so the contrast between any two roles is the
 * same as Material's (on-primary text at least 6.5:1, body text on cards about 8:1); only the
 * hue is a little less perceptually even than HCT's, which does not matter for an accent.
 *
 * Out-of-gamut colours keep their hue and tone and lose chroma until they fit sRGB.
 * Everything is ARGB [Int]s, so this stays plain Kotlin and unit-testable.
 */
class SeedPalette(seedArgb: Int) {
    private val hue: Double
    private val chroma: Double

    init {
        val lab = argbToLab(seedArgb or OPAQUE)
        hue = Math.toDegrees(atan2(lab[2], lab[1])).mod(360.0)
        chroma = hypot(lab[1], lab[2])
    }

    /** Accent colour at [tone] (0 black … 100 white), at the seed's own chroma. */
    fun primary(tone: Int): Int = toneOf(hue, chroma, tone)

    /** A quieter companion for secondary roles, as Material's Fidelity scheme does. */
    fun secondary(tone: Int): Int = toneOf(hue, maxOf(chroma - 32.0, chroma * 0.5), tone)

    /** Surfaces: barely tinted with the seed's hue. */
    fun neutral(tone: Int): Int = toneOf(hue, NEUTRAL_CHROMA, tone)

    /** Outlines and secondary text: a touch more tint than [neutral]. */
    fun neutralVariant(tone: Int): Int = toneOf(hue, NEUTRAL_VARIANT_CHROMA, tone)

    companion object {
        private const val OPAQUE = 0xFF000000.toInt()
        private const val NEUTRAL_CHROMA = 4.0
        private const val NEUTRAL_VARIANT_CHROMA = 7.0

        // D65 reference white.
        private const val WX = 95.047
        private const val WY = 100.0
        private const val WZ = 108.883

        /** The colour with the given CIE L* [tone], [hue] (degrees) and as much of [chroma] as sRGB allows. */
        fun toneOf(hue: Double, chroma: Double, tone: Int): Int {
            if (tone <= 0) return OPAQUE
            if (tone >= 100) return 0xFFFFFFFF.toInt()
            val l = tone.toDouble()
            val radians = Math.toRadians(hue)
            labToLinearRgb(l, chroma * cos(radians), chroma * sin(radians))
                .takeIf { it.inGamut() }
                ?.let { return linearToArgb(it) }
            // Largest in-gamut chroma at this hue and tone.
            var low = 0.0
            var high = chroma
            repeat(24) {
                val mid = (low + high) / 2.0
                if (labToLinearRgb(l, mid * cos(radians), mid * sin(radians)).inGamut()) low = mid else high = mid
            }
            return linearToArgb(labToLinearRgb(l, low * cos(radians), low * sin(radians)))
        }

        /** CIE L* of a colour: Material's "tone". */
        fun toneOfArgb(argb: Int): Double = argbToLab(argb)[0]

        /** WCAG contrast ratio between two colours, 1..21. */
        fun contrast(a: Int, b: Int): Double {
            val la = relativeLuminance(a)
            val lb = relativeLuminance(b)
            return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
        }

        private fun relativeLuminance(argb: Int): Double {
            val r = srgbToLinear((argb shr 16) and 0xFF)
            val g = srgbToLinear((argb shr 8) and 0xFF)
            val b = srgbToLinear(argb and 0xFF)
            return 0.2126 * r + 0.7152 * g + 0.0722 * b
        }

        private fun argbToLab(argb: Int): DoubleArray {
            val r = srgbToLinear((argb shr 16) and 0xFF)
            val g = srgbToLinear((argb shr 8) and 0xFF)
            val b = srgbToLinear(argb and 0xFF)
            val x = (0.41233895 * r + 0.35762064 * g + 0.18051042 * b) * 100.0
            val y = (0.2126 * r + 0.7152 * g + 0.0722 * b) * 100.0
            val z = (0.01932141 * r + 0.11916382 * g + 0.95034478 * b) * 100.0
            val fx = labF(x / WX)
            val fy = labF(y / WY)
            val fz = labF(z / WZ)
            return doubleArrayOf(116.0 * fy - 16.0, 500.0 * (fx - fy), 200.0 * (fy - fz))
        }

        private fun labToLinearRgb(l: Double, a: Double, b: Double): DoubleArray {
            val fy = (l + 16.0) / 116.0
            val fx = fy + a / 500.0
            val fz = fy - b / 200.0
            val x = labFInv(fx) * WX / 100.0
            val y = labFInv(fy) * WY / 100.0
            val z = labFInv(fz) * WZ / 100.0
            return doubleArrayOf(
                3.2413774792388685 * x - 1.5376652402851851 * y - 0.49885366846268053 * z,
                -0.9691452513005321 * x + 1.8758853451067872 * y + 0.04156585616912061 * z,
                0.05562093689691305 * x - 0.20395524564742123 * y + 1.0571799111220335 * z,
            )
        }

        private fun DoubleArray.inGamut(): Boolean = all { it >= -1e-7 && it <= 1.0 + 1e-7 }

        private fun linearToArgb(rgb: DoubleArray): Int {
            val r = linearToSrgb(rgb[0])
            val g = linearToSrgb(rgb[1])
            val b = linearToSrgb(rgb[2])
            return OPAQUE or (r shl 16) or (g shl 8) or b
        }

        private fun srgbToLinear(channel: Int): Double {
            val c = channel / 255.0
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }

        private fun linearToSrgb(linear: Double): Int {
            val c = linear.coerceIn(0.0, 1.0)
            val v = if (c <= 0.0031308) c * 12.92 else 1.055 * c.pow(1.0 / 2.4) - 0.055
            return (v * 255.0).roundToInt().coerceIn(0, 255)
        }

        private fun labF(t: Double): Double = if (t > 216.0 / 24389.0) cbrt(t) else (24389.0 / 27.0 * t + 16.0) / 116.0

        private fun labFInv(f: Double): Double {
            val f3 = f * f * f
            return if (f3 > 216.0 / 24389.0) f3 else (116.0 * f - 16.0) / (24389.0 / 27.0)
        }
    }
}
