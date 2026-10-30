package com.gyro.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.gyro.app.ui.theme.GyroMotion

@Composable
fun GyroTopBar(title: String, modifier: Modifier = Modifier, overline: String? = null, navigation: (@Composable () -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (navigation != null) {
            navigation()
            Spacer(Modifier.width(12.dp))
        }
        AnimatedContent(
            targetState = overline to title,
            transitionSpec = { fadeIn(GyroMotion.fade(220)) togetherWith fadeOut(GyroMotion.fade(120)) },
            modifier = Modifier.weight(1f),
            label = "topBarTitle",
        ) { (over, text) ->
            Column {
                if (over != null) {
                    Text(
                        text = over.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/**
 * Standard tool layout. Portrait: one scrolling column, instrument centred. Wide screens
 * (tablets, unfolded foldables in landscape): instrument on the left, details on the right.
 * [instrument] receives the side length available for a square instrument.
 */
@Composable
fun ToolLayout(
    contentPadding: PaddingValues,
    header: @Composable ColumnScope.() -> Unit,
    instrument: @Composable ColumnScope.(size: Dp) -> Unit,
    details: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val availableWidth = maxWidth
        val availableHeight = maxHeight

        val wide = availableWidth >= 600.dp && availableWidth > availableHeight
        if (wide) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(contentPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                ) {
                    header()
                    instrument(min(availableHeight - 160.dp, availableWidth / 2 - 64.dp).coerceAtLeast(240.dp))
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(contentPadding)
                        .padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    content = details,
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(contentPadding)
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(4.dp))
                header()
                Spacer(Modifier.height(12.dp))
                instrument(min(availableWidth - 48.dp, 360.dp))
                Spacer(Modifier.height(20.dp))
                Column(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    content = details,
                )
            }
        }
    }
}
