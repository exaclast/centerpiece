package com.exaclast.centerpiece

import android.content.Context
import android.graphics.Insets
import android.view.WindowInsets
import android.view.WindowManager

/**
 * Data class representing safe horizontal and vertical boundaries for the Centerpiece bar.
 */
data class SafeCenterBounds(
    val height: Int,
    val safeLeftMargin: Int,
    val safeRightMargin: Int,
    val maxCenterWidth: Int
) {
    /**
     * Clamps content width to ensure it never encroaches into left or right system UI clusters.
     */
    fun clampContentWidth(requestedWidth: Int): Int {
        return requestedWidth.coerceIn(0, maxCenterWidth)
    }
}

/**
 * Helper to compute status bar insets and safe central regions on desktop displays (Googlebook OS).
 */
object SafeInsetHelper {

    /**
     * Calculates the safe central boundary between the left system cluster (Clock/Calendar)
     * and the right system cluster (Quick Settings / Battery / Wi-Fi / Notifications / Pointer).
     */
    fun calculateSafeCenterBounds(
        screenWidth: Int,
        statusBarHeight: Int,
        leftSystemClusterWidth: Int = 400,
        rightSystemClusterWidth: Int = 450
    ): SafeCenterBounds {
        val availableWidth = (screenWidth - leftSystemClusterWidth - rightSystemClusterWidth).coerceAtLeast(0)
        return SafeCenterBounds(
            height = statusBarHeight,
            safeLeftMargin = leftSystemClusterWidth,
            safeRightMargin = rightSystemClusterWidth,
            maxCenterWidth = availableWidth
        )
    }

    /**
     * Obtains the exact status bar height dynamically from WindowInsets or falls back to standard resources.
     */
    fun getStatusBarHeight(context: Context): Int {
        try {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            if (windowManager != null) {
                val metrics = windowManager.currentWindowMetrics
                val insets: Insets = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars())
                if (insets.top > 0) {
                    return insets.top
                }
            }

            // Fallback to internal dimension resource if available
            val resourceId = context.resources?.getIdentifier("status_bar_height", "dimen", "android") ?: 0
            if (resourceId > 0) {
                val dimen = context.resources.getDimensionPixelSize(resourceId)
                if (dimen > 0) return dimen
            }
        } catch (_: Exception) {}

        // Conservative desktop default (approx 36dp on 260dpi = 59px)
        val density = try { context.resources?.displayMetrics?.density ?: 1.625f } catch (_: Exception) { 1.625f }
        return (36 * density).toInt()
    }

    /**
     * Obtains current screen width in pixels from WindowMetrics.
     */
    fun getScreenWidth(context: Context): Int {
        try {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            if (windowManager != null) {
                val bounds = windowManager.currentWindowMetrics.bounds
                return bounds.width()
            }
            return context.resources?.displayMetrics?.widthPixels ?: 2880
        } catch (_: Exception) {
            return 2880
        }
    }

    /**
     * Obtains current screen height in pixels from WindowMetrics.
     */
    fun getScreenHeight(context: Context): Int {
        try {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            if (windowManager != null) {
                val bounds = windowManager.currentWindowMetrics.bounds
                return bounds.height()
            }
            return context.resources?.displayMetrics?.heightPixels ?: 1800
        } catch (_: Exception) {
            return 1800
        }
    }
}
