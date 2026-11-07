package com.exaclast.centerpiece.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.exaclast.centerpiece.CenterpieceItem
import com.exaclast.centerpiece.CenterpiecePreferences
import com.exaclast.centerpiece.ICenterpieceCallback
import com.exaclast.centerpiece.ICenterpieceService
import com.exaclast.centerpiece.KeepAwakeManager
import com.exaclast.centerpiece.MainActivity
import com.exaclast.centerpiece.R
import com.exaclast.centerpiece.SystemMonitorCoordinator
import com.exaclast.centerpiece.WidgetRegistry
import com.exaclast.centerpiece.overlay.OverlayController
import com.exaclast.centerpiece.server.CenterpieceServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Core host service managing the lifecycle, status bar safe bounds, background monitors,
 * and IPC widget registry for Googlebook OS.
 */
class CenterpieceHostService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var windowManager: WindowManager

    val registry = globalRegistry

    private val binder = object : ICenterpieceService.Stub() {
        override fun registerItem(item: CenterpieceItem, callback: ICenterpieceCallback?): Boolean {
            return registry.registerItem(item, callback)
        }

        override fun updateItem(item: CenterpieceItem): Boolean {
            return registry.updateItem(item)
        }

        override fun unregisterItem(itemId: String): Boolean {
            return registry.unregisterItem(itemId)
        }

        override fun getItems(): List<CenterpieceItem> {
            return registry.getAllItems()
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        activeServiceInstance = this
        isRunning = true
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        startForegroundServiceNotification()
        ensureDefaultWidgets(this)
        SystemMonitorCoordinator.start(this, globalRegistry)
        CenterpieceServer.startGlobalServer(this, globalRegistry)

        // Attach application overlay if accessibility overlay is not active AND overlay is enabled
        CenterpiecePreferences.init(this)
        if (!CenterpieceAccessibilityService.isAccessibilityActive && CenterpiecePreferences.isOverlayEnabled(this)) {
            setupApplicationOverlay()
        }

        // Observe master enabled preference
        serviceScope.launch {
            CenterpiecePreferences.enabledFlow.collectLatest { enabled ->
                if (enabled) {
                    if (!CenterpieceAccessibilityService.isAccessibilityActive) {
                        setupApplicationOverlay()
                    }
                } else {
                    detachApplicationOverlay()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            CenterpiecePreferences.setOverlayEnabled(this, false)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        activeServiceInstance = null
        serviceScope.cancel()

        SystemMonitorCoordinator.stop(this)

        detachApplicationOverlay()

        if (!CenterpieceAccessibilityService.isAccessibilityActive) {
            CenterpieceServer.stopGlobalServer()
        }
    }

    private fun startForegroundServiceNotification() {
        val channelId = CHANNEL_ID
        val channelName = "Centerpiece Status Bar Overlay"
        val notifManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            channelId,
            channelName,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Maintains persistent top-bar status items and modular widgets"
            setShowBadge(false)
        }
        notifManager.createNotificationChannel(channel)

        val openSettingsIntent = Intent(this, MainActivity::class.java)
        val pendingSettings = PendingIntent.getActivity(
            this,
            0,
            openSettingsIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, CenterpieceHostService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val pendingStop = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_centerpiece)
            .setContentTitle("Centerpiece Active")
            .setContentText("Top-bar status bar overlay is running")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingSettings)
            .addAction(0, "Settings", pendingSettings)
            .addAction(0, "Stop", pendingStop)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun setupApplicationOverlay() {
        if (overlayController == null) {
            overlayController = OverlayController(
                context = this,
                windowManager = windowManager,
                windowType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                registry = globalRegistry,
                scope = serviceScope
            )
            overlayController?.attach()
        }
    }

    companion object {
        const val TAG = "Centerpiece"
        const val CHANNEL_ID = "centerpiece_overlay_channel"
        const val NOTIFICATION_ID = 40401
        const val ACTION_STOP_SERVICE = "com.exaclast.centerpiece.STOP_SERVICE"

        var isRunning: Boolean = false
            private set

        private var activeServiceInstance: CenterpieceHostService? = null
        private var overlayController: OverlayController? = null

        val globalRegistry = WidgetRegistry()

        fun ensureDefaultWidgets(context: Context) {
            if (globalRegistry.getItem("builtin.logo") == null) {
                globalRegistry.registerItem(
                    CenterpieceItem(
                        id = "builtin.logo",
                        packageName = context.packageName,
                        title = "Centerpiece",
                        iconRes = R.drawable.ic_centerpiece,
                        priority = 1000,
                        isBuiltIn = true
                    )
                )
            }
            if (globalRegistry.getItem("builtin.sysmon") == null) {
                globalRegistry.registerItem(
                    CenterpieceItem(
                        id = "builtin.sysmon",
                        packageName = context.packageName,
                        title = "CPU: --",
                        subtitle = "RAM: --",
                        iconRes = R.drawable.ic_cpu,
                        priority = 900,
                        isBuiltIn = true
                    )
                )
            }
            // Purge legacy media and clipboard chips if present
            globalRegistry.unregisterItem("builtin.media")
            globalRegistry.unregisterItem("builtin.clipboard")

            // Initialize Keep Awake manager
            KeepAwakeManager.init(context, globalRegistry)
        }

        fun detachApplicationOverlay() {
            Log.d(TAG, "Detaching application overlay")
            overlayController?.detach()
            overlayController = null
        }

        fun reattachApplicationOverlayIfRunning(context: Context) {
            if (isRunning && !CenterpieceAccessibilityService.isAccessibilityActive && CenterpiecePreferences.isOverlayEnabled(context)) {
                activeServiceInstance?.setupApplicationOverlay()
            }
        }

        fun start(context: Context) {
            val intent = Intent(context, CenterpieceHostService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, CenterpieceHostService::class.java)
            context.stopService(intent)
        }
    }
}
