package com.exaclast.centerpiece.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.exaclast.centerpiece.data.CenterpieceDashboardState
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** UI tests for [com.exaclast.centerpiece.ui.main.MainScreen]. */
class MainScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setup() {
        val testState = CenterpieceDashboardState(
            isOverlayPermissionGranted = true,
            isServiceRunning = true,
            statusBarHeight = 59
        )
        composeTestRule.setContent {
            MainScreenContent(
                state = testState,
                onToggleService = {},
                onAddSampleWidget = {},
                onRemoveWidget = {}
            )
        }
    }

    @Test
    fun titleAndStatus_areDisplayed() {
        composeTestRule.onNodeWithText("Centerpiece").assertIsDisplayed()
        composeTestRule.onNodeWithText("Centerpiece Active").assertIsDisplayed()
    }
}
