package com.exaclast.centerpiece

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Manages live background monitors (real-time CPU/RAM usage)
 * across both CenterpieceAccessibilityService (Layer 30) and CenterpieceHostService.
 */
object SystemMonitorCoordinator {

    private const val TAG = "SystemMonitor"
    private var coordinatorScope: CoroutineScope? = null
    private var cpuJob: Job? = null
    private var isStarted = false
    private var currentRegistry: WidgetRegistry? = null

    fun start(context: Context, registry: WidgetRegistry) {
        if (isStarted) return
        isStarted = true
        currentRegistry = registry

        // Clean up legacy clipboard item if present
        registry.unregisterItem("builtin.clipboard")

        val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        coordinatorScope = scope

        // Ensure Centerpiece Logo chip is registered (icon-only status chip)
        registry.registerItem(
            CenterpieceItem(
                id = "builtin.logo",
                packageName = context.packageName,
                title = "",
                subtitle = null,
                iconRes = R.drawable.ic_centerpiece,
                priority = 1000,
                isBuiltIn = true
            )
        )

        // Observe mode changes to update chip immediately
        scope.launch {
            CenterpiecePreferences.sysmonModeFlow.collect {
                try {
                    updateSysmon(context, registry)
                } catch (_: Exception) {}
            }
        }

        // Initial immediate sampling
        updateSysmon(context, registry)

        // Continuous CPU/RAM polling loop every 2 seconds
        cpuJob = scope.launch(Dispatchers.Default) {
            delay(300)
            try {
                updateSysmon(context, registry)
            } catch (e: Exception) {
                Log.w(TAG, "Error updating sysmon initial: ${e.message}")
            }
            while (isActive) {
                delay(2000)
                try {
                    updateSysmon(context, registry)
                } catch (e: Exception) {
                    Log.w(TAG, "Error updating sysmon: ${e.message}")
                }
            }
        }
    }

    fun updateSysmon(context: Context, registry: WidgetRegistry? = null) {
        val targetRegistry = registry ?: currentRegistry ?: return
        val cpuPercent = CpuMemMonitor.sampleCpuUsage()
        val memInfo = CpuMemMonitor.readCurrentMemoryInfo(context)
        val mode = CenterpiecePreferences.getSysmonMode(context)

        val title = when (mode) {
            SysmonMetricMode.BOTH -> "CPU $cpuPercent% · RAM ${memInfo.usedPercent}%"
            SysmonMetricMode.CPU_ONLY -> "CPU $cpuPercent%"
            SysmonMetricMode.RAM_ONLY -> "RAM ${memInfo.usedPercent}%"
        }

        val item = CenterpieceItem(
            id = "builtin.sysmon",
            packageName = context.packageName,
            title = title,
            subtitle = null,
            iconRes = R.drawable.ic_cpu,
            priority = 900,
            isBuiltIn = true
        )
        targetRegistry.registerItem(item)
    }

    fun stop(context: Context? = null) {
        isStarted = false
        cpuJob?.cancel()
        cpuJob = null
        coordinatorScope?.cancel()
        coordinatorScope = null
    }
}
