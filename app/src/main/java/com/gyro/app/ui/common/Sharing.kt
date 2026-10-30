package com.gyro.app.ui.common

/**
 * How long sensor / location pipelines keep running after the UI stops collecting.
 * Long enough to survive a configuration change, short enough that switching tools or
 * backgrounding the app releases the hardware almost immediately.
 */
const val STOP_TIMEOUT = 1_500L
