package com.gyro.app.core.color

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeedPaletteTest {
    private val seeds = listOf(
        0xFF00879E.toInt(), // teal
        0xFFFF0000.toInt(), // pure red, far outside most tones' gamut
        0xFFFFFF00.toInt(), // yellow, the hardest hue for dark tones
        0xFF0000FF.toInt(), // pure blue
        0xFF808080.toInt(), // grey: no hue at all
        0xFF000000.toInt(),
        0xFFFFFFFF.toInt(),
    )

    @Test
    fun tonesLandOnTheirLStar() {
        for (seed in seeds) {
            val palette = SeedPalette(seed)
            for (tone in listOf(10, 20, 30, 40, 50, 80, 90, 95)) {
                assertEquals("seed ${seed.toUInt().toString(16)} tone $tone", tone.toDouble(), SeedPalette.toneOfArgb(palette.primary(tone)), 0.6)
                assertEquals(tone.toDouble(), SeedPalette.toneOfArgb(palette.neutral(tone)), 0.6)
            }
        }
    }

    @Test
    fun rolePairsKeepMaterialContrast() {
        for (seed in seeds) {
            val p = SeedPalette(seed)
            // Light: primary 40 on 100; dark: primary 80 with 20.
            assertTrue(SeedPalette.contrast(p.primary(40), p.primary(100)) >= 6.3)
            assertTrue(SeedPalette.contrast(p.primary(80), p.primary(20)) >= 6.3)
            // Body text on cards: neutral variant 30 on neutral 96, and 80 on 10.
            assertTrue(SeedPalette.contrast(p.neutralVariant(30), p.neutral(96)) >= 7.5)
            assertTrue(SeedPalette.contrast(p.neutralVariant(80), p.neutral(10)) >= 7.5)
        }
    }

    @Test
    fun extremesAreBlackAndWhite() {
        val p = SeedPalette(0xFF3366CC.toInt())
        assertEquals(0xFF000000.toInt(), p.primary(0))
        assertEquals(0xFFFFFFFF.toInt(), p.primary(100))
    }

    @Test
    fun keepsTheSeedHue() {
        // A mid-tone of a saturated blue seed is still clearly blue.
        val c = SeedPalette(0xFF1F6FE0.toInt()).primary(40)
        val r = (c shr 16) and 0xFF
        val b = c and 0xFF
        assertTrue(b > r + 60)
    }
}
