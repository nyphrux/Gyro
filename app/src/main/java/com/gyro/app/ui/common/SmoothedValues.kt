package com.gyro.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import com.gyro.app.core.math.AngleMath
import com.gyro.app.ui.theme.LocalReducedMotion
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import kotlin.math.exp

/*
 * Bridges high-rate sensor values into frame-rate motion without recomposition. The returned
 * State should only be read in draw / graphicsLayer lambdas.
 *
 * Why not Animatable.animateTo per sample: sensors deliver ~50–200 samples a second, and every
 * new animateTo cancels the previous one and starts a fresh animation whose first frame has a
 * play time of zero, i.e. it does not move. With a new target arriving every frame or two, most
 * frames were spent restarting, so needles and bubbles crawled and stuttered behind the phone.
 *
 * Instead each value is driven by one long-lived loop that integrates a critically damped
 * spring towards the *latest* target on every display frame (exact closed-form step, so it is
 * stable at any frame rate), then goes idle and stops requesting frames once it has settled.
 */

/** Natural frequency of the follower, rad/s. Critically damped: ~2/ω ≈ 65 ms behind a steadily moving target, no overshoot. */
private const val OMEGA = 30f

/** First frame after idling has no previous timestamp; assume one 60 Hz frame. */
private const val FIRST_FRAME_SECONDS = 1f / 60f

/** Guards against long gaps (app paused, dropped frames) turning into a jump. */
private const val MAX_FRAME_SECONDS = 0.05f

/** Velocity (units/s) below which a value within epsilon of its target counts as settled. */
private const val SETTLE_VELOCITY_FACTOR = 10f

private const val ANGLE_EPSILON = 0.01f // degrees
private const val VALUE_EPSILON = 0.005f // gauge units
private const val OFFSET_EPSILON = 0.0005f // fraction of bubble travel

/**
 * Animates an angle continuously: 359° → 1° moves forward 2° instead of spinning.
 * The returned value is unwrapped (may exceed 360), which is exactly what rotation wants.
 */
@Composable
fun rememberAnimatedAngle(target: () -> Float?): State<Float> {
    val reducedMotion = LocalReducedMotion.current
    val currentTarget = rememberUpdatedState(target)
    val follower = remember { Follower(dimensions = 1, epsilon = ANGLE_EPSILON) }
    val state = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(reducedMotion) {
        follower.drive(
            reducedMotion = reducedMotion,
            goal = { value, out ->
                val angle = currentTarget.value() ?: return@drive false
                // Approach the nearest equivalent of the target, so crossing north is a short move.
                out[0] = if (follower.initialised) AngleMath.unwrap(value[0], angle) else angle
                true
            },
            publish = { state.floatValue = it[0] },
        )
    }
    return state
}

/** Animates a scalar towards a sensor-driven target (gauge arcs, scales). */
@Composable
fun rememberAnimatedValue(initial: Float = 0f, target: () -> Float): State<Float> {
    val reducedMotion = LocalReducedMotion.current
    val currentTarget = rememberUpdatedState(target)
    val follower = remember { Follower(dimensions = 1, epsilon = VALUE_EPSILON).also { it.start(floatArrayOf(initial)) } }
    val state = remember { mutableFloatStateOf(initial) }
    LaunchedEffect(reducedMotion) {
        follower.drive(
            reducedMotion = reducedMotion,
            goal = { _, out ->
                out[0] = currentTarget.value()
                true
            },
            publish = { state.floatValue = it[0] },
        )
    }
    return state
}

/** Animates a 2D position (the level bubble). */
@Composable
fun rememberAnimatedOffset(target: () -> Offset?): State<Offset> {
    val reducedMotion = LocalReducedMotion.current
    val currentTarget = rememberUpdatedState(target)
    val follower = remember { Follower(dimensions = 2, epsilon = OFFSET_EPSILON) }
    val state = remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(reducedMotion) {
        follower.drive(
            reducedMotion = reducedMotion,
            goal = { _, out ->
                val offset = currentTarget.value() ?: return@drive false
                out[0] = offset.x
                out[1] = offset.y
                true
            },
            publish = { state.value = Offset(it[0], it[1]) },
        )
    }
    return state
}

/**
 * Per-frame critically damped follower for [dimensions] independent components.
 * Kept in `remember`, so position and velocity survive a restart of the driving effect.
 */
private class Follower(dimensions: Int, private val epsilon: Float) {
    val value = FloatArray(dimensions)
    private val velocity = FloatArray(dimensions)
    private val target = FloatArray(dimensions)
    var initialised = false
        private set

    fun start(initial: FloatArray) {
        initial.copyInto(value)
        initialised = true
    }

    /**
     * Drives the value until cancelled.
     * @param goal writes the target for the current value into `out`; returns false when there is none (hold still).
     */
    suspend fun drive(
        reducedMotion: Boolean,
        goal: (value: FloatArray, out: FloatArray) -> Boolean,
        publish: (FloatArray) -> Unit,
    ) {
        if (!initialised) {
            // Wait for the first reading and show it directly: nothing to animate from yet.
            snapshotFlow { goal(value, target) }.first { it }
            start(target)
        }
        publish(value)
        while (true) {
            // Idle (no frames requested) until the target moves away from where we rest.
            snapshotFlow { goal(value, target) && isAway() }.first { it }
            if (reducedMotion) {
                target.copyInto(value)
                velocity.fill(0f)
                publish(value)
                continue
            }
            var lastFrameNanos = 0L
            do {
                val settled = withFrameNanos { now ->
                    val dt = if (lastFrameNanos == 0L) {
                        FIRST_FRAME_SECONDS
                    } else {
                        ((now - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, MAX_FRAME_SECONDS)
                    }
                    lastFrameNanos = now
                    val hasGoal = goal(value, target)
                    step(if (hasGoal) target else value, dt).also { publish(value) }
                }
            } while (!settled)
        }
    }

    private fun isAway(): Boolean {
        for (i in value.indices) if (abs(target[i] - value[i]) > epsilon) return true
        return false
    }

    /**
     * Exact step of a critically damped spring (x'' = -2ωx' - ω²x) towards a target held for [dt].
     * x(t) = (x₀ + (v₀ + ωx₀)t)·e^(−ωt). @return true when every component has settled.
     */
    private fun step(to: FloatArray, dt: Float): Boolean {
        val decay = exp(-OMEGA * dt)
        var settled = true
        for (i in value.indices) {
            val x = value[i] - to[i]
            val v = velocity[i]
            val b = v + OMEGA * x
            val nextX = (x + b * dt) * decay
            val nextV = (v - OMEGA * b * dt) * decay
            if (abs(nextX) < epsilon && abs(nextV) < epsilon * SETTLE_VELOCITY_FACTOR) {
                value[i] = to[i]
                velocity[i] = 0f
            } else {
                value[i] = to[i] + nextX
                velocity[i] = nextV
                settled = false
            }
        }
        return settled
    }
}
