package com.gyro.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gyro.app.ui.navigation.GyroApp
import com.gyro.app.ui.navigation.Tool
import com.gyro.app.ui.theme.GyroTheme
import com.gyro.app.ui.theme.isDark

class MainActivity : ComponentActivity() {
    /** A tool asked for by a launcher shortcut, until the UI has switched to it. */
    private val requestedTool = mutableStateOf<Tool?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // On recreation the saved UI state already holds the user's current tool.
        if (savedInstanceState == null) requestedTool.value = intent.requestedTool()
        val preferencesFlow = (application as GyroApplication).container.preferences.preferences

        setContent {
            // DataStore answers within a frame or two; until then the themed window background shows.
            val preferences by preferencesFlow.collectAsStateWithLifecycle(initialValue = null)
            val prefs = preferences
            if (prefs != null) {
                val dark = prefs.themeMode.isDark()
                // Keep system bar icons legible when the in-app theme differs from the system one.
                DisposableEffect(dark) {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                        navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    )
                    onDispose { }
                }
                GyroTheme(themeMode = prefs.themeMode, accent = prefs.accent, customAccent = prefs.customAccent) {
                    GyroApp(
                        preferences = prefs,
                        requestedTool = requestedTool.value,
                        onToolRequestHandled = { requestedTool.value = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.requestedTool()?.let { requestedTool.value = it }
    }

    private fun Intent?.requestedTool(): Tool? = Tool.fromName(this?.getStringExtra(Tool.EXTRA_TOOL))
}
