package com.gyro.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.gyro.app.ui.navigation.Tool
import com.gyro.app.ui.theme.GyroMotion

/**
 * Floating pill navigation. The selected tool expands to show its label — the same morphing
 * capsule idea as DroidBubble's island — while the others collapse to icons.
 */
@Composable
fun GyroNavBar(selected: Tool, onSelect: (Tool) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = colors.surfaceContainerHigh,
        shadowElevation = 10.dp,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Row(
            modifier = Modifier
                .padding(6.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Tool.entries.forEach { tool ->
                NavItem(tool = tool, selected = tool == selected, onClick = { onSelect(tool) })
            }
        }
    }
}

@Composable
private fun NavItem(tool: Tool, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(
        if (selected) colors.primary else Color.Transparent,
        GyroMotion.effectsOrSnap(),
        label = "navContainer",
    )
    val content by animateColorAsState(
        if (selected) colors.onPrimary else colors.onSurfaceVariant,
        GyroMotion.effectsOrSnap(),
        label = "navContent",
    )
    val label = stringResource(tool.label)
    Row(
        modifier = Modifier
            .height(48.dp)
            .clip(CircleShape)
            .background(container)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 18.dp)
            .testTag("nav_${tool.name}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(tool.icon),
            // When selected the visible label names the tab; otherwise the icon must.
            contentDescription = if (selected) null else label,
            tint = content,
            modifier = Modifier.size(22.dp),
        )
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn(GyroMotion.effectsOrSnap()) + expandHorizontally(GyroMotion.spatialOrSnap()),
            exit = fadeOut(GyroMotion.effectsOrSnap()) + shrinkHorizontally(GyroMotion.spatialOrSnap()),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = content,
                maxLines = 1,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/** Tonal circular icon button used in top bars. */
@Composable
fun TopBarIconButton(
    icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    IconButton(
        onClick = onClick,
        modifier = modifier.size(48.dp),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = colors.surfaceContainerHigh,
            contentColor = colors.onSurface,
        ),
    ) {
        Icon(painterResource(icon), contentDescription = contentDescription, modifier = Modifier.size(22.dp))
    }
}
