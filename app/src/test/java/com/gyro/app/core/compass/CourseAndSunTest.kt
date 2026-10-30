package com.gyro.app.core.compass

import com.gyro.app.core.prefs.NorthReference
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CourseAndSunTest {

    @Test
    fun course_offsetIsShortestTurnWithSign() {
        val tracker = CourseTracker()
        assertEquals(20f, tracker.update(heading = 350f, target = 10f).offsetDegrees, 1e-4f) // turn right across north
        assertEquals(-30f, tracker.update(heading = 40f, target = 10f).offsetDegrees, 1e-4f) // turn left
        assertEquals(180f, tracker.update(heading = 0f, target = 180f).offsetDegrees, 1e-4f)
        assertEquals(10f, tracker.update(heading = 0f, target = 370f).targetDegrees, 1e-4f)
    }

    @Test
    fun course_onCourseHasHysteresis() {
        val tracker = CourseTracker(enterDegrees = 3f, exitDegrees = 6f)
        assertFalse(tracker.update(95f, 90f).onCourse) // 5° off: not yet
        assertTrue(tracker.update(92f, 90f).onCourse) // within 3°: on course
        assertTrue(tracker.update(95f, 90f).onCourse) // drifting to 5° keeps it
        assertFalse(tracker.update(97f, 90f).onCourse) // beyond 6° leaves
        tracker.update(90f, 90f)
        tracker.reset()
        assertFalse(tracker.onCourse)
    }

    private data class SunCase(val name: String, val lat: Double, val lon: Double, val epochMillis: Long, val azimuth: Float, val elevation: Float)

    /** Reference values from NREL's Solar Position Algorithm (pvlib.solarposition.spa_python). */
    private val referenceCases = listOf(
        SunCase("London summer noon", 51.5074, -0.1278, 1718971200000, 178.814f, 61.933f),
        SunCase("London winter 15:00", 51.5074, -0.1278, 1734793200000, 220.893f, 5.344f),
        SunCase("Sydney morning", -33.8688, 151.2093, 1741644000000, 76.328f, 25.759f),
        SunCase("Reykjavik midnight", 64.1466, -21.9426, 1782001800000, 346.375f, -1.650f),
        SunCase("Quito afternoon", -0.1807, -78.4678, 1793379600000, 182.533f, 76.228f),
        SunCase("Los Angeles night", 34.05, -118.25, 1768446000000, 259.427f, -23.241f),
    )

    @Test
    fun solarPosition_matchesNrelSpa() {
        referenceCases.forEach { c ->
            val sun = SolarPosition.compute(c.lat, c.lon, c.epochMillis)
            // NOAA and SPA differ by hundredths of a degree; near the horizon only their refraction models differ.
            val elevationTolerance = if (c.elevation < 2f) 0.4f else 0.1f
            assertEquals(c.azimuth, sun.azimuthDegrees, 0.15f, "${c.name} azimuth")
            assertEquals(c.elevation, sun.elevationDegrees, elevationTolerance, "${c.name} elevation")
        }
    }

    @Test
    fun solarPosition_horizonFlag() {
        assertTrue(SolarPosition.compute(51.5074, -0.1278, 1718971200000).isUp)
        assertFalse(SolarPosition.compute(34.05, -118.25, 1768446000000).isUp)
    }

    private val tenEast = MagneticModel { _, _ -> 10f }
    private val london = PositionResult.Available(GeoPoint(51.5074, -0.1278))

    @Test
    fun sunState_convertsToMagneticWhenCardIsMagnetic() {
        val trueSun = SunState.from(true, london, displaysTrueNorth = true, epochMillis = 1718971200000, model = tenEast)
        val magneticSun = SunState.from(true, london, displaysTrueNorth = false, epochMillis = 1718971200000, model = tenEast)
        assertIs<SunState.Visible>(trueSun)
        assertIs<SunState.Visible>(magneticSun)
        assertEquals(178.81f, trueSun.bearingDegrees, 0.15f)
        // 10° east declination: magnetic bearing = true bearing − 10°.
        assertEquals(trueSun.bearingDegrees - 10f, magneticSun.bearingDegrees, 1e-3f)
    }

    @Test
    fun sunState_reflectsPositionLookup() {
        fun state(show: Boolean, p: PositionResult?) = SunState.from(show, p, false, 0L, tenEast)
        assertEquals(SunState.Hidden, state(false, london))
        assertEquals(SunState.Locating, state(true, null))
        assertEquals(SunState.Locating, state(true, PositionResult.Loading))
        assertEquals(SunState.PermissionRequired, state(true, PositionResult.PermissionRequired))
        assertEquals(SunState.Unavailable, state(true, PositionResult.Unavailable))
    }

    @Test
    fun declinationResult_derivedFromPosition() {
        fun d(ref: NorthReference, p: PositionResult?) = declinationResult(ref, p, 0L, tenEast)
        assertNull(d(NorthReference.MAGNETIC, london))
        assertEquals(DeclinationResult.Loading, d(NorthReference.TRUE, null))
        assertEquals(DeclinationResult.Loading, d(NorthReference.TRUE, PositionResult.Loading))
        assertEquals(DeclinationResult.PermissionRequired, d(NorthReference.TRUE, PositionResult.PermissionRequired))
        assertEquals(DeclinationResult.LocationUnavailable, d(NorthReference.TRUE, PositionResult.Unavailable))
        assertEquals(DeclinationResult.Available(10f), d(NorthReference.TRUE, london))
    }
}
