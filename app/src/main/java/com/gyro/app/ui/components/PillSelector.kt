package com.gyro.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.gyro.app.ui.common.LocalGyroHaptics
import com.gyro.app.ui.theme.GyroMotion

/**
 * A segmented single-choice control with a sliding indicator. Each segment is a 48 dp
 * radio-button target; the indicator glides with the layout spring.
 */
@Composable
fun <T> PillSelector(options: List<T>, selected: T, onSelect: (T) -> Unit, label: @Composable (T) -> String, modifier: Modifier = Modifier, accessibilityLabel: (@Composable (T) -> String)? = null) {
    val haptics = LocalGyroHaptics.current
    val selectedIndex = options.indexOf(selected).coerceAtLeast(0)
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(CircleShape)
            .background(colors.surfaceContainerHigh)
            .padding(4.dp),
    ) {
        val segmentWidth = maxWidth / options.size.coerceAtLeast(1)
        val indicatorOffset by animateDpAsState(segmentWidth * selectedIndex, GyroMotion.spatialOrSnap(), label = "pillOffset")
        Box(
            modifier = Modifier
                .offset { IntOffset(indicatorOffset.roundToPx(), 0) }
                .width(segmentWidth)
                .fillMaxHeight()
                .shadow(2.dp, CircleShape)
                .clip(CircleShape)
                .background(colors.primary),
        )
        Row(modifier = Modifier.fillMaxSize().selectableGroup()) {
            options.forEachIndexed { index, option ->
                val isSelected = index == selectedIndex
                val textColor by animateColorAsState(
                    if (isSelected) colors.onPrimary else colors.onSurfaceVariant,
                    GyroMotion.effectsOrSnap(),
                    label = "pillText",
                )
                val description = accessibilityLabel?.invoke(option)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .selectable(
                            selected = isSelected,
                            role = Role.RadioButton,
                            onClick = {
                                if (!isSelected) {
                                    haptics.tick()
                                    onSelect(option)
                                }
                            },
                        )
                        .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label(option),
                        color = textColor,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                }
            }
        }
    }
}
