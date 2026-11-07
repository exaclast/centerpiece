package com.exaclast.centerpiece

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Coordinates status bar overlay visibility based on:
 * 1. True Fullscreen mode (hiding when an app is in true immersive fullscreen, but NOT when maximized).
 * 2. Blacklist / Whitelist rules against the active foreground application.
 */
object AppVisibilityCoordinator {

    private const val TAG = "AppVisibility"

    private val _isOverlayVisibleFlow = MutableStateFlow(true)
    val isOverlayVisibleFlow: StateFlow<Boolean> = _isOverlayVisibleFlow.asStateFlow()

    private val _currentForegroundPackageFlow = MutableStateFlow<String?>(null)
    val currentForegroundPackageFlow: StateFlow<String?> = _currentForegroundPackageFlow.asStateFlow()

    private val _isAppFullscreenFlow = MutableStateFlow(false)
    val isAppFullscreenFlow: StateFlow<Boolean> = _isAppFullscreenFlow.asStateFlow()

    private val _hideReasonFlow = MutableStateFlow<String?>(null)
    val hideReasonFlow: StateFlow<String?> = _hideReasonFlow.asStateFlow()

    private var activeService: AccessibilityService? = null
    private var trackingJob: Job? = null
    private var visibilityCallback: ((Boolean, String?) -> Unit)? = null
    private var cachedHomePackages: Set<String>? = null

    fun setVisibilityCallback(callback: (Boolean, String?) -> Unit) {
        visibilityCallback = callback
        callback(_isOverlayVisibleFlow.value, _hideReasonFlow.value)
    }

