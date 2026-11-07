package com.exaclast.centerpiece

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Alignment options for the Centerpiece bar in the Googlebook OS status bar.
 */
enum class BarAlignment {
    CENTER,
    LEFT,
    RIGHT;

    companion object {
        fun fromString(value: String?): BarAlignment {
            return when (value?.uppercase()?.trim()) {
                "LEFT" -> LEFT
                "RIGHT" -> RIGHT
                else -> CENTER
            }
        }
    }
}

/**
 * Configuration model for status bar layout, position fine-tuning offsets, and height.
 * @param alignment Horizontal anchoring mode (CENTER, LEFT, RIGHT)
 * @param offsetX Horizontal fine-tune offset in DP (+ = right, - = left)
 * @param offsetY Vertical fine-tune offset in DP (+ = down, - = up)
 * @param heightDp Custom bar height in DP (0 = Auto / default system status bar height)
 */
data class LayoutConfig(
    val alignment: BarAlignment = BarAlignment.CENTER,
    val offsetX: Int = 0,
    val offsetY: Int = 0,
    val heightDp: Int = 0
)

/**
 * Metric display options for the system monitor chip.
 */
enum class SysmonMetricMode {
    BOTH,
    CPU_ONLY,
    RAM_ONLY;

    companion object {
        fun fromString(value: String?): SysmonMetricMode {
            return when (value?.uppercase()?.trim()) {
                "CPU", "CPU_ONLY" -> CPU_ONLY
                "RAM", "RAM_ONLY" -> RAM_ONLY
                else -> BOTH
            }
        }
    }
}

/**
 * Application filtering mode for Centerpiece overlay visibility.
 */
enum class AppFilterMode {
    DISABLED,
    BLACKLIST,
    WHITELIST;

    companion object {
        fun fromString(value: String?): AppFilterMode {
            return when (value?.uppercase()?.trim()) {
                "BLACKLIST", "BLACK_LIST" -> BLACKLIST
                "WHITELIST", "WHITE_LIST" -> WHITELIST
                else -> DISABLED
            }
        }
    }
}

/**
 * Manages user preferences for Centerpiece, including master overlay toggle state
 * and custom status bar layout/positioning parameters.
 */
object CenterpiecePreferences {

    private const val PREFS_NAME = "centerpiece_prefs"
    private const val KEY_OVERLAY_ENABLED = "overlay_enabled"

    private const val KEY_LAYOUT_ALIGNMENT = "layout_alignment"
    private const val KEY_LAYOUT_OFFSET_X = "layout_offset_x"
    private const val KEY_LAYOUT_OFFSET_Y = "layout_offset_y"
    private const val KEY_LAYOUT_HEIGHT_DP = "layout_height_dp"
    private const val KEY_SYSMON_MODE = "sysmon_mode"
    private const val KEY_HIDE_IN_FULLSCREEN = "hide_in_fullscreen"
    private const val KEY_APP_FILTER_MODE = "app_filter_mode"
    private const val KEY_APP_FILTER_PACKAGES = "app_filter_packages"

    private val _enabledFlow = MutableStateFlow(true)
    val enabledFlow: StateFlow<Boolean> = _enabledFlow.asStateFlow()

    private val _layoutConfigFlow = MutableStateFlow(LayoutConfig())
    val layoutConfigFlow: StateFlow<LayoutConfig> = _layoutConfigFlow.asStateFlow()

    private val _sysmonModeFlow = MutableStateFlow(SysmonMetricMode.BOTH)
    val sysmonModeFlow: StateFlow<SysmonMetricMode> = _sysmonModeFlow.asStateFlow()

    private val _hideInFullscreenFlow = MutableStateFlow(true)
    val hideInFullscreenFlow: StateFlow<Boolean> = _hideInFullscreenFlow.asStateFlow()

    private val _appFilterModeFlow = MutableStateFlow(AppFilterMode.DISABLED)
    val appFilterModeFlow: StateFlow<AppFilterMode> = _appFilterModeFlow.asStateFlow()

    private val _appFilterPackagesFlow = MutableStateFlow<Set<String>>(emptySet())
    val appFilterPackagesFlow: StateFlow<Set<String>> = _appFilterPackagesFlow.asStateFlow()

    private var initialized = false

