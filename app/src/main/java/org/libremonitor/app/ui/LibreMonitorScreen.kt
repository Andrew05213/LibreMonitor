/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.ui

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Monitor
import androidx.compose.material.icons.rounded.Power
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.ListItem
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.libremonitor.app.MonitorActions
import org.libremonitor.app.MonitorUiState
import org.libremonitor.core.BatterySample
import org.libremonitor.core.BluetoothDeviceInfo
import org.libremonitor.core.BluetoothSample
import org.libremonitor.core.CpuSample
import org.libremonitor.core.DeviceInfo
import org.libremonitor.core.MemorySample
import org.libremonitor.core.Reading
import org.libremonitor.core.ReadingStatus
import org.libremonitor.core.RootAccess
import kotlin.math.roundToInt

private val LightColors = lightColorScheme(
    primary = Color(0xFF386A20),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBCEFA2),
    onPrimaryContainer = Color(0xFF002019),
    secondary = Color(0xFF49675E),
    secondaryContainer = Color(0xFFD8ECE4),
    tertiary = Color(0xFF8A5A00),
    background = Color(0xFFF2F6F4),
    onBackground = Color(0xFF15201C),
    surface = Color(0xFFFCFEFC),
    onSurface = Color(0xFF15201C),
    surfaceVariant = Color(0xFFE3ECE7),
    onSurfaceVariant = Color(0xFF46534C),
    outline = Color(0xFF738078),
    outlineVariant = Color(0xFFC9D6CE),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA3D397),
    onPrimary = Color(0xFF00382C),
    primaryContainer = Color(0xFF274E21),
    onPrimaryContainer = Color(0xFFBCEFA2),
    secondary = Color(0xFFB0CCC1),
    secondaryContainer = Color(0xFF344C2C),
    tertiary = Color(0xFFB7D29E),
    background = Color(0xFF10140F),
    onBackground = Color(0xFFE1EAE4),
    surface = Color(0xFF1A211B),
    onSurface = Color(0xFFE1EAE4),
    surfaceVariant = Color(0xFF25352E),
    onSurfaceVariant = Color(0xFFB7C7BD),
    outline = Color(0xFF87998E),
    outlineVariant = Color(0xFF3A4D43),
)

private val LightAccent = Color(0xFF386A20)
private val DarkAccent = Color(0xFFB7D29E)

