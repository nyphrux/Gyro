package com.gyro.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gyro.app.core.speed.SpeedUnit
import com.gyro.app.ui.components.GyroNavBar
import com.gyro.app.ui.components.PillSelector
import com.gyro.app.ui.navigation.Tool
import com.gyro.app.ui.theme.GyroTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComponentsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun navBar_selectsToolOnClick() {
        var selected by mutableStateOf(Tool.COMPASS)
        compose.setContent {
            GyroTheme(reducedMotion = true) {
                GyroNavBar(selected = selected, onSelect = { selected = it })
            }
        }
        compose.onNodeWithTag("nav_COMPASS").assertIsSelected()
        compose.onNodeWithTag("nav_TILT").assertIsNotSelected().performClick()
        compose.runOnIdle { assertEquals(Tool.TILT, selected) }
        compose.onNodeWithTag("nav_TILT").assertIsSelected()
        compose.onNodeWithTag("nav_COMPASS").assertIsNotSelected()
    }

    @Test
    fun pillSelector_changesUnit() {
        var chosen: SpeedUnit? = null
        compose.setContent {
            GyroTheme(reducedMotion = true) {
                var unit by remember { mutableStateOf(SpeedUnit.KILOMETRES_PER_HOUR) }
                PillSelector(
                    options = SpeedUnit.entries,
                    selected = unit,
                    onSelect = {
                        unit = it
                        chosen = it
                    },
                    label = { it.symbol },
                )
            }
        }
        compose.onNodeWithText("mph").performClick()
        compose.runOnIdle { assertEquals(SpeedUnit.MILES_PER_HOUR, chosen) }
        compose.onNodeWithText("mph").assertIsSelected()
    }
}
