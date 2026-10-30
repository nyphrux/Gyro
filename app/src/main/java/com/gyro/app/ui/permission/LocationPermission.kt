package com.gyro.app.ui.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.gyro.app.core.speed.LocationAccess

/**
 * Location permission state for one screen.
 *
 * - [access] is re-read on every resume, so changes made in system settings are picked up.
 * - [isBlocked] becomes true when a request returns denied and the system will no longer show
 *   its dialog ("don't ask again"); the UI then offers app settings instead.
 * - [precise] decides what we ask for: the speedometer needs FINE (always requested together
 *   with COARSE so Android 12+ can offer the precision choice); true north needs only COARSE.
 */
@Stable
class LocationPermissionState internal constructor(private val context: Context, val precise: Boolean) {
    var access: LocationAccess by mutableStateOf(LocationAccess.UNKNOWN)
        private set

    var isBlocked: Boolean by mutableStateOf(false)
        private set

    internal var launch: () -> Unit = {}

    /** True when the permission needed for [precise] is held. */
    val isSatisfied: Boolean
        get() = access == LocationAccess.GRANTED || (!precise && access == LocationAccess.APPROXIMATE_ONLY)

    fun request() = launch()

    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun openLocationSettings() {
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    internal fun refresh() {
        val fine = context.isGranted(Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = context.isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)
        val satisfied = if (precise) fine else (fine || coarse)
        if (satisfied) isBlocked = false
        access = when {
            fine -> LocationAccess.GRANTED
            coarse -> LocationAccess.APPROXIMATE_ONLY
            isBlocked -> LocationAccess.PERMANENTLY_DENIED
            else -> LocationAccess.DENIED
        }
    }

    internal fun onRequestResult(activity: Activity?) {
        val wanted = if (precise) Manifest.permission.ACCESS_FINE_LOCATION else Manifest.permission.ACCESS_COARSE_LOCATION
        val granted = context.isGranted(wanted)
        // Denied and the system says no rationale is needed: it will not show the dialog again.
        isBlocked = !granted && activity != null && !activity.shouldShowRequestPermissionRationale(wanted)
        refresh()
    }
}

@Composable
fun rememberLocationPermissionState(precise: Boolean, onResult: (LocationPermissionState) -> Unit = {}, ): LocationPermissionState {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val state = remember(context, precise) { LocationPermissionState(context, precise).also { it.refresh() } }
    val currentOnResult by rememberUpdatedState(onResult)

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        state.onRequestResult(activity)
        currentOnResult(state)
    }
    SideEffect {
        state.launch = {
            val permissions = if (precise) {
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            } else {
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            launcher.launch(permissions)
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { state.refresh() }
    return state
}

private fun Context.isGranted(permission: String): Boolean = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
