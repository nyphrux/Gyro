package com.gyro.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gyro.app.ui.theme.GyroThemeColors
import com.gyro.app.ui.theme.Readout

enum class PanelTone { NEUTRAL, WARNING, ERROR }

@Immutable
data class PanelAction(val label: String, val onClick: () -> Unit)

/**
 * Explains a state the user may need to act on: missing permission, disabled location,
 * missing hardware, calibration. Always pairs an icon and words; colour is secondary.
 */
@Composable
fun MessagePanel(@DrawableRes icon: Int, title: String, body: String, modifier: Modifier = Modifier, tone: PanelTone = PanelTone.NEUTRAL, primaryAction: PanelAction? = null, secondaryAction: PanelAction? = null, extra: (@Composable ColumnScope.() -> Unit)? = null) {
    val scheme = MaterialTheme.colorScheme
    val semantic = GyroThemeColors.current
    val (badgeColor, badgeContent) = when (tone) {
        PanelTone.NEUTRAL -> scheme.primaryContainer to scheme.onPrimaryContainer
        PanelTone.WARNING -> semantic.warningContainer to semantic.onWarningContainer
        PanelTone.ERROR -> scheme.errorContainer to scheme.onErrorContainer
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = scheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) {}) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(badgeColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(icon), contentDescription = null, tint = badgeContent, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(14.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
            }
            Spacer(Modifier.height(10.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            if (extra != null) {
                Spacer(Modifier.height(12.dp))
                extra()
            }
            if (primaryAction != null || secondaryAction != null) {
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    secondaryAction?.let { TextButton(onClick = it.onClick) { Text(it.label) } }
                    primaryAction?.let { Button(onClick = it.onClick) { Text(it.label) } }
                }
            }
        }
    }
}

/**
 * A labelled measurement in a horizontal group of tiles.
 * Merged for accessibility so TalkBack reads "Top speed, 42 km/h".
 */
@Composable
fun StatTile(
    label: String,
    value: String,
    position: GroupPosition,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    trailing: (@Composable () -> Unit)? = null,
) {
    Surface(
        modifier = modifier,
        shape = horizontalGroupShape(position),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp, end = if (trailing == null) 16.dp else 0.dp, top = 14.dp, bottom = 14.dp)
                    .semantics(mergeDescendants = true) {},
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(value, style = Readout.medium, color = valueColor, maxLines = 1)
                if (supporting != null) {
                    Text(
                        supporting,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (trailing != null) trailing()
        }
    }
}

/** Small explanatory footnote with an icon, used for honest caveats (GPS limits etc.). */
@Composable
fun Footnote(@DrawableRes icon: Int, text: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.Top) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 1.dp).size(16.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