    fun init(context: Context) {
        if (!initialized) {
            val prefs = getPrefs(context)
            if (prefs != null) {
                _enabledFlow.value = prefs.getBoolean(KEY_OVERLAY_ENABLED, true)

                val alignStr = prefs.getString(KEY_LAYOUT_ALIGNMENT, "CENTER")
                val offsetX = prefs.getInt(KEY_LAYOUT_OFFSET_X, 0)
                val offsetY = prefs.getInt(KEY_LAYOUT_OFFSET_Y, 0)
                val heightDp = prefs.getInt(KEY_LAYOUT_HEIGHT_DP, 0)
                val sysmonStr = prefs.getString(KEY_SYSMON_MODE, "BOTH")

                _layoutConfigFlow.value = LayoutConfig(
                    alignment = BarAlignment.fromString(alignStr),
                    offsetX = offsetX,
                    offsetY = offsetY,
                    heightDp = heightDp
                )
                _sysmonModeFlow.value = SysmonMetricMode.fromString(sysmonStr)

                val hideFullscreen = prefs.getBoolean(KEY_HIDE_IN_FULLSCREEN, true)
                val filterModeStr = prefs.getString(KEY_APP_FILTER_MODE, "DISABLED")
                val filterPackages = prefs.getStringSet(KEY_APP_FILTER_PACKAGES, emptySet()) ?: emptySet()

                _hideInFullscreenFlow.value = hideFullscreen
                _appFilterModeFlow.value = AppFilterMode.fromString(filterModeStr)
                _appFilterPackagesFlow.value = filterPackages.toSet()

                initialized = true
            }
        }
    }

    fun isOverlayEnabled(context: Context): Boolean {
        try {
            init(context)
        } catch (_: Exception) {}
        return _enabledFlow.value
    }

    fun setOverlayEnabled(context: Context, enabled: Boolean) {
        try {
            init(context)
            getPrefs(context)?.edit()?.putBoolean(KEY_OVERLAY_ENABLED, enabled)?.apply()
        } catch (_: Exception) {}
        _enabledFlow.value = enabled
    }

    fun getLayoutConfig(context: Context): LayoutConfig {
        try {
            init(context)
        } catch (_: Exception) {}
        return _layoutConfigFlow.value
    }

    fun setLayoutConfig(context: Context, config: LayoutConfig) {
        try {
            init(context)
            getPrefs(context)?.edit()?.apply {
                putString(KEY_LAYOUT_ALIGNMENT, config.alignment.name)
                putInt(KEY_LAYOUT_OFFSET_X, config.offsetX)
                putInt(KEY_LAYOUT_OFFSET_Y, config.offsetY)
                putInt(KEY_LAYOUT_HEIGHT_DP, config.heightDp)
                apply()
            }
        } catch (_: Exception) {}
        _layoutConfigFlow.value = config
    }

    fun getSysmonMode(context: Context): SysmonMetricMode {
        try {
            init(context)
        } catch (_: Exception) {}
        return _sysmonModeFlow.value
    }

    fun setSysmonMode(context: Context, mode: SysmonMetricMode) {
        try {
            init(context)
            getPrefs(context)?.edit()?.putString(KEY_SYSMON_MODE, mode.name)?.apply()
        } catch (_: Exception) {}
        _sysmonModeFlow.value = mode
    }

    fun resetLayoutConfig(context: Context) {
        setLayoutConfig(context, LayoutConfig())
    }

    fun isHideInFullscreen(context: Context): Boolean {
        try { init(context) } catch (_: Exception) {}
        return _hideInFullscreenFlow.value
    }

    fun setHideInFullscreen(context: Context, hide: Boolean) {
        try {
            init(context)
            getPrefs(context)?.edit()?.putBoolean(KEY_HIDE_IN_FULLSCREEN, hide)?.apply()
        } catch (_: Exception) {}
        _hideInFullscreenFlow.value = hide
    }

    fun getAppFilterMode(context: Context): AppFilterMode {
        try { init(context) } catch (_: Exception) {}
        return _appFilterModeFlow.value
    }

    fun setAppFilterMode(context: Context, mode: AppFilterMode) {
        try {
            init(context)
            getPrefs(context)?.edit()?.putString(KEY_APP_FILTER_MODE, mode.name)?.apply()
        } catch (_: Exception) {}
        _appFilterModeFlow.value = mode
    }

    fun getAppFilterPackages(context: Context): Set<String> {
        try { init(context) } catch (_: Exception) {}
        return _appFilterPackagesFlow.value
    }

    fun setAppFilterPackages(context: Context, packages: Set<String>) {
        try {
            init(context)
            getPrefs(context)?.edit()?.putStringSet(KEY_APP_FILTER_PACKAGES, HashSet(packages))?.apply()
        } catch (_: Exception) {}
        _appFilterPackagesFlow.value = packages.toSet()
    }

    fun addAppFilterPackage(context: Context, pkg: String) {
        val cleanPkg = pkg.trim()
        if (cleanPkg.isEmpty()) return
        val current = getAppFilterPackages(context).toMutableSet()
        current.add(cleanPkg)
        setAppFilterPackages(context, current)
    }

    fun removeAppFilterPackage(context: Context, pkg: String) {
        val cleanPkg = pkg.trim()
        val current = getAppFilterPackages(context).toMutableSet()
        current.remove(cleanPkg)
        setAppFilterPackages(context, current)
    }

    private fun getPrefs(context: Context): SharedPreferences? {
        return try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        } catch (_: Exception) {
            null
        }
    }
}
