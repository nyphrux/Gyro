package com.gyro.app.core.math

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AngleMathTest {

    @Test
    fun normalize_wrapsIntoZeroTo360() {
        assertEquals(0f, AngleMath.normalize(0f))
        assertEquals(0f, AngleMath.normalize(360f))
        assertEquals(10f, AngleMath.normalize(370f), 1e-4f)
        assertEquals(350f, AngleMath.normalize(-10f), 1e-4f)
        assertEquals(270f, AngleMath.normalize(-450f), 1e-4f)
        assertEquals(0f, AngleMath.normalize(-1e-7f)) // must never return 360
    }

    @Test
    fun shortestDelta_crossesNorthTheShortWay() {
        assertEquals(20f, AngleMath.shortestDelta(350f, 10f), 1e-4f)
        assertEquals(-20f, AngleMath.shortestDelta(10f, 350f), 1e-4f)
        assertEquals(90f, AngleMath.shortestDelta(0f, 90f), 1e-4f)
        assertEquals(180f, AngleMath.shortestDelta(0f, 180f), 1e-4f)
        assertEquals(-179f, AngleMath.shortestDelta(0f, 181f), 1e-4f)
    }

    @Test
    fun unwrap_keepsAnimationContinuousAcrossNorth() {
        // 359° → 1° should animate +2°, not -358°.
        assertEquals(361f, AngleMath.unwrap(359f, 1f), 1e-4f)
        // Works from an already-unwrapped value several turns in.
        assertEquals(719f, AngleMath.unwrap(721f, 359f), 1e-4f)
        assertEquals(-2f, AngleMath.unwrap(2f, 358f), 1e-4f)
    }

    @Test
    fun cardinalDirection_usesCentredSectors() {
        assertEquals(CardinalDirection.N, CardinalDirection.fromHeading(0f))
        assertEquals(CardinalDirection.N, CardinalDirection.fromHeading(359f))
        assertEquals(CardinalDirection.N, CardinalDirection.fromHeading(22.4f))
        assertEquals(CardinalDirection.NE, CardinalDirection.fromHeading(22.5f))
        assertEquals(CardinalDirection.E, CardinalDirection.fromHeading(90f))
        assertEquals(CardinalDirection.SW, CardinalDirection.fromHeading(225f))
        assertEquals(CardinalDirection.NW, CardinalDirection.fromHeading(337.4f))
        assertEquals(CardinalDirection.N, CardinalDirection.fromHeading(337.5f))
        assertEquals(CardinalDirection.W, CardinalDirection.fromHeading(-90f))
    }

    @Test
    fun cardinalDetent_firesOncePerCrossingWithHysteresis() {
        val detent = CardinalDetent(hysteresisDegrees = 2f)
        assertFalse(detent.update(80f)) // first sample only initialises
        assertFalse(detent.update(89f))
        assertFalse(detent.update(90.5f)) // inside the hysteresis band
        assertFalse(detent.update(89.5f))
        assertTrue(detent.update(93f)) // decisively past east
        assertFalse(detent.update(95f))
        assertFalse(detent.update(91f)) // noise back towards the boundary
        assertTrue(detent.update(85f)) // decisively back
    }

    @Test
    fun cardinalDetent_handlesWrapAtNorth() {
        val detent = CardinalDetent()
        detent.update(350f)
        assertTrue(detent.update(5f))
        assertTrue(detent.update(355f))
    }
}
