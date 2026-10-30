package com.gyro.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gyro.app.ui.theme.GyroMotion
import com.gyro.app.ui.theme.GyroThemeColors
import com.gyro.app.ui.theme.GyroSemanticColors
import com.gyro.app.ui.theme.LocalReducedMotion

enum class IslandTone { NEUTRAL, ACTIVE, SUCCESS, WARNING, ERROR }

/** Status shown in the island. Keep [text] state-like (not a live number) so TalkBack is not spammed. */
@Immutable
data class IslandStatus(val text: String, val tone: IslandTone, @DrawableRes val icon: Int? = null, val pulsing: Boolean = false)

private fun IslandTone.accent(colors: GyroSemanticColors): Color = when (this) {
    IslandTone.NEUTRAL -> colors.islandNeutral
    IslandTone.ACTIVE -> colors.islandActive
    IslandTone.SUCCESS -> colors.islandSuccess
    IslandTone.WARNING -> colors.islandWarning
    IslandTone.ERROR -> colors.islandError
}

/**
 * Gyro's signature element, a nod to DroidBubble's dynamic island: a near-black pill that
 * morphs its width with a spring as the instrument's state changes. Icons and wording carry the
 * meaning; colour only reinforces it.
 */
@Composable
fun StatusIsland(status: IslandStatus, modifier: Modifier = Modifier) {
    val colors = GyroThemeColors.current
    val accent by animateColorAsState(status.tone.accent(colors), GyroMotion.effectsOrSnap(), label = "islandAccent")
    Surface(
        shape = CircleShape,
        color = colors.island,
        contentColor = colors.onIsland,
        shadowElevation = 6.dp,
        modifier = modifier
            .animateContentSize(GyroMotion.spatialOrSnap())
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 36.dp)
                .padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IslandIndicator(icon = status.icon, accent = accent, pulsing = status.pulsing)
            Spacer(Modifier.width(8.dp))
            AnimatedContent(
                targetState = status.text,
                transitionSpec = {
                    fadeIn(GyroMotion.fade(160)) togetherWith fadeOut(GyroMotion.fade(120)) using SizeTransform(clip = false)
                },
                label = "islandText",
            ) { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onIsland,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun IslandIndicator(@DrawableRes icon: Int?, accent: Color, pulsing: Boolean) {
    Box(modifier = Modifier.size(18.dp), contentAlignment = Alignment.Center) {
        if (pulsing && !LocalReducedMotion.current) {
            // Only composed while pulsing, so the infinite transition stops with it.
            val transition = rememberInfiniteTransition(label = "islandPulse")
            val progress by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(1400, easing = LinearOutSlowInEasing), RepeatMode.Restart),
                label = "pulseProgress",
            )
            Canvas(Modifier.size(18.dp)) {
                drawCircle(accent.copy(alpha = 0.45f * (1f - progress)), radius = size.minDimension / 2f * (0.4f + 0.6f * progress))
            }
        }
        if (icon != null) {
            Icon(painter = painterResource(icon), contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
        } else {
            Canvas(Modifier.size(8.dp)) { drawCircle(accent) }
        }
    }
}
