package com.exaclast.centerpiece.ui.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exaclast.centerpiece.AppFilterMode
import com.exaclast.centerpiece.BarAlignment
import com.exaclast.centerpiece.CenterpieceAction
import com.exaclast.centerpiece.CenterpieceItem
import com.exaclast.centerpiece.CenterpiecePreferences
import com.exaclast.centerpiece.LayoutConfig
import com.exaclast.centerpiece.SysmonMetricMode
import com.exaclast.centerpiece.data.CenterpieceDashboardState
import com.exaclast.centerpiece.data.DataRepository
import com.exaclast.centerpiece.service.CenterpieceHostService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface MainScreenUiState {
    object Loading : MainScreenUiState
    data class Error(val throwable: Throwable) : MainScreenUiState
    data class Success(val state: CenterpieceDashboardState) : MainScreenUiState
}

class MainScreenViewModel(private val dataRepository: DataRepository) : ViewModel() {

    val uiState: StateFlow<MainScreenUiState> =
        dataRepository.dashboardState
            .map<CenterpieceDashboardState, MainScreenUiState> { MainScreenUiState.Success(it) }
            .catch { emit(MainScreenUiState.Error(it)) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = MainScreenUiState.Loading
            )

    fun toggleService(context: Context) {
        val current = CenterpiecePreferences.isOverlayEnabled(context)
        val next = !current
        CenterpiecePreferences.setOverlayEnabled(context, next)
        if (next) {
            CenterpieceHostService.start(context)
        } else {
            CenterpieceHostService.stop(context)
        }
    }

    fun removeWidget(widgetId: String) {
        CenterpieceHostService.globalRegistry.unregisterItem(widgetId)
    }

    fun addSamplePluginWidget() {
        val sampleItem = CenterpieceItem(
            id = "plugin.sample.terminal",
            packageName = "com.exaclast.terminal",
            title = "VM Terminal",
            subtitle = "Active",
            priority = 500,
            actions = listOf(
                CenterpieceAction(id = "open_term", label = "Open Terminal Session"),
                CenterpieceAction(id = "clear_cache", label = "Clear Project Cache"),
                CenterpieceAction(id = "restart_ssh", label = "Restart SSH Bridge")
            ),
            isBuiltIn = false
        )
        CenterpieceHostService.globalRegistry.registerItem(sampleItem)
    }

    fun setLayoutAlignment(alignment: BarAlignment) {
        val current = (uiState.value as? MainScreenUiState.Success)?.state?.layoutConfig ?: LayoutConfig()
        dataRepository.updateLayoutConfig(current.copy(alignment = alignment))
    }

    fun setOffsetX(offsetX: Int) {
        val current = (uiState.value as? MainScreenUiState.Success)?.state?.layoutConfig ?: LayoutConfig()
        dataRepository.updateLayoutConfig(current.copy(offsetX = offsetX))
    }

    fun setOffsetY(offsetY: Int) {
        val current = (uiState.value as? MainScreenUiState.Success)?.state?.layoutConfig ?: LayoutConfig()
        dataRepository.updateLayoutConfig(current.copy(offsetY = offsetY))
    }

    fun setHeightDp(heightDp: Int) {
        val current = (uiState.value as? MainScreenUiState.Success)?.state?.layoutConfig ?: LayoutConfig()
        dataRepository.updateLayoutConfig(current.copy(heightDp = heightDp))
    }

    fun resetLayout() {
        dataRepository.resetLayoutConfig()
    }

    fun setSysmonMode(mode: SysmonMetricMode) {
        dataRepository.setSysmonMode(mode)
    }

    fun setHideInFullscreen(hide: Boolean) {
        dataRepository.setHideInFullscreen(hide)
    }

    fun setAppFilterMode(mode: AppFilterMode) {
        dataRepository.setAppFilterMode(mode)
    }

    fun addAppFilterPackage(pkg: String) {
        dataRepository.addAppFilterPackage(pkg)
    }

    fun removeAppFilterPackage(pkg: String) {
        dataRepository.removeAppFilterPackage(pkg)
    }
}
