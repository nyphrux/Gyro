package com.gyro.app.core.tilt

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SlopeTest {
    @Test
    fun percent() {
        assertEquals(100f, Slope.percent(45f)!!, 1e-3f)
        assertEquals(-100f, Slope.percent(-45f)!!, 1e-3f)
        assertEquals(0f, Slope.percent(0f)!!, 1e-6f)
        assertEquals(8.333f, Slope.percent(4.7636f)!!, 1e-2f) // a 1:12 wheelchair ramp
        assertNull(Slope.percent(90f))
    }

    @Test
    fun ratio() {
        assertEquals(1f, Slope.ratioRun(45f)!!, 1e-3f)
        assertEquals(12f, Slope.ratioRun(4.7636f)!!, 1e-2f)
        assertEquals(12f, Slope.ratioRun(-4.7636f)!!, 1e-2f) // the run has no sign
        assertNull(Slope.ratioRun(0f)) // level: 1:∞
        assertNull(Slope.ratioRun(0.01f))
        assertNull(Slope.ratioRun(90f))
    }

    @Test
    fun unitNamesRoundTrip() {
        SlopeUnit.entries.forEach { assertEquals(it, SlopeUnit.fromName(it.name)) }
        assertNull(SlopeUnit.fromName("FURLONGS"))
    }
}
