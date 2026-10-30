package com.gyro.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Grouped surfaces, adapted from the DroidBubble reference: items in one group share a
 * column (or row) with large outer corners and tight inner corners, separated by small gaps,
 * so related controls read as one card without divider lines.
 */

enum class GroupPosition { FIRST, MIDDLE, LAST, SINGLE }

private val OuterRadius = 24.dp
private val InnerRadius = 6.dp

fun groupPosition(index: Int, count: Int): GroupPosition = when {
    count <= 1 -> GroupPosition.SINGLE
    index == 0 -> GroupPosition.FIRST
    index == count - 1 -> GroupPosition.LAST
    else -> GroupPosition.MIDDLE
}

/** Shape for an item in a vertical group. */
fun verticalGroupShape(position: GroupPosition, outer: Dp = OuterRadius, inner: Dp = InnerRadius) = when (position) {
    GroupPosition.FIRST -> RoundedCornerShape(topStart = outer, topEnd = outer, bottomStart = inner, bottomEnd = inner)
    GroupPosition.MIDDLE -> RoundedCornerShape(inner)
    GroupPosition.LAST -> RoundedCornerShape(topStart = inner, topEnd = inner, bottomStart = outer, bottomEnd = outer)
    GroupPosition.SINGLE -> RoundedCornerShape(outer)
}

/** Shape for an item in a horizontal group (stat tiles). */
fun horizontalGroupShape(position: GroupPosition, outer: Dp = OuterRadius, inner: Dp = InnerRadius) = when (position) {
    GroupPosition.FIRST -> RoundedCornerShape(topStart = outer, bottomStart = outer, topEnd = inner, bottomEnd = inner)
    GroupPosition.MIDDLE -> RoundedCornerShape(inner)
    GroupPosition.LAST -> RoundedCornerShape(topStart = inner, bottomStart = inner, topEnd = outer, bottomEnd = outer)
    GroupPosition.SINGLE -> RoundedCornerShape(outer)
}

@Composable
fun GroupedSurface(
    position: GroupPosition,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = verticalGroupShape(position),
        color = color,
        content = content,
    )
}

/** Gap between items of one group. */
val GroupGap = 4.dp

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 20.dp, bottom = 8.dp)
            .semantics { heading() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
