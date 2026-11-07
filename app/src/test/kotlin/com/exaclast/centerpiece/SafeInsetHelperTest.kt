package com.exaclast.centerpiece

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeInsetHelperTest {

    @Test
    fun testCalculateSafeCenterBounds_standardDesktop() {
        val screenWidth = 2880
        val statusBarHeight = 59

        val bounds = SafeInsetHelper.calculateSafeCenterBounds(
            screenWidth = screenWidth,
            statusBarHeight = statusBarHeight,
            leftSystemClusterWidth = 400,
            rightSystemClusterWidth = 450
        )

        assertEquals(59, bounds.height)
        assertEquals(400, bounds.safeLeftMargin)
        assertEquals(450, bounds.safeRightMargin)
        // Max allowed center width = 2880 - 400 - 450 = 2030
        assertEquals(2030, bounds.maxCenterWidth)
        assertTrue("Max center width should be positive", bounds.maxCenterWidth > 0)
    }

    @Test
    fun testCalculateSafeCenterBounds_compactDisplay() {
        val bounds = SafeInsetHelper.calculateSafeCenterBounds(
            screenWidth = 1280,
            statusBarHeight = 48,
            leftSystemClusterWidth = 200,
            rightSystemClusterWidth = 250
        )

        assertEquals(48, bounds.height)
        assertEquals(830, bounds.maxCenterWidth)
    }

    @Test
    fun testClampedCenterWidth() {
        val bounds = SafeInsetHelper.calculateSafeCenterBounds(
            screenWidth = 2880,
            statusBarHeight = 59,
            leftSystemClusterWidth = 400,
            rightSystemClusterWidth = 450
        )

        // If content is 600px, it fits easily
        val clamped600 = bounds.clampContentWidth(600)
        assertEquals(600, clamped600)

        // If content requested is 2500px, it must be clamped to 2030 to prevent corner clashing
        val clamped2500 = bounds.clampContentWidth(2500)
        assertEquals(2030, clamped2500)
    }
}
