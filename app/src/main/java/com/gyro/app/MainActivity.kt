package com.gyro.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gyro.app.ui.navigation.GyroApp
import com.gyro.app.ui.theme.GyroTheme
import com.gyro.app.ui.theme.isDark

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
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
                GyroTheme(themeMode = prefs.themeMode, accent = prefs.accent) {
                    GyroApp(prefs)
                }
            }
        }
    }
}
