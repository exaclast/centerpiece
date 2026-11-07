package com.exaclast.centerpiece

/**
 * Calculated screen coordinates for anchoring a popover overlay.
 */
data class PopoverPosition(
    val x: Int,
    val y: Int
)

/**
 * Calculates geometry for positioning a dropdown/popover sheet anchored beneath a status chip.
 */
object PopoverPositioner {

    /**
     * Computes the top-left (x, y) coordinates for a popover window.
     * Centers the popover under the anchor view, ensuring it remains within display safe margins.
     */
    fun calculatePosition(
        anchorX: Int,
        anchorY: Int,
        anchorWidth: Int,
        anchorHeight: Int,
        popoverWidth: Int,
        popoverHeight: Int,
        screenWidth: Int,
        screenHeight: Int,
        margin: Int = 16,
        gap: Int = 6
    ): PopoverPosition {
        // Ideal X is centered relative to anchor
        val idealX = anchorX + (anchorWidth - popoverWidth) / 2
        // Clamp X between margin and screenWidth - popoverWidth - margin
        val maxX = (screenWidth - popoverWidth - margin).coerceAtLeast(margin)
        val clampedX = idealX.coerceIn(margin, maxX)

        // Ideal Y is immediately below anchor
        val idealY = anchorY + anchorHeight + gap
        // Clamp Y so it doesn't extend beyond bottom of screen
        val maxY = (screenHeight - popoverHeight - margin).coerceAtLeast(margin)
        val clampedY = idealY.coerceIn(margin, maxY)

        return PopoverPosition(x = clampedX, y = clampedY)
    }
}
