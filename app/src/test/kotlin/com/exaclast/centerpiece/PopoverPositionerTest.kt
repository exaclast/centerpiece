package com.exaclast.centerpiece

import org.junit.Assert.assertEquals
import org.junit.Test

class PopoverPositionerTest {

    @Test
    fun testPositionCenteredAnchor() {
        // Screen 2880x1800, anchor in exact center
        // Anchor: x=1400, width=80, height=59
        // Popover: width=300, height=200
        val pos = PopoverPositioner.calculatePosition(
            anchorX = 1400,
            anchorY = 0,
            anchorWidth = 80,
            anchorHeight = 59,
            popoverWidth = 300,
            popoverHeight = 200,
            screenWidth = 2880,
            screenHeight = 1800,
            margin = 16,
            gap = 6
        )

        // targetX = 1400 + (80 - 300) / 2 = 1400 - 110 = 1290
        assertEquals(1290, pos.x)
        // targetY = 0 + 59 + 6 = 65
        assertEquals(65, pos.y)
    }

    @Test
    fun testClampLeftEdge() {
        // Anchor near far left edge: anchorX = 20, width = 60
        // targetX without clamp: 20 + (60 - 300) / 2 = 20 - 120 = -100
        // With margin = 16, should clamp to 16
        val pos = PopoverPositioner.calculatePosition(
            anchorX = 20,
            anchorY = 0,
            anchorWidth = 60,
            anchorHeight = 59,
            popoverWidth = 300,
            popoverHeight = 200,
            screenWidth = 2880,
            screenHeight = 1800,
            margin = 16,
            gap = 6
        )

        assertEquals(16, pos.x)
        assertEquals(65, pos.y)
    }

    @Test
    fun testClampRightEdge() {
        // Anchor near far right edge: anchorX = 2800, width = 60
        // targetX without clamp: 2800 + (60 - 300) / 2 = 2690
        // Max x = 2880 - 300 - 16 = 2564
        // Should clamp to 2564
        val pos = PopoverPositioner.calculatePosition(
            anchorX = 2800,
            anchorY = 0,
            anchorWidth = 60,
            anchorHeight = 59,
            popoverWidth = 300,
            popoverHeight = 200,
            screenWidth = 2880,
            screenHeight = 1800,
            margin = 16,
            gap = 6
        )

        assertEquals(2564, pos.x)
        assertEquals(65, pos.y)
    }
}
