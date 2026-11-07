package com.exaclast.centerpiece.ui.main

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.exaclast.centerpiece.AppFilterMode
import com.exaclast.centerpiece.BarAlignment
import com.exaclast.centerpiece.CenterpieceItem
import com.exaclast.centerpiece.LayoutConfig
import com.exaclast.centerpiece.R
import com.exaclast.centerpiece.SysmonMetricMode
import com.exaclast.centerpiece.data.CenterpieceDashboardState
import com.exaclast.centerpiece.data.DefaultDataRepository

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class MainScreenViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return MainScreenViewModel(DefaultDataRepository(context)) as T
    }
}

@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = viewModel(
        factory = MainScreenViewModelFactory(LocalContext.current.applicationContext)
    )
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    when (val state = uiState) {
        MainScreenUiState.Loading -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        is MainScreenUiState.Success -> {
            MainScreenContent(
                state = state.state,
                onToggleService = { viewModel.toggleService(context) },
                onAddSampleWidget = { viewModel.addSamplePluginWidget() },
                onRemoveWidget = { id -> viewModel.removeWidget(id) },
                onSetAlignment = { viewModel.setLayoutAlignment(it) },
                onSetOffsetX = { viewModel.setOffsetX(it) },
                onSetOffsetY = { viewModel.setOffsetY(it) },
                onSetHeightDp = { viewModel.setHeightDp(it) },
                onResetLayout = { viewModel.resetLayout() },
                onSetSysmonMode = { viewModel.setSysmonMode(it) },
                onSetHideInFullscreen = { viewModel.setHideInFullscreen(it) },
                onSetAppFilterMode = { viewModel.setAppFilterMode(it) },
                onAddAppFilterPackage = { viewModel.addAppFilterPackage(it) },
                onRemoveAppFilterPackage = { viewModel.removeAppFilterPackage(it) },
                modifier = modifier
            )
        }
        is MainScreenUiState.Error -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Error: ${state.throwable.message}",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MainScreenContent(
    state: CenterpieceDashboardState,
    onToggleService: () -> Unit,
    onAddSampleWidget: () -> Unit,
    onRemoveWidget: (String) -> Unit,
    onSetAlignment: (BarAlignment) -> Unit,
    onSetOffsetX: (Int) -> Unit,
    onSetOffsetY: (Int) -> Unit,
    onSetHeightDp: (Int) -> Unit,
    onResetLayout: () -> Unit,
    onSetSysmonMode: (SysmonMetricMode) -> Unit,
    onSetHideInFullscreen: (Boolean) -> Unit,
    onSetAppFilterMode: (AppFilterMode) -> Unit,
    onAddAppFilterPackage: (String) -> Unit,
    onRemoveAppFilterPackage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_centerpiece),
                            contentDescription = "Centerpiece",
                            tint = Color.Unspecified,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Centerpiece",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Googlebook OS Desktop Utility",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // Overlay Permission Alert Banner
            if (!state.isOverlayPermissionGranted) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Overlay Permission Required",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "Centerpiece needs 'Display over other apps' to float in the central status bar.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                ).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Grant")
                        }
                    }
                }
            }

            // 1. Service Status Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (state.isServiceRunning) Color(0xFF4CAF50) else Color(0xFF757575))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (state.isServiceRunning) "Centerpiece Active" else "Centerpiece Inactive",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (state.isServiceRunning)
                                "Top bar overlay running. Anchored to ${state.statusBarHeight}px status bar."
                            else
                                "Turn on to float modular status chips in the desktop menu bar.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = state.isServiceRunning,
                        onCheckedChange = { onToggleService() },
                        enabled = state.isOverlayPermissionGranted || state.isEnhancedDesktopModeActive
                    )
                }
            }

            // 1b. Enhanced Desktop Mode (Layer 30 - Stays Above Maximized Windows)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (state.isEnhancedDesktopModeActive)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Persistent Maximized Window Docking",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            if (state.isEnhancedDesktopModeActive) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "ACTIVE",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    color = Color(0xFF2E7D32)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (state.isEnhancedDesktopModeActive)
                                "Promoted to Z-Layer 30. Centerpiece stays visible on top of the black status bar even when apps are maximized!"
                            else
                                "When apps maximize, Googlebook OS turns the status bar black, occluding normal overlays. Enable Enhanced Mode to float above the status bar.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    if (!state.isEnhancedDesktopModeActive) {
                        Button(
                            onClick = {
                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            }
                        ) {
                            Text("Enable", fontSize = 12.sp)
                        }
                    }
                }
            }

            // 2. Interactive Status Bar Preview Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Live Status Bar Safe Alignment Preview",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Googlebook OS status bar (${state.screenWidth} × ${state.statusBarHeight}px). Centerpiece occupies central region between system clusters.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Simulated System Top Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF16181D))
                            .border(1.dp, Color(0xFF2C3038), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Left System Cluster (Simulated Clock / App Launcher)
                        Row(
                            modifier = Modifier.align(Alignment.CenterStart),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "12:00",
                                color = Color(0xFF8B929A),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Mon Nov 4",
                                color = Color(0xFF6B727A),
                                fontSize = 10.sp
                            )
                        }

                        // Centerpiece Active Chips Preview with live alignment and offsets
                        val previewAlign = when (state.layoutConfig.alignment) {
                            BarAlignment.CENTER -> Alignment.Center
                            BarAlignment.LEFT -> Alignment.CenterStart
                            BarAlignment.RIGHT -> Alignment.CenterEnd
                        }
                        val previewPadStart = if (state.layoutConfig.alignment == BarAlignment.LEFT) 90.dp else 0.dp
                        val previewPadEnd = if (state.layoutConfig.alignment == BarAlignment.RIGHT) 90.dp else 0.dp

                        Row(
                            modifier = Modifier
                                .align(previewAlign)
                                .padding(start = previewPadStart, end = previewPadEnd)
                                .offset(
                                    x = (state.layoutConfig.offsetX * 0.4f).dp,
                                    y = (state.layoutConfig.offsetY * 0.4f).dp
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (state.activeWidgets.isEmpty()) {
                                Text(
                                    text = "Centerpiece Active Region",
                                    color = Color(0xFF4FC3F7),
                                    fontSize = 11.sp
                                )
                            } else {
                                state.activeWidgets.take(4).forEach { item ->
                                    SimulatedChip(item)
                                }
                            }
                        }

                        // Right System Cluster (Simulated Quick Settings / Battery / Wi-Fi)
                        Row(
                            modifier = Modifier.align(Alignment.CenterEnd),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "📶  🔋 94%",
                                color = Color(0xFF8B929A),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // 2b. Layout & Positioning Customization Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Layout & Positioning Customization",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Choose horizontal anchor alignment, fine-tune position offsets, and adjust bar height.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        OutlinedButton(
                            onClick = onResetLayout,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Reset Defaults", fontSize = 12.sp)
                        }
                    }

                    // A. Alignment Mode
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Horizontal Docking Alignment",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = state.layoutConfig.alignment == BarAlignment.LEFT,
                                onClick = { onSetAlignment(BarAlignment.LEFT) },
                                label = { Text("Left Bound") },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = state.layoutConfig.alignment == BarAlignment.CENTER,
                                onClick = { onSetAlignment(BarAlignment.CENTER) },
                                label = { Text("Centered (Default)") },
                                modifier = Modifier.weight(1.2f)
                            )
                            FilterChip(
                                selected = state.layoutConfig.alignment == BarAlignment.RIGHT,
                                onClick = { onSetAlignment(BarAlignment.RIGHT) },
                                label = { Text("Right Bound") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // B. Horizontal Offset (X)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Horizontal Location Offset (X)",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${if (state.layoutConfig.offsetX > 0) "+" else ""}${state.layoutConfig.offsetX} dp",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { onSetOffsetX((state.layoutConfig.offsetX - 2).coerceAtLeast(-100)) },
                                modifier = Modifier.size(36.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("-", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                            Slider(
                                value = state.layoutConfig.offsetX.toFloat(),
                                onValueChange = { onSetOffsetX(it.toInt()) },
                                valueRange = -100f..100f,
                                modifier = Modifier.weight(1f)
                            )
                            FilledTonalButton(
                                onClick = { onSetOffsetX((state.layoutConfig.offsetX + 2).coerceAtMost(100)) },
                                modifier = Modifier.size(36.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("+", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }
                        Text(
                            text = "Negative moves leftward, positive moves rightward.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // C. Vertical Offset (Y)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Vertical Location Offset (Y)",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${if (state.layoutConfig.offsetY > 0) "+" else ""}${state.layoutConfig.offsetY} dp",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { onSetOffsetY((state.layoutConfig.offsetY - 1).coerceAtLeast(-20)) },
                                modifier = Modifier.size(36.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("-", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                            Slider(
                                value = state.layoutConfig.offsetY.toFloat(),
                                onValueChange = { onSetOffsetY(it.toInt()) },
                                valueRange = -20f..30f,
                                modifier = Modifier.weight(1f)
                            )
                            FilledTonalButton(
                                onClick = { onSetOffsetY((state.layoutConfig.offsetY + 1).coerceAtMost(30)) },
                                modifier = Modifier.size(36.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("+", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }
                        Text(
                            text = "Negative shifts upward, positive shifts downward within top status bar boundary.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // D. Top Bar Height
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Bar Height",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (state.layoutConfig.heightDp == 0) "Auto (~36dp)" else "${state.layoutConfig.heightDp} dp",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = state.layoutConfig.heightDp == 0,
                                onClick = { onSetHeightDp(0) },
                                label = { Text("Auto") }
                            )
                            Slider(
                                value = if (state.layoutConfig.heightDp == 0) 36f else state.layoutConfig.heightDp.toFloat(),
                                onValueChange = { onSetHeightDp(it.toInt()) },
                                valueRange = 24f..52f,
                                modifier = Modifier.weight(1f)
                            )
                            FilledTonalButton(
                                onClick = {
                                    val current = if (state.layoutConfig.heightDp == 0) 36 else state.layoutConfig.heightDp
                                    onSetHeightDp((current + 2).coerceAtMost(52))
                                },
                                modifier = Modifier.size(36.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("+", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }
                        Text(
                            text = "Auto matches standard system status bar height (59px / ~36dp on Googlebook OS).",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 2c. System Monitor Status Bar Metrics Options Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column {
                        Text(
                            text = "System Monitor Status Bar Metrics",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Choose which hardware resource metrics appear in the top status bar chip.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = state.sysmonMode == SysmonMetricMode.BOTH,
                            onClick = { onSetSysmonMode(SysmonMetricMode.BOTH) },
                            label = { Text("Both (CPU & RAM)") },
                            modifier = Modifier.weight(1.2f)
                        )
                        FilterChip(
                            selected = state.sysmonMode == SysmonMetricMode.CPU_ONLY,
                            onClick = { onSetSysmonMode(SysmonMetricMode.CPU_ONLY) },
                            label = { Text("CPU Only") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = state.sysmonMode == SysmonMetricMode.RAM_ONLY,
                            onClick = { onSetSysmonMode(SysmonMetricMode.RAM_ONLY) },
                            label = { Text("RAM Only") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 2d. App Visibility & Fullscreen Rules Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Column {
                        Text(
                            text = "App Visibility & Fullscreen Rules",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Control when Centerpiece hides in true fullscreen apps and configure per-application visibility rules.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // A. Hide in True Fullscreen Mode Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Hide in True Fullscreen Mode",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Automatically hides Centerpiece when an app enters immersive fullscreen (e.g. video playback, games). Stays visible on standard maximized windows.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = state.isHideInFullscreen,
                            onCheckedChange = { onSetHideInFullscreen(it) }
                        )
                    }

                    // Live Status Badge
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (state.isCenterpieceVisible) Color(0xFF4CAF50) else Color(0xFFFF9800))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (state.isCenterpieceVisible) "Centerpiece Active & Visible" else "Centerpiece Hidden by Rule",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        val appLabel = when {
                            state.isAppFullscreen -> "Mode: Fullscreen"
                            !state.currentForegroundPackage.isNullOrEmpty() -> "App: ${state.currentForegroundPackage}"
                            else -> "Desktop / Idle"
                        }
                        Text(
                            text = appLabel,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // B. App Filter Mode (Disabled / Blacklist / Whitelist)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "App Filter Mode",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = state.appFilterMode == AppFilterMode.DISABLED,
                                onClick = { onSetAppFilterMode(AppFilterMode.DISABLED) },
                                label = { Text("Disabled (All Apps)") },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = state.appFilterMode == AppFilterMode.BLACKLIST,
                                onClick = { onSetAppFilterMode(AppFilterMode.BLACKLIST) },
                                label = { Text("Blacklist (Hide for list)") },
                                modifier = Modifier.weight(1.1f)
                            )
                            FilterChip(
                                selected = state.appFilterMode == AppFilterMode.WHITELIST,
                                onClick = { onSetAppFilterMode(AppFilterMode.WHITELIST) },
                                label = { Text("Whitelist (Show only for list)") },
                                modifier = Modifier.weight(1.2f)
                            )
                        }
                    }

                    // C. Package List & Quick Add (When Blacklist or Whitelist is active)
                    if (state.appFilterMode != AppFilterMode.DISABLED) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = if (state.appFilterMode == AppFilterMode.BLACKLIST)
                                    "Blacklisted Applications (Centerpiece will be hidden when these apps are active):"
                                else
                                    "Whitelisted Applications (Centerpiece will only show on Desktop or these apps):",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Configured Package Chips
                            if (state.appFilterPackages.isEmpty()) {
                                Text(
                                    text = "No packages configured yet. Add apps below.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    state.appFilterPackages.forEach { pkg ->
                                        FilledTonalButton(
                                            onClick = { onRemoveAppFilterPackage(pkg) },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Text("$pkg  ×", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }

                            // Manual input
                            var newPkgInput by remember { mutableStateOf("") }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = newPkgInput,
                                    onValueChange = { newPkgInput = it },
                                    placeholder = { Text("Enter package (e.g. com.twodboy.wog2)", fontSize = 12.sp) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                Button(
                                    onClick = {
                                        if (newPkgInput.isNotBlank()) {
                                            onAddAppFilterPackage(newPkgInput.trim())
                                            newPkgInput = ""
                                        }
                                    },
                                    enabled = newPkgInput.isNotBlank()
                                ) {
                                    Text("Add")
                                }
                            }

                            // Quick Add suggestions
                            Text(
                                text = "Quick add installed apps:",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    "Chrome" to "com.android.chrome",
                                    "YouTube" to "com.google.android.youtube",
                                    "Terminal" to "com.android.virtualization.terminal",
                                    "Photos" to "com.google.android.apps.photos",
                                    "Plex" to "com.plexapp.android",
                                    "World of Goo 2" to "com.twodboy.wog2"
                                ).forEach { (label, pkg) ->
                                    if (!state.appFilterPackages.contains(pkg)) {
                                        OutlinedButton(
                                            onClick = { onAddAppFilterPackage(pkg) },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text("+ $label", fontSize = 10.5.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Modular Widgets Management
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Active Status Bar Widgets (${state.activeWidgets.size})",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                        FilledTonalButton(onClick = { onAddSampleWidget() }) {
                            Text("+ Add Demo Widget", fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (state.activeWidgets.isEmpty()) {
                        Text(
                            text = "No widgets currently loaded. Turn on Centerpiece to start default widgets.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        state.activeWidgets.forEach { item ->
                            WidgetItemRow(item = item, onRemove = { onRemoveWidget(item.id) })
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                }
            }

            // 4. Developer Extension & IPC API Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Plugin & Extension Protocol (AIDL / Broadcast)",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Third-party applications can register custom status chips and expandable popover sheets.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "CLI Quick Registration via ADB Broadcast:",
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1E1E1E))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "am broadcast -a com.exaclast.centerpiece.REGISTER_WIDGET \\\n" +
                                    "  --es id \"my_widget\" \\\n" +
                                    "  --es title \"Build\" \\\n" +
                                    "  --es subtitle \"Passing\" \\\n" +
                                    "  --ei priority 50",
                            color = Color(0xFFA5D6A7),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // 5. Advanced System & Daemon Diagnostics Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_centerpiece),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Advanced System & Daemon Diagnostics",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    Text(
                        text = "Technical runtime configuration for Centerpiece on Googlebook OS.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    DiagnosticsItemRow(label = "Platform", value = "Googlebook OS Desktop (Android 17)")
                    DiagnosticsItemRow(
                        label = "Overlay Window Layer",
                        value = if (state.isEnhancedDesktopModeActive) "Layer 30 (TYPE_ACCESSIBILITY_OVERLAY 2032)" else "Layer 2038 (TYPE_APPLICATION_OVERLAY)"
                    )
                    DiagnosticsItemRow(label = "Status Bar Height", value = "${state.statusBarHeight} px (Anchored)")
                    DiagnosticsItemRow(label = "Display Resolution", value = "${state.screenWidth} × ${state.screenHeight} px")
                    DiagnosticsItemRow(label = "Zero-ADB REST Daemon", value = "http://127.0.0.1:7420 (Active)")
                    DiagnosticsItemRow(label = "IPC Protocols", value = "AIDL (ICenterpieceService) + Dynamic Broadcast")
                }
            }
        }
    }
}

@Composable
fun DiagnosticsItemRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun SimulatedChip(item: CenterpieceItem) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x33FFFFFF))
            .border(0.5.dp, Color(0x44FFFFFF), RoundedCornerShape(12.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (item.title.isNotBlank()) {
                Text(
                    text = item.title,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            } else {
                Icon(
                    painter = painterResource(id = if (item.iconRes != 0) item.iconRes else R.drawable.ic_centerpiece),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }
            if (!item.subtitle.isNullOrEmpty()) {
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = item.subtitle,
                    color = Color(0xFF90CAF9),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun WidgetItemRow(item: CenterpieceItem, onRemove: () -> Unit) {
    val displayTitle = if (item.title.isNotBlank()) item.title else if (item.id == "builtin.logo") "Centerpiece (Icon)" else "Keep Screen Awake (Off)"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = displayTitle,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (item.isBuiltIn) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Built-in",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(
                text = "ID: ${item.id}  •  Priority: ${item.priority}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (!item.isBuiltIn) {
            OutlinedButton(onClick = onRemove) {
                Text("Remove", fontSize = 11.sp)
            }
        }
    }
}
