package com.gyro.app.ui.settings

import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gyro.app.R
import com.gyro.app.core.update.ReleaseInfo
import com.gyro.app.ui.common.LocalGyroHaptics
import com.gyro.app.ui.components.GroupPosition
import com.gyro.app.ui.components.GroupedSurface
import kotlin.math.roundToInt

/**
 * Top-bar update button (opposite the back arrow). Spins while checking, shows download
 * progress as a ring, and wears a dot once an update is known.
 */
@Composable
internal fun UpdateButton(state: UpdateState, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalGyroHaptics.current
    val available = state as? UpdateState.Available
    val description = stringResource(
        when {
            state is UpdateState.Checking -> R.string.update_checking
            available != null -> R.string.update_available_cd
            else -> R.string.update_check
        },
    )
    Box(contentAlignment = Alignment.Center) {
        IconButton(
            onClick = {
                haptics.tick()
                onClick()
            },
            modifier = Modifier
                .size(48.dp)
                .semantics { contentDescription = description },
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = colors.surfaceContainerHigh,
                contentColor = colors.onSurface,
            ),
        ) {
            Crossfade(targetState = state is UpdateState.Checking, label = "updateIcon") { checking ->
                if (checking) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = colors.primary)
                } else {
                    Icon(painterResource(R.drawable.ic_update), contentDescription = null, modifier = Modifier.size(22.dp))
                }
            }
        }
        val download = available?.download as? DownloadState.InProgress
        if (download != null) {
            val ring = Modifier.size(48.dp)
            val progress = download.progress
            if (progress != null) {
                CircularProgressIndicator(progress = { progress }, modifier = ring, strokeWidth = 2.5.dp, color = colors.primary, trackColor = colors.surfaceContainerHigh)
            } else {
                CircularProgressIndicator(modifier = ring, strokeWidth = 2.5.dp, color = colors.primary)
            }
        } else if (available != null) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-4).dp, y = 4.dp)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(colors.primary)
                    .border(2.dp, colors.surface, CircleShape),
            )
        }
    }
}

/** Release details, notes, download progress and the install action. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UpdateSheet(state: UpdateState.Available, currentVersion: String, onDismiss: () -> Unit, onPrimary: () -> Unit, onCancelDownload: () -> Unit, onOpenReleasePage: () -> Unit) {
    val context = LocalContext.current
    val haptics = LocalGyroHaptics.current
    val colors = MaterialTheme.colorScheme
    val release = state.release
    val download = state.download
    val downloading = download is DownloadState.InProgress

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header
            Column {
                Text(
                    text = stringResource(R.string.update_sheet_overline).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.primary,
                )
                Text(
                    text = stringResource(R.string.update_sheet_title, release.version),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                )
                val size = release.apkSize.takeIf { it > 0 }?.let { Formatter.formatShortFileSize(context, it) }
                Text(
                    text = if (size != null) {
                        stringResource(R.string.update_sheet_current_size, currentVersion, size)
                    } else {
                        stringResource(R.string.update_sheet_current, currentVersion)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }

            // What's new
            GroupedSurface(GroupPosition.SINGLE, color = colors.surfaceContainerHigh) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.update_whats_new),
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.primary,
                            modifier = Modifier.weight(1f),
                        )
                        if (release.name != release.tag && release.name != release.version) {
                            Text(release.name, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                        }
                    }
                    Text(
                        text = release.notes.ifBlank { stringResource(R.string.update_no_changelog) },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (release.notes.isBlank()) colors.onSurfaceVariant else colors.onSurface,
                        modifier = Modifier
                            .heightIn(max = 260.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            }

            // Progress / status
            AnimatedVisibility(visible = downloading) {
                val progress = (download as? DownloadState.InProgress)?.progress
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(R.string.update_downloading),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        if (progress != null) {
                            Text("${(progress * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                    val bar = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                    if (progress != null) LinearProgressIndicator(progress = { progress }, modifier = bar) else LinearProgressIndicator(bar)
                }
            }
            val status = when {
                download is DownloadState.Failed -> stringResource(R.string.update_download_failed)
                state.needsInstallPermission -> stringResource(R.string.update_install_permission)
                release.apkUrl == null -> stringResource(R.string.update_no_apk)
                else -> null
            }
            if (status != null) {
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (download is DownloadState.Failed) colors.error else colors.onSurfaceVariant,
                )
            }

            // Actions
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = {
                        haptics.tick()
                        if (downloading) onCancelDownload() else onDismiss()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) { Text(stringResource(if (downloading) R.string.update_action_cancel else R.string.update_action_later)) }
                Button(
                    onClick = {
                        haptics.tick()
                        onPrimary()
                    },
                    enabled = !downloading,
                    modifier = Modifier
                        .weight(1.5f)
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    if (downloading) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.update_downloading))
                    } else {
                        Icon(painterResource(R.drawable.ic_update), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(primaryLabel(release, download)))
                    }
                }
            }
            if (release.apkUrl != null) {
                TextButton(onClick = onOpenReleasePage, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(stringResource(R.string.update_action_open_page))
                }
            }
        }
    }
}

private fun primaryLabel(release: ReleaseInfo, download: DownloadState): Int = when {
    download is DownloadState.Done -> R.string.update_action_install
    download is DownloadState.Failed -> R.string.update_action_retry
    release.apkUrl == null -> R.string.update_action_open_page
    else -> R.string.update_action_download
}
