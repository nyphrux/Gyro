package com.gyro.app.platform.sensor

import android.hardware.display.DisplayManager
import android.os.Handler
import android.view.Display
import com.gyro.app.core.math.ScreenRotation

/**
 * Caches the default display's rotation and keeps it current via [DisplayManager.DisplayListener],
 * so sensor callbacks can read it at 50 Hz without querying the display every time.
 */
internal class DisplayRotationTracker(private val displayManager: DisplayManager) {
    @Volatile
    var rotation: ScreenRotation = read()
        private set

    private val listener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY) rotation = read()
        }
    }

    fun start(handler: Handler) {
        rotation = read()
        displayManager.registerDisplayListener(listener, handler)
    }

    fun stop() {
        displayManager.unregisterDisplayListener(listener)
    }

    private fun read(): ScreenRotation =
        ScreenRotation.fromSurfaceRotation(displayManager.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: 0)
}