    fun start(service: AccessibilityService, scope: CoroutineScope) {
        activeService = service
        cachedHomePackages = getHomePackages(service)

        // Observe preference changes to re-evaluate visibility immediately
        scope.launch {
            CenterpiecePreferences.hideInFullscreenFlow.collectLatest {
                evaluateFromWindows()
            }
        }
        scope.launch {
            CenterpiecePreferences.appFilterModeFlow.collectLatest {
                evaluateFromWindows()
            }
        }
        scope.launch {
            CenterpiecePreferences.appFilterPackagesFlow.collectLatest {
                evaluateFromWindows()
            }
        }
        scope.launch {
            CenterpiecePreferences.enabledFlow.collectLatest {
                evaluateFromWindows()
            }
        }

        // Periodic window bounds check (every 600ms) to detect internal fullscreen transitions
        // such as HTML5 video fullscreen in Chrome without activity changes.
        trackingJob?.cancel()
        trackingJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                delay(600)
                evaluateFromWindows()
            }
        }

        evaluateFromWindows()
    }

    fun stop() {
        trackingJob?.cancel()
        trackingJob = null
        activeService = null
        visibilityCallback = null
    }

    /**
     * Handles incoming accessibility events to track foreground application switches.
     */
    fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString()
            if (!pkg.isNullOrEmpty() && pkg != "com.android.systemui" && pkg != "android") {
                _currentForegroundPackageFlow.value = pkg
            }
            evaluateFromWindows()
        } else if (event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            evaluateFromWindows()
        }
    }

    /**
     * Inspects active interactive windows from the AccessibilityService to detect
     * true fullscreen windows vs maximized windows.
     */
    fun evaluateFromWindows() {
        val service = activeService ?: return
        val screenWidth = SafeInsetHelper.getScreenWidth(service)
        val screenHeight = SafeInsetHelper.getScreenHeight(service)
        val homePackages = getHomePackages(service)

        val windowList: List<AccessibilityWindowInfo> = try {
            service.windows ?: emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching windows: ${e.message}")
            emptyList()
        }

        var detectedFullscreen = false
        var topForegroundPackage = _currentForegroundPackageFlow.value

        if (windowList.isNotEmpty()) {
            val appWindows = windowList.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            val topWindow = appWindows.firstOrNull { it.isFocused }
                ?: appWindows.firstOrNull { it.isActive }
                ?: appWindows.maxByOrNull { it.layer }

            if (topWindow != null) {
                val bounds = Rect()
                topWindow.getBoundsInScreen(bounds)

                val pkgFromWindow = topWindow.root?.packageName?.toString()
                if (!pkgFromWindow.isNullOrEmpty() && pkgFromWindow != "com.android.systemui" && pkgFromWindow != "android") {
                    topForegroundPackage = pkgFromWindow
                    _currentForegroundPackageFlow.value = pkgFromWindow
                }

                val isHome = homePackages.contains(topForegroundPackage) || topForegroundPackage == "com.google.android.apps.nexuslauncher"
                val isSelf = topForegroundPackage == service.packageName

                if (!isHome && !isSelf) {
                    detectedFullscreen = isTrueFullscreen(bounds, screenWidth, screenHeight)
                }
            }
        }

        _isAppFullscreenFlow.value = detectedFullscreen

        val (visible, reason) = computeVisibility(
            isOverlayEnabled = CenterpiecePreferences.isOverlayEnabled(service),
            isHideInFullscreen = CenterpiecePreferences.isHideInFullscreen(service),
            isAppFullscreen = detectedFullscreen,
            filterMode = CenterpiecePreferences.getAppFilterMode(service),
            filterPackages = CenterpiecePreferences.getAppFilterPackages(service),
            foregroundPackage = topForegroundPackage,
            homePackages = homePackages,
            selfPackage = service.packageName
        )

        val changed = (_isOverlayVisibleFlow.value != visible) || (_hideReasonFlow.value != reason)
        _isOverlayVisibleFlow.value = visible
        _hideReasonFlow.value = reason

        if (changed) {
            Log.i(TAG, "Visibility updated: visible=$visible, reason=$reason, fullscreen=$detectedFullscreen, foreground=$topForegroundPackage")
            val cb = visibilityCallback
            if (cb != null) {
                if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                    cb(visible, reason)
                } else {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        cb(visible, reason)
                    }
                }
            }
        }
    }

    /**
     * Determines whether a window bounding box represents TRUE immersive fullscreen
     * rather than standard maximized desktop windowing.
     *
     * On Googlebook OS:
     * - Maximized Window: Bounds are Rect(0, 59, screenWidth, 1709) with top >= 59.
     * - True Fullscreen (Video/Game): Window bounds start at y <= 10 and span the full display.
     */
    fun isTrueFullscreen(left: Int, top: Int, right: Int, bottom: Int, screenWidth: Int, screenHeight: Int): Boolean {
        val width = right - left
        val height = bottom - top
        val coversStatusBar = top <= 10
        val coversFullHeight = height >= (screenHeight - 35)
        val coversCenterTop = left <= (screenWidth / 2) && right >= (screenWidth / 2)
        return coversStatusBar && coversFullHeight && coversCenterTop
    }

    fun isTrueFullscreen(bounds: Rect, screenWidth: Int, screenHeight: Int): Boolean {
        return isTrueFullscreen(bounds.left, bounds.top, bounds.right, bounds.bottom, screenWidth, screenHeight)
    }

    /**
     * Evaluates visibility policy given current window state and preferences.
     */
    fun computeVisibility(
        isOverlayEnabled: Boolean,
        isHideInFullscreen: Boolean,
        isAppFullscreen: Boolean,
        filterMode: AppFilterMode,
        filterPackages: Set<String>,
        foregroundPackage: String?,
        homePackages: Set<String>,
        selfPackage: String
    ): Pair<Boolean, String?> {
        if (!isOverlayEnabled) {
            return Pair(false, "overlay_disabled")
        }

        if (isHideInFullscreen && isAppFullscreen) {
            return Pair(false, "fullscreen")
        }

        val pkg = foregroundPackage?.trim() ?: ""

        // Launcher, Centerpiece settings, or System UI are always permitted to show overlay
        if (pkg.isEmpty() || homePackages.contains(pkg) || pkg == selfPackage || pkg == "com.android.systemui") {
            return Pair(true, null)
        }

        return when (filterMode) {
            AppFilterMode.DISABLED -> Pair(true, null)
            AppFilterMode.BLACKLIST -> {
                if (filterPackages.contains(pkg)) {
                    Pair(false, "blacklisted: $pkg")
                } else {
                    Pair(true, null)
                }
            }
            AppFilterMode.WHITELIST -> {
                if (filterPackages.contains(pkg)) {
                    Pair(true, null)
                } else {
                    Pair(false, "not_whitelisted: $pkg")
                }
            }
        }
    }

    fun getHomePackages(context: Context): Set<String> {
        cachedHomePackages?.let { return it }
        val set = mutableSetOf(
            "com.google.android.apps.nexuslauncher",
            "com.android.launcher",
            "com.android.launcher3"
        )
        try {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolveInfos = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            for (info in resolveInfos) {
                info.activityInfo?.packageName?.let { set.add(it) }
            }
        } catch (_: Exception) {}
        cachedHomePackages = set
        return set
    }
}