private data class LiveState(val label: String, val container: Color, val content: Color)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibreMonitorScreen(state: MonitorUiState, actions: MonitorActions) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var route by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var detail by remember { mutableStateOf<MetricDetail?>(null) }
    val dark = isSystemInDarkTheme()
    val colors = if (dark) DarkColors else LightColors
    val accent = colors.primary
    val page = when {
        route == "battery" -> org.libremonitor.core.SamplingPlan.Page.BATTERY
        route == "performance" -> org.libremonitor.core.SamplingPlan.Page.PERFORMANCE
        route == "hardware" -> org.libremonitor.core.SamplingPlan.Page.HARDWARE
        route == "network" -> org.libremonitor.core.SamplingPlan.Page.NETWORK
        route == "bluetooth" -> org.libremonitor.core.SamplingPlan.Page.BLUETOOTH
        route == "system" -> org.libremonitor.core.SamplingPlan.Page.SYSTEM
        route != null || selectedTab == 2 -> org.libremonitor.core.SamplingPlan.Page.SETTINGS
        selectedTab == 1 -> org.libremonitor.core.SamplingPlan.Page.OVERVIEW
        else -> org.libremonitor.core.SamplingPlan.Page.FUNCTIONS
    }
    LaunchedEffect(page) { actions.onPageChange(page) }
    androidx.activity.compose.BackHandler(enabled = route != null) { route = null }
    val title = when (route) {
        "battery" -> "电池电量"; "performance" -> "性能记录"; "overlay" -> "悬浮监视器"
        "hardware" -> "硬件信息"; "network" -> "网络状态"; "bluetooth" -> "蓝牙设备"
        "system" -> "系统状态"; "diagnostics" -> "采集诊断"; else -> "LibreMonitor"
    }
    val tabs = listOf(Triple("功能", Icons.Rounded.Dashboard, 0), Triple("概览", Icons.Rounded.Monitor, 1), Triple("设置", Icons.Rounded.Settings, 2))
    MaterialTheme(colorScheme = colors) {
        Scaffold(containerColor = colors.background, contentWindowInsets = WindowInsets.safeDrawing,
            topBar = { AppHeader(state, title, route != null) { route = null } },
            bottomBar = {
                if (route == null) NavigationBar(containerColor = colors.surface, tonalElevation = 0.dp) {
                    tabs.forEach { (label, icon, index) ->
                        NavigationBarItem(selected = selectedTab == index,
                            onClick = { selectedTab = index; route = null }, icon = { Icon(icon, label) }, label = { Text(label) })
                    }
                }
            }) { insets ->
            Column(Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(if (selectedTab == 1 && route == null) 12.dp else 4.dp)) {
                state.error?.let { ErrorBanner(it, actions.onDismissError) }
                key(selectedTab, route) {
                    when (route) {
                        "battery" -> BatteryPage(state, actions, accent, { detail = it }) { confirmClear = true }
                        "performance" -> PerformancePage(state, actions)
                        "overlay" -> {
                            ListItem(headlineContent = { Text("悬浮监视器") }, leadingContent = { Icon(Icons.Rounded.Layers, null) },
                                trailingContent = { Switch(state.overlayEnabled, { actions.onOverlayToggle() }) })
                            OverlayCustomization(state.settings.overlay, actions.onOverlayOptionsChange, actions.onOverlayPreview)
                        }
                        "hardware" -> { SectionCard("设备", Icons.Rounded.Smartphone) { DeviceRows(state.device) }; OverviewPage(state, actions, accent, showStorage = true) { detail = it } }
                        "network", "system" -> state.snapshot?.let { snapshot ->
                            if (route == "network") SectionCard("网络", Icons.Rounded.Wifi) {
                                MiniPair(tile("下载", snapshot.network.downloadBytesPerSecond, ::rateQuantity, nonnegativeDouble, snapshot.timestampEpochMillis, Icons.Rounded.Download),
                                    tile("上传", snapshot.network.uploadBytesPerSecond, ::rateQuantity, nonnegativeDouble, snapshot.timestampEpochMillis, Icons.Rounded.Upload)) { detail = it }
                            }
                            val environment = if (route == "network") snapshot.environment.copy(system = emptyMap()) else snapshot.environment.copy(connectivity = emptyMap())
                            EnvironmentSections(environment, snapshot.timestampEpochMillis -
                                (snapshot.elapsedRealtimeMillis - environment.sampledElapsedMillis).coerceAtLeast(0), actions) { detail = it }
                        }
                        "bluetooth" -> BluetoothSection(state, actions, state.snapshot?.timestampEpochMillis) { detail = it }
                        "diagnostics" -> {
                            Text(state.samplingDiagnosticsSummary, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 16.dp))
                            Button(actions.onDiagnosticsExport, modifier = Modifier.fillMaxWidth()) { Text("导出采集诊断") }
                        }
                        else -> when (selectedTab) {
                            0 -> FunctionsPage { route = it }
                            1 -> OverviewPage(state, actions, accent) { detail = it }
                            else -> NativeSettingsPage(state, actions, { route = "overlay" }) { confirmClear = true }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
        if (confirmClear) ClearHistoryDialog(onDismiss = { confirmClear = false }, onConfirm = { confirmClear = false; actions.onClearHistory() })
        detail?.let { item -> MetricDetailSheet(item) { detail = null } }
    }
}

@Composable
private fun FunctionsPage(open: (String) -> Unit) {
    listOf(Triple("硬件信息", Icons.Rounded.Smartphone, "hardware"), Triple("电池电量", Icons.Rounded.BatteryFull, "battery"),
        Triple("性能记录", Icons.Rounded.Speed, "performance"), Triple("悬浮监视器", Icons.Rounded.Layers, "overlay"),
        Triple("网络状态", Icons.Rounded.Wifi, "network"), Triple("蓝牙设备", Icons.Rounded.Bluetooth, "bluetooth"),
        Triple("系统状态", Icons.Rounded.Info, "system"), Triple("采集诊断", Icons.Rounded.Tune, "diagnostics")).forEach { (title, icon, route) ->
        ListItem(headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
            leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable { open(route) })
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    }
}

@Composable
private fun AppHeader(state: MonitorUiState, title: String, back: Boolean, navigateBack: () -> Unit) {
    val live = liveState(state)
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().heightIn(min = 64.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (back) IconButton(navigateBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") }
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            if (state.samplingActive) Surface(shape = RoundedCornerShape(50), color = live.container) {
                Text(if (live.label == "实时") state.actualIntervalMillis?.let { ReadingFormat.interval(it.coerceAtLeast(1)) } ?: live.label else live.label,
                    Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = live.content, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun liveState(state: MonitorUiState): LiveState {
    if (!state.samplingActive) return LiveState("按需暂停", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
    val snapshot = state.snapshot
    val elapsedNow = foregroundElapsedTime()
    val age = snapshot?.elapsedRealtimeMillis?.takeIf { it in 0L..elapsedNow }?.let { elapsedNow - it }
    val stale = snapshot != null && (age == null || age > maxOf(15_000L, state.settings.sampleIntervalMs * 3))
    return when {
        snapshot == null -> LiveState("等待中", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
        stale -> LiveState("已过期", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
        else -> LiveState("实时", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

/** UI-only freshness clock; it neither samples hardware nor runs while below STARTED. */
@Composable
private fun foregroundElapsedTime(): Long {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val tick = remember(lifecycle) { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                tick.longValue = SystemClock.elapsedRealtime()
                delay(1_000L)
            }
        }
    }
    return maxOf(tick.longValue, SystemClock.elapsedRealtime())
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Warning, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("知道了") }
        }
    }
}

@Composable
private fun OverviewPage(
    state: MonitorUiState,
    actions: MonitorActions,
    accent: Color,
    showStorage: Boolean = false,
    onDetail: (MetricDetail) -> Unit,
) {
    val snapshot = state.snapshot
    state.device?.let { device ->
        SectionCard(device.model, Icons.Rounded.Smartphone) {
            InfoRow("芯片", device.cpuIdentity.name.value ?: "—")
            InfoRow("架构", device.abis.joinToString(" / "))
        }
    }
    if (snapshot == null) { HeroPlaceholder("CPU", Icons.Rounded.Speed); return }
    val stamp = snapshot.timestampEpochMillis
    val cpu = snapshot.cpu
    val averageFrequency = org.libremonitor.core.PerformancePoint.from(snapshot, 0,
        setOf(org.libremonitor.core.PerformanceMetric.CPU_FREQUENCY)).readings.getValue(org.libremonitor.core.PerformanceMetric.CPU_FREQUENCY)
    SectionCard("CPU", Icons.Rounded.DeveloperBoard) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(
                tile("负载", cpu.usagePercent, ::percentQuantity, validPercent, stamp, Icons.Rounded.Speed),
                tile("温度", cpu.temperatureC, ::temperatureQuantity, validTemperature, stamp, Icons.Rounded.Thermostat),
                tile("均频", averageFrequency, ::frequencyQuantity, positiveDouble, stamp, Icons.Rounded.Speed),
                tile("核心", if (cpu.coreIds.isEmpty()) Reading.unavailable<Int>("/proc/stat", "未取得逻辑核心")
                    else Reading(cpu.coreIds.size, "/proc/stat 逻辑核心编号"), { Quantity(it.toString(), null) }, { it > 0 }, stamp, Icons.Rounded.DeveloperBoard),
            ).forEach { item ->
                Column(Modifier.weight(1f).clickable { onDetail(item.detail) }.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(item.quantity?.display ?: "—", color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(item.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        CpuCoreGroups(cpu, state.device?.cpuIdentity, stamp, onDetail)
    }
    SectionCard("内存", Icons.Rounded.Memory) {
        val memory = snapshot.memory
        val used = memoryUsedPercent(memory)
        val memoryTile = tile("内存占用", used, ::percentQuantity, validPercent, stamp, Icons.Rounded.Memory)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(Modifier.clickable { onDetail(memoryTile.detail) }) {
                Gauge(used.validValue(validPercent)?.div(100), MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.outlineVariant, "内存", 100.dp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val total = memory.totalBytes.validValue { it > 0L }
                val available = memory.availableBytes.validValue(nonnegativeLong)
                val physical = if (total != null && available != null && available <= total)
                    "${bytesQuantity(total - available).display} / ${bytesQuantity(total).display}" else "—"
                val swapTotal = memory.swapTotalBytes.validValue(nonnegativeLong)
                val swapFree = memory.swapFreeBytes.validValue(nonnegativeLong)
                val swap = if (swapTotal != null && swapFree != null && swapFree <= swapTotal)
                    "${bytesQuantity(swapTotal - swapFree).display} / ${bytesQuantity(swapTotal).display}" else "—"
                InfoRow("物理内存", physical)
                InfoRow("可用", available?.let(::bytesQuantity)?.display ?: "—")
                InfoRow("Swap", swap)
            }
        }
    }
    SectionCard("GPU", Icons.Rounded.Memory) {
        snapshot.gpu.name.value?.takeIf { snapshot.gpu.name.available }?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
        MiniPair(tile("负载", snapshot.gpu.usagePercent, ::percentQuantity, validPercent, stamp, Icons.Rounded.Speed),
            tile("频率", snapshot.gpu.frequencyMhz, ::frequencyQuantity, positiveDouble, stamp, Icons.Rounded.Speed), onDetail)
    }
    SectionCard("帧率", Icons.Rounded.Speed) {
        val fpsStamp = snapshot.fps.sampledElapsedRealtimeMillis?.let { stamp - (snapshot.elapsedRealtimeMillis - it).coerceAtLeast(0) } ?: stamp
        MiniPair(tile("FPS", snapshot.fps.fps, ::fpsQuantity, { it.isFinite() && it >= 0 }, fpsStamp, Icons.Rounded.Speed),
            tile("1% low", snapshot.fps.onePercentLow, ::fpsQuantity, { it.isFinite() && it >= 0 }, fpsStamp, Icons.Rounded.Speed), onDetail)
    }
    SectionCard("网络", Icons.Rounded.Wifi) {
        MiniPair(tile("下载", snapshot.network.downloadBytesPerSecond, ::rateQuantity, nonnegativeDouble, stamp, Icons.Rounded.Download),
            tile("上传", snapshot.network.uploadBytesPerSecond, ::rateQuantity, nonnegativeDouble, stamp, Icons.Rounded.Upload), onDetail)
    }
    if (showStorage) SectionCard("存储", Icons.Rounded.Storage) {
        MiniPair(tile("可用", snapshot.storage.availableBytes, ::bytesQuantity, nonnegativeLong, stamp, Icons.Rounded.Storage),
            tile("总计", snapshot.storage.totalBytes, ::bytesQuantity, { it > 0 }, stamp, Icons.Rounded.Storage), onDetail)
    }
}

@Composable
private fun BluetoothSection(
    state: MonitorUiState,
    actions: MonitorActions,
    stamp: Long?,
    onDetail: (MetricDetail) -> Unit,
) {
    val bluetooth = state.snapshot?.bluetooth ?: return
    SectionCard("蓝牙", Icons.Rounded.Bluetooth) {
        if (!state.bluetoothPermissionGranted) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.BluetoothDisabled, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("需要蓝牙权限才能读取适配器和已配对设备", style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedButton(
                onClick = actions.onBluetoothPermissionRequest,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Icon(Icons.Rounded.Bluetooth, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("授权蓝牙")
            }
            return@SectionCard
        }
        MiniPair(
            tile("蓝牙状态", bluetooth.enabled, { Quantity(if (it) "开启" else "关闭", null) }, { true }, stamp, Icons.Rounded.Bluetooth),
            tile("适配器状态", bluetooth.state, { Quantity(it, null) }, { it.isNotBlank() }, stamp, Icons.Rounded.Devices),
            onDetail,
        )
        InfoRow("名称", bluetooth.name.validValue { it.isNotBlank() } ?: "—")
        InfoRow("地址", bluetooth.address.validValue { it.isNotBlank() } ?: "—")
        InfoRow("核心版本", bluetooth.version.validValue { it.isNotBlank() } ?: "系统未公开")
        InfoRow("LE 支持", bluetooth.leSupported.validValue { true }?.let { if (it) "支持" else "不支持" } ?: "—")
        InfoRow("已连接配置", bluetooth.connectedCount.validValue { it >= 0 }?.let { "$it 个 Profile" } ?: "—")
        MiniPair(
            tile("蓝牙 PAN 下载", state.snapshot!!.network.bluetoothDownload, ::rateQuantity, nonnegativeDouble, stamp, Icons.Rounded.ArrowDownward),
            tile("蓝牙 PAN 上传", state.snapshot.network.bluetoothUpload, ::rateQuantity, nonnegativeDouble, stamp, Icons.Rounded.ArrowUpward), onDetail)
        Text("仅统计蓝牙 PAN 网络接口；耳机音频链路没有通用公开吞吐计数。协议 UUID 代表能力，不代表当前编码或链路速率。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        bluetooth.capabilities.forEach { (label, reading) ->
            InfoRow(label, reading.value ?: "系统未公开（点设备查看协议能力）")
        }
        if (bluetooth.devices.isEmpty()) {
            EmptyLine("—", "没有已配对设备")
        } else {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text("已配对设备 · ${bluetooth.devices.size}", style = MaterialTheme.typography.titleSmall)
            val elapsed = foregroundElapsedTime()
            bluetooth.devices.forEach { device ->
                val observation = state.earbudObservations.firstOrNull { it.address.equals(device.address, true) &&
                    elapsed - it.sampledElapsedMillis in 0..60_000 }
                BluetoothDeviceRow(if (observation == null) device else device.copy(earbudBattery = observation.battery,
                    rssiDbm = observation.rssi.takeIf { it in -127..20 }?.let { Reading(it, "匹配同一蓝牙地址的 BLE 广播") }
                        ?: Reading.unavailable("BLE 广播", "RSSI 无效")),
                    if (observation == null) stamp else System.currentTimeMillis() - (elapsed - observation.sampledElapsedMillis), onDetail)
            }
        }
        EarbudScanPanel(state, actions)
    }
}

@Composable
private fun BluetoothDeviceRow(device: BluetoothDeviceInfo, stamp: Long?, onDetail: (MetricDetail) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) {
                onDetail(
                    MetricDetail(
                        label = device.name,
                        valueText = device.protocol,
                        status = if (device.batteryPercent.available || device.rssiDbm.available ||
                            device.earbudBattery?.let { it.left != null || it.right != null || it.case != null } == true) ReadingStatus.AVAILABLE else ReadingStatus.UNSUPPORTED,
                        source = if (device.earbudBattery != null) "BluetoothClass / UUID + 同地址 Apple BLE 广播" else "BluetoothDevice / BluetoothClass / UUID",
                        reason = if (device.earbudBattery != null) "电量为厂商广播的 10% 近似档位；只按相同蓝牙地址关联，缺失值不作为 0。"
                            else "协议由设备类型、Class 服务和已知 UUID 映射；RSSI 需要扫描或 GATT 连接，Android 不提供已配对设备的实时 RSSI。",
                        note = "类型 ${device.type} · ${device.bondState} · 主类 ${device.majorClass} · Class ${device.deviceClass}",
                        sampledAtEpochMillis = stamp,
                    ),
                )
            }
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.Devices, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text(device.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(device.bondState, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            "${device.type} · ${device.protocol} · ${device.majorClass}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                device.earbudBattery?.let { "左约 ${it.left?.let { x -> "$x%" } ?: "—"} · 右约 ${it.right?.let { x -> "$x%" } ?: "—"} · 盒约 ${it.case?.let { x -> "$x%" } ?: "—"}" }
                    ?: "电量 ${device.batteryPercent.validValue { it in 0..100 }?.let { "$it%" } ?: "—"}",
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                "信号 ${device.rssiDbm.validValue { it < 0 }?.let { "$it dBm" } ?: "—"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CpuCoreGroups(cpu: CpuSample, identity: org.libremonitor.core.CpuIdentity?, stamp: Long?, onDetail: (MetricDetail) -> Unit) {
    val count = maxOf(cpu.perCoreUsagePercent.size, cpu.frequenciesMhz.size, cpu.coreIds.size)
    val known = identity?.cores.orEmpty().associateBy { it.id }
    val groups = (0 until count).groupBy { index -> known[cpu.coreIds.getOrNull(index) ?: index]?.tier ?: org.libremonitor.core.CoreTier.UNKNOWN }
    org.libremonitor.core.CoreTier.entries.forEach { tier ->
        val indices = groups[tier].orEmpty()
        if (indices.isEmpty()) return@forEach
        val color = when (tier) {
            org.libremonitor.core.CoreTier.PRIME -> MaterialTheme.colorScheme.tertiary
            org.libremonitor.core.CoreTier.PERFORMANCE -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.secondary
        }
        Surface(color = color.copy(alpha = 0.08f), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${tier.label} · ${indices.size}${if (indices.any { known[cpu.coreIds.getOrNull(it) ?: it]?.source?.contains("推断") == true }) "（推断）" else ""}", color = color, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall)
                if (indices.isEmpty()) Text("未识别到该类型的逻辑核心", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                indices.forEach { index -> CoreRow(cpu, index, stamp, onDetail) }
            }
        }
    }
}

@Composable
private fun EarbudScanPanel(state: MonitorUiState, actions: MonitorActions) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Text("AirPods 广播电量", style = MaterialTheme.typography.titleSmall)
    Text(state.bluetoothScanMessage, style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedButton(onClick = if (state.bluetoothScanning) actions.onEarbudScanStop else actions.onEarbudScan) {
        Text(if (state.bluetoothScanning) "停止扫描" else "扫描附近耳机电量")
    }
    val elapsed = foregroundElapsedTime()
    state.earbudObservations.forEach { observation ->
        val fresh = elapsed - observation.sampledElapsedMillis in 0..60_000
        val battery = observation.battery
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("${battery.model} · ${observation.address.takeLast(5)}", fontWeight = FontWeight.Medium)
            Text(if (!fresh) "广播已过期，请重新扫描" else {
                fun level(value: Int?, charging: Boolean) = value?.let { "约 $it%${if (charging) " ⚡" else ""}" } ?: "—"
                "左 ${level(battery.left, battery.leftCharging)} · 右 ${level(battery.right, battery.rightCharging)} · 盒 ${level(battery.case, battery.caseCharging)}"
            }, style = MaterialTheme.typography.bodyMedium)
            Text(if (fresh) "BLE RSSI ${observation.rssi.takeIf { it in -127..20 }?.let { "$it dBm" } ?: "—"} · ${(elapsed - observation.sampledElapsedMillis) / 1_000} 秒前" else "此读数不再作为当前电量显示",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Text("10% 档位的厂商广播近似值；仅适配已识别的 AirPods / Pro 格式。随机地址不会仅凭名字自动关联到已配对设备。",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun EnvironmentSections(environment: org.libremonitor.core.EnvironmentSample, stamp: Long,
    actions: MonitorActions, onDetail: (MetricDetail) -> Unit) {
    var networkExpanded by rememberSaveable { mutableStateOf(false) }
    var systemExpanded by rememberSaveable { mutableStateOf(false) }
    if (environment.connectivity.isNotEmpty()) SectionCard("连接 / 移动网络 / 热点", Icons.Rounded.Wifi) {
        val fields = environment.connectivity.entries.toList().let { if (networkExpanded) it else it.take(4) }
        MetricGrid(fields.map { (label, reading) -> tile(label, reading, { Quantity(it, null) }, { it.isNotBlank() }, stamp, Icons.Rounded.Wifi) }, 2, onDetail)
        TextButton(onClick = { networkExpanded = !networkExpanded }) { Text(if (networkExpanded) "收起详细网络字段" else "展开移动网络与热点字段") }
        if (environment.connectivity["移动制式"]?.status == ReadingStatus.PERMISSION_DENIED) {
            OutlinedButton(onClick = actions.onPhonePermissionRequest) { Text("授权电话状态以读取制式和信号") }
        }
    }
    if (environment.system.isNotEmpty()) SectionCard("系统状态", Icons.Rounded.Smartphone) {
        val fields = environment.system.entries.toList().let { if (systemExpanded) it else it.take(4) }
        MetricGrid(fields.map { (label, reading) -> tile(label, reading, { Quantity(it, null) }, { it.isNotBlank() }, stamp, Icons.Rounded.Smartphone) }, 2, onDetail)
        TextButton(onClick = { systemExpanded = !systemExpanded }) { Text(if (systemExpanded) "收起系统字段" else "展开省电 / 热状态等字段") }
    }
}

@Composable
private fun HeroPlaceholder(title: String, icon: ImageVector) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text("等待首个样本", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun Gauge(fraction: Double?, color: Color, track: Color, label: String, size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 9.dp.toPx()
            val inset = stroke / 2
            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(this.size.width - stroke, this.size.height - stroke),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            val value = (fraction ?: 0.0).coerceIn(0.0, 1.0).toFloat()
            if (value > 0f) {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * value,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(this.size.width - stroke, this.size.height - stroke),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                fraction?.let { "${(it * 100).roundToInt()}%" } ?: "—",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CoreRow(cpu: CpuSample, index: Int, stamp: Long?, onDetail: (MetricDetail) -> Unit) {
    val coreId = cpu.coreIds.getOrNull(index) ?: index
    val usage = cpu.perCoreUsagePercent.getOrNull(index)
    val frequency = cpu.frequenciesMhz.getOrNull(index)?.validValue { it > 0L }
    val percent = usage?.validValue(validPercent)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) {
                onDetail(
                    MetricDetail(
                        label = "核心 $coreId",
                        valueText = percent?.let { ReadingFormat.percent(it) },
                        status = usage?.status ?: ReadingStatus.UNSUPPORTED,
                        source = usage?.source ?: "逐核使用率",
                        reason = usage?.missingExplanation(validPercent),
                        note = "编号来自采集到的逻辑核心 ID；频率为驱动报告值",
                        sampledAtEpochMillis = stamp,
                    ),
                )
            }
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("CPU$coreId", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(44.dp))
        Box(
            Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                Modifier
                    .fillMaxWidth((percent ?: 0.0).div(100.0).toFloat().coerceIn(0f, 1f))
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        Text(
            percent?.let { "${it.roundToInt()}%" } ?: "—",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.width(44.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
        Text(
            frequency?.let { "${it} MHz" } ?: "—",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(66.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

@Composable
private fun UsageRow(
    label: String,
    reading: Reading<Double>,
    valid: (Double) -> Boolean,
    stamp: Long?,
    onDetail: (MetricDetail) -> Unit,
    icon: ImageVector,
) {
    val value = reading.validValue(valid)
    val tile = tile(label, reading, ::percentQuantity, valid, stamp, icon)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { onDetail(tile.detail) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(74.dp))
        Box(
            Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                Modifier
                    .fillMaxWidth((value ?: 0.0).div(100.0).toFloat().coerceIn(0f, 1f))
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        Text(
            value?.let { ReadingFormat.percent(it) } ?: "—",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.width(54.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

@Composable
private fun MiniPair(left: QuantityTile, right: QuantityTile, onDetail: (MetricDetail) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MetricTile(left, onDetail, Modifier.weight(1f))
        MetricTile(right, onDetail, Modifier.weight(1f))
    }
}

@Composable
private fun BatteryPage(
    state: MonitorUiState,
    actions: MonitorActions,
    accent: Color,
    onDetail: (MetricDetail) -> Unit,
    requestClear: () -> Unit,
) {
    val snapshot = state.snapshot
    val battery = snapshot?.battery
    val stamp = snapshot?.timestampEpochMillis
    BatteryHero(battery, stamp, onDetail)
    if (battery == null) {
        MetricGrid(
            listOf(
                pendingTile("电压", Icons.Rounded.Bolt),
                pendingTile("电流", Icons.Rounded.Power),
                pendingTile("功率", Icons.Rounded.Power),
                pendingTile("温度", Icons.Rounded.Thermostat),
                pendingTile("健康状态", Icons.Rounded.Favorite),
                pendingTile("容量健康", Icons.Rounded.BatteryChargingFull),
            ),
            2,
            onDetail,
        )
    } else {
        MetricGrid(
            listOf(
                tile("电压", battery.voltageV, ::voltageQuantity, positiveDouble, stamp, Icons.Rounded.Bolt),
                tile("电流", battery.currentMa, ::currentQuantity, finiteNumber, stamp, Icons.Rounded.Power),
                tile("电池端功率", battery.powerW, ::powerQuantity, finiteNumber, stamp, Icons.Rounded.Power),
                tile("温度", battery.temperatureC, ::temperatureQuantity, validTemperature, stamp, Icons.Rounded.Thermostat),
                tile("健康状态", battery.healthStatus, { Quantity(it, null) }, { it.isNotBlank() }, stamp, Icons.Rounded.Favorite),
                tile("容量健康", battery.healthPercent, ::percentQuantity, validHealthPercent, stamp, Icons.Rounded.BatteryChargingFull),
                tile("设计容量", battery.designCapacityMah, ::capacityQuantity, positiveDouble, stamp, Icons.Rounded.BatteryFull),
                tile("满充容量", battery.fullChargeCapacityMah, ::capacityQuantity, positiveDouble, stamp, Icons.Rounded.BatteryFull),
                tile("剩余电荷", battery.chargeCounterMah, ::capacityQuantity, nonnegativeDouble, stamp, Icons.Rounded.BatteryFull),
                tile("循环次数", battery.cycleCount, ::cycleQuantity, { it >= 0L }, stamp, Icons.Rounded.Autorenew),
                tile("充电源", battery.chargingSource, { Quantity(it, null) }, { it.isNotBlank() }, stamp, Icons.Rounded.Power),
            ),
            2,
            onDetail,
        )
    }
    SectionCard("电池趋势", Icons.Rounded.History) {
        BatteryTrend(state.history, state.settings.historyEnabled, state.settings.historyIntervalMs)
    }
    HistoryControls(state, actions, requestClear)
}

@Composable
private fun BatteryHero(battery: BatterySample?, stamp: Long?, onDetail: (MetricDetail) -> Unit) {
    val level = battery?.levelPercent
    val value = level?.validValue(validBatteryLevel)
    val tile = if (level != null) tile("电量", level, ::levelQuantity, validBatteryLevel, stamp, Icons.Rounded.BatteryFull)
    else pendingTile("电量", Icons.Rounded.BatteryFull)
    Card(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { onDetail(tile.detail) },
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Icon(Icons.Rounded.BatteryChargingFull, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text("电池", style = MaterialTheme.typography.titleSmall)
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        value?.toString() ?: "—",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    value?.let {
                        Text("%", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
                    }
                }
                Text(
                    battery?.let { batteryStatusLabel(it.status) } ?: "等待样本",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Gauge(
                fraction = value?.div(100.0),
                color = MaterialTheme.colorScheme.primary,
                track = MaterialTheme.colorScheme.surfaceVariant,
                label = "电量",
                size = 100.dp,
            )
        }
    }
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.semantics { contentDescription = title })
    }
}

@Composable
private fun RootControl(state: MonitorUiState, actions: MonitorActions) {
    var confirmRequest by rememberSaveable { mutableStateOf(false) }
    var confirmGrant by rememberSaveable { mutableStateOf(false) }
    val root = state.rootState
    val status = when (root.access) {
        RootAccess.DISABLED -> "未启用"
        RootAccess.REQUESTING -> "等待授权"
        RootAccess.GRANTED -> "已启用 · Root"
        RootAccess.DENIED -> "未授权"
        RootAccess.UNAVAILABLE -> "不可用"
        RootAccess.ERROR -> "连接异常"
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text("Root", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(status, style = MaterialTheme.typography.labelLarge)
    }
    state.permissionGrantMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    when (root.access) {
        RootAccess.REQUESTING -> OutlinedButton(
            onClick = {},
            enabled = false,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text("等待授权…") }
        RootAccess.GRANTED -> {
            OutlinedButton(
                onClick = { confirmGrant = true },
                enabled = !state.permissionGrantRunning,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (state.permissionGrantRunning) "正在授权…" else "一键授权通知、悬浮窗、电池、蓝牙")
            }
            TextButton(onClick = actions.onRootDisable, modifier = Modifier.fillMaxWidth()) {
                Text("停用 Root")
            }
        }
        else -> OutlinedButton(
            onClick = { confirmRequest = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Icon(Icons.Rounded.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (root.access == RootAccess.DISABLED) "启用 Root" else "手动重试")
        }
    }
    if (confirmGrant) {
        AlertDialog(
            onDismissRequest = { confirmGrant = false },
            title = { Text("一键授予所需权限？") },
            text = {
                Text("通过现有 Root 会话仅对本应用执行固定命令：\n· 通知权限\n· 悬浮窗权限\n· 电池优化白名单\n· 蓝牙连接权限\n\n不开放 shell，不修改其他应用或系统文件。")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmGrant = false
                    actions.onRootGrantPermissions()
                }) { Text("确认授权") }
            },
            dismissButton = { TextButton(onClick = { confirmGrant = false }) { Text("取消") } },
        )
    }
    if (confirmRequest) {
        AlertDialog(
            onDismissRequest = { confirmRequest = false },
            title = { Text("启用 Root？") },
            text = { Text("仅读取固定白名单节点；授权由系统 Root 管理器决定。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRequest = false
                    actions.onRootRequest()
                }) { Text("请求授权") }
            },
            dismissButton = { TextButton(onClick = { confirmRequest = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun HistoryControls(state: MonitorUiState, actions: MonitorActions, requestClear: () -> Unit) {
    SectionCard("记录", Icons.Rounded.History) {
        SwitchRow(
            icon = Icons.Rounded.History,
            title = "保存电池历史",
            subtitle = "约每 ${ReadingFormat.interval(state.settings.historyIntervalMs)} 一条 · 保留 ${state.settings.retentionDays} 天",
            checked = state.settings.historyEnabled,
            onCheckedChange = actions.onHistoryEnabledChange,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = actions.onExport, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Icon(Icons.Rounded.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("导出 CSV")
            }
            OutlinedButton(
                onClick = requestClear,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("清除")
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() },
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            content()
        }
    }
}

private data class QuantityTile(
    val label: String,
    val quantity: Quantity?,
    val detail: MetricDetail,
    val icon: ImageVector,
)

private data class MetricDetail(
    val label: String,
    val valueText: String?,
    val status: ReadingStatus,
    val source: String,
    val reason: String?,
    val note: String?,
    val sampledAtEpochMillis: Long?,
)

private fun <T> tile(
    label: String,
    reading: Reading<T>,
    format: (T) -> Quantity,
    valid: (T) -> Boolean,
    sampledAt: Long?,
    icon: ImageVector,
): QuantityTile {
    val quantity = reading.validValue(valid)?.let(format)
    return QuantityTile(
        label = label,
        quantity = quantity,
        icon = icon,
        detail = MetricDetail(
            label = label,
            valueText = quantity?.display,
            status = if (quantity == null) reading.status else ReadingStatus.AVAILABLE,
            source = reading.source,
            reason = if (quantity == null) reading.missingExplanation(valid) else reading.reason?.takeIf { it.isNotBlank() },
            note = null,
            sampledAtEpochMillis = sampledAt,
        ),
    )
}

private fun pendingTile(label: String, icon: ImageVector): QuantityTile = tile(
    label = label,
    reading = Reading.unavailable<Double>("尚未采样", "尚未获得首个有效样本"),
    format = ::percentQuantity,
    valid = { true },
    sampledAt = null,
    icon = icon,
)

@Composable
private fun MetricGrid(specs: List<QuantityTile>, columns: Int, onDetail: (MetricDetail) -> Unit) {
    if (specs.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        specs.chunked(columns.coerceAtLeast(1)).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { spec -> MetricTile(spec, onDetail, Modifier.weight(1f)) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun MetricTile(spec: QuantityTile, onDetail: (MetricDetail) -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .heightIn(min = 84.dp)
            .clickable(role = Role.Button) { onDetail(spec.detail) },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(spec.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Text(
                    spec.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    spec.quantity?.value ?: "—",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                spec.quantity?.unit?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 3.dp, bottom = 3.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.42f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(0.58f))
    }
}

@Composable
private fun EmptyLine(value: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DeviceRows(device: DeviceInfo?) {
    if (device == null) {
        Text("设备信息尚未就绪", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    InfoRow("设备", listOf(deviceText(device.manufacturer), deviceText(device.model)).joinToString(" "))
    InfoRow("系统", "Android ${deviceText(device.androidVersion)} · API ${device.sdkInt.takeIf { it > 0 } ?: "—"}")
    InfoRow("架构", device.abis.takeIf { it.isNotEmpty() }?.joinToString(" / ") ?: "—")
    InfoRow("逻辑处理器", device.processorCount.takeIf { it > 0 }?.toString() ?: "—")
    InfoRow("显示", if (device.screenWidthPx > 0 && device.screenHeightPx > 0) "${device.screenWidthPx} × ${device.screenHeightPx} px" else "—")
    InfoRow("刷新率", device.refreshRateHz.takeIf { it.isFinite() && it > 0f }?.let { "${ReadingFormat.number(it.toDouble())} Hz" } ?: "—")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MetricDetailSheet(item: MetricDetail, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(item.label, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(item.valueText ?: "—", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
            val status = when (item.status) {
                ReadingStatus.AVAILABLE -> "可用"
                ReadingStatus.PERMISSION_DENIED -> "权限拒绝"
                ReadingStatus.UNSUPPORTED -> "设备不支持"
                ReadingStatus.ERROR -> "读取异常"
            }
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
                Text(status, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
            }
            item.reason?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            item.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Text(sourceLabel(item.source), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            item.sampledAtEpochMillis?.let {
                Text("样本 ${ReadingFormat.time(it, true)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ClearHistoryDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("清除全部电池记录？") },
        text = { Text("永久删除本机保存的全部历史，无法撤销；已导出的 CSV 不受影响。") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("确认清除", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun memoryUsedPercent(memory: MemorySample): Reading<Double> {
    val total = memory.totalBytes.validValue { it > 0L }
        ?: return Reading.unavailable("内存占用率", "缺少有效的内存总量")
    val available = memory.availableBytes.validValue { it in 0L..total }
        ?: return Reading.unavailable("内存占用率", "缺少有效的可用内存")
    return Reading((total - available).toDouble() * 100.0 / total.toDouble(), "由内存总量与可用量推算")
}

private fun swapUsedPercent(memory: MemorySample): Reading<Double> {
    val total = memory.swapTotalBytes.validValue { it > 0L }
        ?: return Reading.unavailable("/proc/meminfo:SwapUsed", "设备未启用 Swap")
    val free = memory.swapFreeBytes.validValue { it in 0L..total }
        ?: return Reading.unavailable("/proc/meminfo:SwapUsed", "缺少 Swap 空闲量")
    return Reading((total - free).toDouble() * 100.0 / total.toDouble(), "SwapTotal − SwapFree")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NativeSettingsPage(state: MonitorUiState, actions: MonitorActions, overlay: () -> Unit, clear: () -> Unit) {
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    var rootPanel by rememberSaveable { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val version = remember(context) { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }.getOrDefault("—") }
    Text("监测", Modifier.padding(top = 12.dp, bottom = 4.dp), color = MaterialTheme.colorScheme.primary)
    NativeSettingRow("刷新间隔", Icons.Rounded.Tune, ReadingFormat.interval(state.settings.sampleIntervalMs)) { dialog = "interval" }
    NativeSettingRow("悬浮监视器", Icons.Rounded.Layers, if (state.overlayEnabled) "开启" else "关闭", overlay)
    NativeSettingRow("运行模式", Icons.Rounded.Shield, if (state.rootState.access == RootAccess.GRANTED) "Root" else "普通") { rootPanel = true }
    ListItem(headlineContent = { Text("打开应用时恢复 Root") }, leadingContent = { Icon(Icons.Rounded.Refresh, null) },
        trailingContent = { Switch(state.settings.autoRestoreRoot, actions.onAutoRestoreRootChange) })
    HorizontalDivider()
    Text("电池", Modifier.padding(top = 12.dp, bottom = 4.dp), color = MaterialTheme.colorScheme.primary)
    NativeSettingRow("电流单位", Icons.Rounded.Power, state.settings.batteryCurrentMode.label) { dialog = "current" }
    ListItem(headlineContent = { Text("保存电池历史") }, leadingContent = { Icon(Icons.Rounded.History, null) },
        trailingContent = { Switch(state.settings.historyEnabled, actions.onHistoryEnabledChange) })
    NativeSettingRow("导出电池历史", Icons.Rounded.FileDownload, "CSV", actions.onExport)
    NativeSettingRow("清除电池历史", Icons.Rounded.Delete, "${state.history.size} 条", clear)
    HorizontalDivider()
    Text("关于", Modifier.padding(top = 12.dp, bottom = 4.dp), color = MaterialTheme.colorScheme.primary)
    ListItem(headlineContent = { Text("LibreMonitor") }, trailingContent = { Text(version) }, leadingContent = { Icon(Icons.Rounded.Info, null) })
    ListItem(headlineContent = { Text("许可证") }, trailingContent = { Text("Apache-2.0") })
    if (dialog != null) {
        val current = dialog
        AlertDialog(onDismissRequest = { dialog = null }, title = { Text(if (current == "interval") "刷新间隔" else "电流单位") },
            text = { Column {
                if (current == "interval") listOf(100L, 200L, 500L, 1_000L, 2_000L, 5_000L, 10_000L).forEach { interval ->
                    Row(Modifier.fillMaxWidth().clickable { actions.onSampleIntervalChange(interval); dialog = null }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(state.settings.sampleIntervalMs == interval, { actions.onSampleIntervalChange(interval); dialog = null })
                        Text(ReadingFormat.interval(interval))
                    }
                } else org.libremonitor.core.BatteryCurrentMode.entries.forEach { mode ->
                    Row(Modifier.fillMaxWidth().clickable { actions.onBatteryCurrentModeChange(mode); dialog = null }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(state.settings.batteryCurrentMode == mode, { actions.onBatteryCurrentModeChange(mode); dialog = null })
                        Text(mode.label)
                    }
                }
            } }, confirmButton = { TextButton({ dialog = null }) { Text("关闭") } })
    }
    if (rootPanel) ModalBottomSheet(onDismissRequest = { rootPanel = false }) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { RootControl(state, actions) }
    }
}

@Composable
private fun NativeSettingRow(title: String, icon: ImageVector, value: String, click: () -> Unit) {
    ListItem(headlineContent = { Text(title) }, leadingContent = { Icon(icon, null) },
        trailingContent = { Row(verticalAlignment = Alignment.CenterVertically) { Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant); Icon(Icons.Rounded.ChevronRight, null) } },
        modifier = Modifier.clickable(onClick = click))
}
