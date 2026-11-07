package com.exaclast.centerpiece.overlay

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import com.exaclast.centerpiece.BarAlignment
import com.exaclast.centerpiece.CenterpieceItem
import com.exaclast.centerpiece.CenterpiecePreferences
import com.exaclast.centerpiece.KeepAwakeManager
import com.exaclast.centerpiece.LayoutConfig
import com.exaclast.centerpiece.MainActivity
import com.exaclast.centerpiece.SafeInsetHelper
import com.exaclast.centerpiece.WidgetRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Manages the overlay bar view lifecycle and window parameters for either
 * TYPE_ACCESSIBILITY_OVERLAY (Z-order layer 30, above status bar) or
 * TYPE_APPLICATION_OVERLAY (Z-order layer 11).
 */
class OverlayController(
    private val context: Context,
    private val windowManager: WindowManager,
    private val windowType: Int,
    private val registry: WidgetRegistry,
    private val scope: CoroutineScope
) {

    private var overlayBarView: CenterpieceBarView? = null
    private var popoverCoordinator: PopoverCoordinator? = null
    private var observerJob: Job? = null
    private var layoutObserverJob: Job? = null
    private var currentLayoutParams: WindowManager.LayoutParams? = null

    val isAttached: Boolean
        get() = overlayBarView != null

    fun attach() {
        if (overlayBarView != null) return

        popoverCoordinator = PopoverCoordinator(
            context = context,
            windowManager = windowManager,
            windowType = windowType,
            onActionTriggered = { itemId, actionId, _ ->
                registry.dispatchActionClick(itemId, actionId)
            },
            onDismissed = { itemId ->
                overlayBarView?.activeItemId = null
                registry.dispatchPopoverDismissed(itemId)
            }
        )

        val barView = CenterpieceBarView(context) { item, anchorView ->
            handleChipClicked(item, anchorView)
        }

        val config = CenterpiecePreferences.getLayoutConfig(context)
        val density = context.resources.displayMetrics.density
        val statusBarHeight = SafeInsetHelper.getStatusBarHeight(context)
        val screenWidth = SafeInsetHelper.getScreenWidth(context)
        val safeBounds = SafeInsetHelper.calculateSafeCenterBounds(
            screenWidth = screenWidth,
            statusBarHeight = statusBarHeight
        )
        barView.updateSafeBounds(safeBounds)

        val heightPx = if (config.heightDp > 0) {
            (config.heightDp * density).toInt()
        } else {
            statusBarHeight
        }

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            heightPx,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            when (config.alignment) {
                BarAlignment.CENTER -> {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    x = (config.offsetX * density).toInt()
                }
                BarAlignment.LEFT -> {
                    gravity = Gravity.TOP or Gravity.START
                    x = safeBounds.safeLeftMargin + (config.offsetX * density).toInt()
                }
                BarAlignment.RIGHT -> {
                    gravity = Gravity.TOP or Gravity.END
                    x = (safeBounds.safeRightMargin - (config.offsetX * density)).toInt()
                }
            }
            y = (config.offsetY * density).toInt()
        }
        currentLayoutParams = layoutParams

        try {
            Log.d("Centerpiece", "Attaching overlay window with type=$windowType, height=$heightPx, alignment=${config.alignment}")
            windowManager.addView(barView, layoutParams)
            overlayBarView = barView

            // Immediately populate with current items
            barView.submitItems(registry.getAllItems())

            // Observe widget registry updates
            observerJob = scope.launch(Dispatchers.Main) {
                registry.itemsFlow.collectLatest { items ->
                    val view = overlayBarView ?: return@collectLatest
                    view.submitItems(items)
                    currentLayoutParams?.let { params ->
                        try {
                            windowManager.updateViewLayout(view, params)
                        } catch (e: Exception) {
                            Log.w("Centerpiece", "updateViewLayout error: ${e.message}")
                        }
                    }
                }
            }

            // Observe layout configuration updates dynamically
            layoutObserverJob = scope.launch(Dispatchers.Main) {
                CenterpiecePreferences.layoutConfigFlow.collectLatest { newConfig ->
                    applyLayoutConfig(newConfig)
                }
            }

            // Sync Keep Awake flag dynamically with window
            KeepAwakeManager.setWindowKeepAwakeCallback { shouldKeepAwake ->
                val view = overlayBarView ?: return@setWindowKeepAwakeCallback
                val params = currentLayoutParams ?: return@setWindowKeepAwakeCallback
                if (shouldKeepAwake) {
                    params.flags = params.flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                } else {
                    params.flags = params.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON.inv()
                }
                try {
                    windowManager.updateViewLayout(view, params)
                } catch (e: Exception) {
                    Log.w("Centerpiece", "updateViewLayout keepAwake error: ${e.message}")
                }
            }

            // Sync with current app visibility policy (fullscreen / blacklist / whitelist)
            if (!com.exaclast.centerpiece.AppVisibilityCoordinator.isOverlayVisibleFlow.value) {
                setOverlayVisible(false)
            }
        } catch (e: Exception) {
            Log.e("Centerpiece", "Failed to add overlay window", e)
        }
    }

    /**
     * Dynamically updates the window positioning, gravity, offsets, and height on the fly.
     */
    fun applyLayoutConfig(config: LayoutConfig) {
        val barView = overlayBarView ?: return
        val params = currentLayoutParams ?: return
        val density = context.resources.displayMetrics.density
        val statusBarHeight = SafeInsetHelper.getStatusBarHeight(context)
        val screenWidth = SafeInsetHelper.getScreenWidth(context)
        val safeBounds = SafeInsetHelper.calculateSafeCenterBounds(
            screenWidth = screenWidth,
            statusBarHeight = statusBarHeight
        )
        barView.updateSafeBounds(safeBounds)

        val heightPx = if (config.heightDp > 0) {
            (config.heightDp * density).toInt()
        } else {
            statusBarHeight
        }
        params.height = heightPx

        when (config.alignment) {
            BarAlignment.CENTER -> {
                params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                params.x = (config.offsetX * density).toInt()
            }
            BarAlignment.LEFT -> {
                params.gravity = Gravity.TOP or Gravity.START
                params.x = safeBounds.safeLeftMargin + (config.offsetX * density).toInt()
            }
            BarAlignment.RIGHT -> {
                params.gravity = Gravity.TOP or Gravity.END
                params.x = (safeBounds.safeRightMargin - (config.offsetX * density)).toInt()
            }
        }
        params.y = (config.offsetY * density).toInt()

        try {
            Log.d("Centerpiece", "Updating overlay layout: align=${config.alignment}, x=${params.x}, y=${params.y}, height=${params.height}")
            windowManager.updateViewLayout(barView, params)
        } catch (e: Exception) {
            Log.w("Centerpiece", "Failed to updateViewLayout with config: $config", e)
        }
    }

    fun setOverlayVisible(visible: Boolean) {
        val barView = overlayBarView ?: return
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            barView.post { setOverlayVisible(visible) }
            return
        }
        val params = currentLayoutParams ?: return

        if (visible) {
            barView.visibility = android.view.View.VISIBLE
            params.alpha = 1.0f
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            try {
                windowManager.updateViewLayout(barView, params)
            } catch (e: Exception) {
                Log.w("Centerpiece", "Error updating overlay visible: ${e.message}")
            }
        } else {
            popoverCoordinator?.dismiss()
            barView.visibility = android.view.View.GONE
            params.alpha = 0.0f
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            try {
                windowManager.updateViewLayout(barView, params)
            } catch (e: Exception) {
                Log.w("Centerpiece", "Error updating overlay hidden: ${e.message}")
            }
        }
    }

    fun detach() {
        KeepAwakeManager.setWindowKeepAwakeCallback { }

        observerJob?.cancel()
        observerJob = null

        layoutObserverJob?.cancel()
        layoutObserverJob = null
        currentLayoutParams = null

        popoverCoordinator?.dismiss()
        popoverCoordinator = null

        overlayBarView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: Exception) {
            }
        }
        overlayBarView = null
    }

    private fun handleChipClicked(item: CenterpieceItem, anchorView: StatusChipView) {
        val coordinator = popoverCoordinator
        val now = android.os.SystemClock.uptimeMillis()

        if (coordinator != null) {
            val recentlyDismissedSame = coordinator.lastDismissedItemId == item.id &&
                (now - coordinator.lastDismissedTime) < 350L

            if (coordinator.isShowing) {
                val wasSameItem = coordinator.activeItemId == item.id
                coordinator.dismiss()
                overlayBarView?.activeItemId = null
                if (wasSameItem) {
                    return
                }
            } else if (recentlyDismissedSame) {
                // Tap on this chip dismissed the popover via ACTION_OUTSIDE
                overlayBarView?.activeItemId = null
                return
            }
        }

        // Centerpiece logo chip opens Settings dashboard directly
        if (item.id == "builtin.logo") {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            context.startActivity(intent)
            return
        }

        overlayBarView?.activeItemId = item.id
        popoverCoordinator?.showPopover(item, anchorView)

        if (item.clickIntent != null) {
            try {
                item.clickIntent.send()
            } catch (_: Exception) {
            }
        }

        registry.dispatchItemClick(item.id)
    }
}
