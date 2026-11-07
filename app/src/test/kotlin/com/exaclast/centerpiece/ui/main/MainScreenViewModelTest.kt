package com.exaclast.centerpiece.ui.main

import com.exaclast.centerpiece.AppFilterMode
import com.exaclast.centerpiece.BarAlignment
import com.exaclast.centerpiece.CenterpieceItem
import com.exaclast.centerpiece.LayoutConfig
import com.exaclast.centerpiece.SysmonMetricMode
import com.exaclast.centerpiece.data.CenterpieceDashboardState
import com.exaclast.centerpiece.data.DataRepository
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MainScreenViewModelTest {

    @Test
    fun uiState_initiallyLoadingOrSuccess() = runTest {
        val testState = CenterpieceDashboardState(
            isOverlayPermissionGranted = true,
            isServiceRunning = true,
            statusBarHeight = 59,
            activeWidgets = listOf(
                CenterpieceItem(id = "item1", packageName = "p", title = "Test")
            )
        )
        val viewModel = MainScreenViewModel(FakeDashboardRepository(testState))
        val firstState = viewModel.uiState.first { it is MainScreenUiState.Success }
        assertTrue(firstState is MainScreenUiState.Success)
        val success = firstState as MainScreenUiState.Success
        assertEquals(59, success.state.statusBarHeight)
        assertTrue(success.state.isOverlayPermissionGranted)
        assertEquals(1, success.state.activeWidgets.size)
    }

    @Test
    fun testLayoutMutations() = runTest {
        val testState = CenterpieceDashboardState(
            isOverlayPermissionGranted = true,
            isServiceRunning = true,
            layoutConfig = LayoutConfig(alignment = BarAlignment.CENTER, offsetX = 0, offsetY = 0, heightDp = 0)
        )
        val repo = FakeDashboardRepository(testState)
        val viewModel = MainScreenViewModel(repo)

        viewModel.setLayoutAlignment(BarAlignment.LEFT)
        var state = (viewModel.uiState.first { (it as? MainScreenUiState.Success)?.state?.layoutConfig?.alignment == BarAlignment.LEFT } as MainScreenUiState.Success).state
        assertEquals(BarAlignment.LEFT, state.layoutConfig.alignment)

        viewModel.setOffsetX(15)
        state = (viewModel.uiState.first { (it as? MainScreenUiState.Success)?.state?.layoutConfig?.offsetX == 15 } as MainScreenUiState.Success).state
        assertEquals(15, state.layoutConfig.offsetX)

        viewModel.setOffsetY(4)
        state = (viewModel.uiState.first { (it as? MainScreenUiState.Success)?.state?.layoutConfig?.offsetY == 4 } as MainScreenUiState.Success).state
        assertEquals(4, state.layoutConfig.offsetY)

        viewModel.setHeightDp(42)
        state = (viewModel.uiState.first { (it as? MainScreenUiState.Success)?.state?.layoutConfig?.heightDp == 42 } as MainScreenUiState.Success).state
        assertEquals(42, state.layoutConfig.heightDp)

        viewModel.resetLayout()
        state = (viewModel.uiState.first { (it as? MainScreenUiState.Success)?.state?.layoutConfig?.alignment == BarAlignment.CENTER } as MainScreenUiState.Success).state
        assertEquals(BarAlignment.CENTER, state.layoutConfig.alignment)
        assertEquals(0, state.layoutConfig.offsetX)
        assertEquals(0, state.layoutConfig.offsetY)
        assertEquals(0, state.layoutConfig.heightDp)
    }

    @Test
    fun setSysmonMode_updatesState() = runTest {
        val testState = CenterpieceDashboardState(sysmonMode = SysmonMetricMode.BOTH)
        val viewModel = MainScreenViewModel(FakeDashboardRepository(testState))
        viewModel.setSysmonMode(SysmonMetricMode.CPU_ONLY)
        val updated = viewModel.uiState.first { (it as? MainScreenUiState.Success)?.state?.sysmonMode == SysmonMetricMode.CPU_ONLY }
        assertEquals(SysmonMetricMode.CPU_ONLY, (updated as MainScreenUiState.Success).state.sysmonMode)
    }

    @Test
    fun setHideInFullscreen_updatesState() = runTest {
        val testState = CenterpieceDashboardState(isHideInFullscreen = false)
        val viewModel = MainScreenViewModel(FakeDashboardRepository(testState))
        viewModel.setHideInFullscreen(true)
        val updated = viewModel.uiState.first { (it as? MainScreenUiState.Success)?.state?.isHideInFullscreen == true }
        assertTrue((updated as MainScreenUiState.Success).state.isHideInFullscreen)
    }

    @Test
    fun setAppFilterMode_andPackages_updatesState() = runTest {
        val testState = CenterpieceDashboardState(appFilterMode = AppFilterMode.DISABLED)
        val viewModel = MainScreenViewModel(FakeDashboardRepository(testState))
        viewModel.setAppFilterMode(AppFilterMode.BLACKLIST)
        viewModel.addAppFilterPackage("com.example.game")

        val updated = viewModel.uiState.first { (it as? MainScreenUiState.Success)?.state?.appFilterMode == AppFilterMode.BLACKLIST }
        val success = updated as MainScreenUiState.Success
        assertEquals(AppFilterMode.BLACKLIST, success.state.appFilterMode)
        assertTrue(success.state.appFilterPackages.contains("com.example.game"))

        viewModel.removeAppFilterPackage("com.example.game")
        val afterRemove = viewModel.uiState.first { !(it as? MainScreenUiState.Success)?.state?.appFilterPackages?.contains("com.example.game")!! }
        assertTrue((afterRemove as MainScreenUiState.Success).state.appFilterPackages.isEmpty())
    }
}

private class FakeDashboardRepository(
    private var state: CenterpieceDashboardState
) : DataRepository {
    private val flow = MutableStateFlow(state)
    override val dashboardState: Flow<CenterpieceDashboardState> = flow
    override fun checkOverlayPermission(): Boolean = state.isOverlayPermissionGranted

    override fun updateLayoutConfig(config: LayoutConfig) {
        state = state.copy(layoutConfig = config)
        flow.value = state
    }

    override fun resetLayoutConfig() {
        state = state.copy(layoutConfig = LayoutConfig())
        flow.value = state
    }

    override fun setSysmonMode(mode: SysmonMetricMode) {
        state = state.copy(sysmonMode = mode)
        flow.value = state
    }

    override fun setHideInFullscreen(hide: Boolean) {
        state = state.copy(isHideInFullscreen = hide)
        flow.value = state
    }

    override fun setAppFilterMode(mode: AppFilterMode) {
        state = state.copy(appFilterMode = mode)
        flow.value = state
    }

    override fun addAppFilterPackage(pkg: String) {
        val pkgs = state.appFilterPackages.toMutableSet()
        pkgs.add(pkg)
        state = state.copy(appFilterPackages = pkgs)
        flow.value = state
    }

    override fun removeAppFilterPackage(pkg: String) {
        val pkgs = state.appFilterPackages.toMutableSet()
        pkgs.remove(pkg)
        state = state.copy(appFilterPackages = pkgs)
        flow.value = state
    }
}
