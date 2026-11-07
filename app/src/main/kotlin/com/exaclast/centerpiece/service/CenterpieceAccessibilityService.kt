package com.exaclast.centerpiece.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.exaclast.centerpiece.AppVisibilityCoordinator
import com.exaclast.centerpiece.CenterpiecePreferences
import com.exaclast.centerpiece.SystemMonitorCoordinator
import com.exaclast.centerpiece.overlay.OverlayController
import com.exaclast.centerpiece.server.CenterpieceServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Enhanced Desktop Overlay Service leveraging TYPE_ACCESSIBILITY_OVERLAY (Z-order layer 30).
 * This ensures Centerpiece renders ABOVE the system status bar (layer 15) even when desktop
 * application windows are maximized and the status bar paints its black background.
 */
class CenterpieceAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var overlayController: OverlayController? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "CenterpieceAccessibilityService connected. Promoting overlay to Layer 30 (Above Status Bar).")
        isAccessibilityActive = true

        // Ensure host service has registered default widgets
        CenterpieceHostService.ensureDefaultWidgets(this)

        // Start live system monitors (CPU/RAM frequency scaling and Clipboard listener)
        SystemMonitorCoordinator.start(this, CenterpieceHostService.globalRegistry)

        // Start desktop REST server daemon
        CenterpieceServer.startGlobalServer(this, CenterpieceHostService.globalRegistry)

        // Detach foreground service overlay if it was using TYPE_APPLICATION_OVERLAY
        CenterpieceHostService.detachApplicationOverlay()

        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        overlayController = OverlayController(
            context = this,
            windowManager = windowManager,
            windowType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            registry = CenterpieceHostService.globalRegistry,
            scope = serviceScope
        )

        // Configure accessibility event types and window retrieval flags
        try {
            val info = serviceInfo ?: AccessibilityServiceInfo()
            info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOWS_CHANGED
            info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            serviceInfo = info
        } catch (e: Exception) {
            Log.w(TAG, "Error configuring serviceInfo: ${e.message}")
        }

        // Start App Visibility Coordinator (fullscreen & blacklist/whitelist filtering)
        AppVisibilityCoordinator.start(this, serviceScope)
        AppVisibilityCoordinator.setVisibilityCallback { visible, _ ->
            overlayController?.setOverlayVisible(visible)
        }

        // Observe master enabled preference
        serviceScope.launch {
            CenterpiecePreferences.init(this@CenterpieceAccessibilityService)
            CenterpiecePreferences.enabledFlow.collectLatest { enabled ->
                Log.i(TAG, "Master overlay enabled state changed: $enabled")
                if (enabled) {
                    if (overlayController?.isAttached != true) {
                        overlayController?.attach()
                    }
                } else {
                    if (overlayController?.isAttached == true) {
                        overlayController?.detach()
                    }
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        AppVisibilityCoordinator.onAccessibilityEvent(event)
    }

    override fun onInterrupt() {
        Log.i(TAG, "CenterpieceAccessibilityService interrupted.")
    }

    override fun onDestroy() {
        super.onDestroy()
        isAccessibilityActive = false
        AppVisibilityCoordinator.stop()
        SystemMonitorCoordinator.stop(this)
        overlayController?.detach()
        overlayController = null
        serviceScope.cancel()

        if (!CenterpieceHostService.isRunning) {
            CenterpieceServer.stopGlobalServer()
        }

        // Fall back to application overlay if host service is still running AND overlay is enabled
        if (CenterpiecePreferences.isOverlayEnabled(this)) {
            CenterpieceHostService.reattachApplicationOverlayIfRunning(this)
        }
    }

    companion object {
        const val TAG = "CenterpieceA11y"
        var isAccessibilityActive: Boolean = false
            private set
    }
}
