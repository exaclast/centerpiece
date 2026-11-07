package com.exaclast.centerpiece.data

import android.content.Context
import android.provider.Settings
import com.exaclast.centerpiece.AppFilterMode
import com.exaclast.centerpiece.AppVisibilityCoordinator
import com.exaclast.centerpiece.CenterpieceItem
import com.exaclast.centerpiece.CenterpiecePreferences
import com.exaclast.centerpiece.LayoutConfig
import com.exaclast.centerpiece.SafeInsetHelper
import com.exaclast.centerpiece.SysmonMetricMode
import com.exaclast.centerpiece.service.CenterpieceAccessibilityService
import com.exaclast.centerpiece.service.CenterpieceHostService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Live configuration and state model for the Centerpiece management dashboard.
 */
data class CenterpieceDashboardState(
    val isOverlayPermissionGranted: Boolean = false,
    val isServiceRunning: Boolean = false,
    val isEnhancedDesktopModeActive: Boolean = false,
    val statusBarHeight: Int = 59,
    val screenWidth: Int = 2880,
    val screenHeight: Int = 1800,
    val activeWidgets: List<CenterpieceItem> = emptyList(),
    val layoutConfig: LayoutConfig = LayoutConfig(),
    val sysmonMode: SysmonMetricMode = SysmonMetricMode.BOTH,
    val isHideInFullscreen: Boolean = true,
    val appFilterMode: AppFilterMode = AppFilterMode.DISABLED,
    val appFilterPackages: Set<String> = emptySet(),
    val currentForegroundPackage: String? = null,
    val isAppFullscreen: Boolean = false,
    val isCenterpieceVisible: Boolean = true
)

interface DataRepository {
    val dashboardState: Flow<CenterpieceDashboardState>
    fun checkOverlayPermission(): Boolean
    fun updateLayoutConfig(config: LayoutConfig)
    fun resetLayoutConfig()
    fun setSysmonMode(mode: SysmonMetricMode)
    fun setHideInFullscreen(hide: Boolean)
    fun setAppFilterMode(mode: AppFilterMode)
    fun addAppFilterPackage(pkg: String)
    fun removeAppFilterPackage(pkg: String)
}

class DefaultDataRepository(private val context: Context) : DataRepository {

    init {
        CenterpiecePreferences.init(context)
    }

    private val baseStateFlow = combine(
        CenterpieceHostService.globalRegistry.itemsFlow,
        CenterpiecePreferences.enabledFlow,
        CenterpiecePreferences.layoutConfigFlow,
        CenterpiecePreferences.sysmonModeFlow
    ) { items, isEnabled, layoutConfig, sysmonMode ->
        Quadruple(items, isEnabled, layoutConfig, sysmonMode)
    }

    private val visibilityConfigFlow = combine(
        CenterpiecePreferences.hideInFullscreenFlow,
        CenterpiecePreferences.appFilterModeFlow,
        CenterpiecePreferences.appFilterPackagesFlow,
        AppVisibilityCoordinator.currentForegroundPackageFlow,
        AppVisibilityCoordinator.isAppFullscreenFlow
    ) { hideFullscreen, filterMode, filterPackages, foregroundPkg, isFullscreen ->
        VisibilityStateBundle(hideFullscreen, filterMode, filterPackages, foregroundPkg, isFullscreen)
    }

    override val dashboardState: Flow<CenterpieceDashboardState> =
        combine(
            baseStateFlow,
            visibilityConfigFlow,
            AppVisibilityCoordinator.isOverlayVisibleFlow
        ) { base, vis, isVisible ->
            CenterpieceDashboardState(
                isOverlayPermissionGranted = checkOverlayPermission(),
                isServiceRunning = base.second,
                isEnhancedDesktopModeActive = CenterpieceAccessibilityService.isAccessibilityActive,
                statusBarHeight = SafeInsetHelper.getStatusBarHeight(context),
                screenWidth = SafeInsetHelper.getScreenWidth(context),
                screenHeight = SafeInsetHelper.getScreenHeight(context),
                activeWidgets = base.first,
                layoutConfig = base.third,
                sysmonMode = base.fourth,
                isHideInFullscreen = vis.hideFullscreen,
                appFilterMode = vis.filterMode,
                appFilterPackages = vis.filterPackages,
                currentForegroundPackage = vis.foregroundPkg,
                isAppFullscreen = vis.isFullscreen,
                isCenterpieceVisible = isVisible
            )
        }

    override fun checkOverlayPermission(): Boolean {
        return Settings.canDrawOverlays(context)
    }

    override fun updateLayoutConfig(config: LayoutConfig) {
        CenterpiecePreferences.setLayoutConfig(context, config)
    }

    override fun resetLayoutConfig() {
        CenterpiecePreferences.resetLayoutConfig(context)
    }

    override fun setSysmonMode(mode: SysmonMetricMode) {
        CenterpiecePreferences.setSysmonMode(context, mode)
    }

    override fun setHideInFullscreen(hide: Boolean) {
        CenterpiecePreferences.setHideInFullscreen(context, hide)
    }

    override fun setAppFilterMode(mode: AppFilterMode) {
        CenterpiecePreferences.setAppFilterMode(context, mode)
    }

    override fun addAppFilterPackage(pkg: String) {
        CenterpiecePreferences.addAppFilterPackage(context, pkg)
    }

    override fun removeAppFilterPackage(pkg: String) {
        CenterpiecePreferences.removeAppFilterPackage(context, pkg)
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
private data class VisibilityStateBundle(
    val hideFullscreen: Boolean,
    val filterMode: AppFilterMode,
    val filterPackages: Set<String>,
    val foregroundPkg: String?,
    val isFullscreen: Boolean
)
