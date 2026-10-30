package com.gyro.app.core.math

/**
 * How the screen is rotated relative to the device's natural orientation
 * (mirrors `android.view.Surface.ROTATION_*`; ROTATION_90 means the device is turned
 * 90° counter-clockwise, so its +X axis now points up the screen).
 */
enum class ScreenRotation {
    ROTATION_0, ROTATION_90, ROTATION_180, ROTATION_270;

    /** Device-frame unit vector pointing towards the top of the screen. */
    val screenUp: Vec3
        get() = when (this) {
            ROTATION_0 -> Vec3.Y
            ROTATION_90 -> Vec3.X
            ROTATION_180 -> -Vec3.Y
            ROTATION_270 -> -Vec3.X
        }

    /** Device-frame unit vector pointing towards the right of the screen. */
    val screenRight: Vec3
        get() = when (this) {
            ROTATION_0 -> Vec3.X
            ROTATION_90 -> -Vec3.Y
            ROTATION_180 -> -Vec3.X
            ROTATION_270 -> Vec3.Y
        }

    /** Re-expresses a device-frame vector in screen axes (x right, y up, z out of the screen). */
    fun toScreen(device: Vec3): Vec3 = Vec3(device dot screenRight, device dot screenUp, device.z)

    companion object {
        /** Maps `Display.getRotation()` / `Surface.ROTATION_*` (0..3). */
        fun fromSurfaceRotation(value: Int): ScreenRotation = when (value) {
            1 -> ROTATION_90
            2 -> ROTATION_180
            3 -> ROTATION_270
            else -> ROTATION_0
        }
    }
}
