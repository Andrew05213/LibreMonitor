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
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeveloperBoard
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
import org.libremonitor.core.CpuSample
import org.libremonitor.core.DeviceInfo
import org.libremonitor.core.MemorySample
import org.libremonitor.core.Reading
import org.libremonitor.core.ReadingStatus
import org.libremonitor.core.RootAccess
import kotlin.math.roundToInt

private val LightColors = lightColorScheme(
    primary = Color(0xFF0B6B57),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB8F2DF),
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
    primary = Color(0xFF7DE2C3),
    onPrimary = Color(0xFF00382C),
    primaryContainer = Color(0xFF0D5143),
    onPrimaryContainer = Color(0xFFB8F2DF),
    secondary = Color(0xFFB0CCC1),
    secondaryContainer = Color(0xFF304C42),
    tertiary = Color(0xFFFFC46B),
    background = Color(0xFF0A1210),
    onBackground = Color(0xFFE1EAE4),
    surface = Color(0xFF111D19),
    onSurface = Color(0xFFE1EAE4),
    surfaceVariant = Color(0xFF25352E),
    onSurfaceVariant = Color(0xFFB7C7BD),
    outline = Color(0xFF87998E),
    outlineVariant = Color(0xFF3A4D43),
)

private val LightAccent = Color(0xFFF0A227)
private val DarkAccent = Color(0xFFFFC46B)

private data class LiveState(val label: String, val container: Color, val content: Color)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibreMonitorScreen(state: MonitorUiState, actions: MonitorActions) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var detail by remember { mutableStateOf<MetricDetail?>(null) }
    val dark = isSystemInDarkTheme()
    val colors = if (dark) DarkColors else LightColors
    val accent = if (dark) DarkAccent else LightAccent
    val tabs = listOf(
        Triple("概览", Icons.Rounded.Dashboard, 0),
        Triple("电池", Icons.Rounded.BatteryFull, 1),
        Triple("设置", Icons.Rounded.Settings, 2),
    )

    MaterialTheme(colorScheme = colors) {
        Scaffold(
            containerColor = colors.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = { AppHeader(state) },
            bottomBar = {
                NavigationBar(containerColor = colors.surface, tonalElevation = 0.dp) {
                    tabs.forEach { (label, icon, index) ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) },
                        )
                    }
                }
            },
        ) { insets ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(insets),
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    state.error?.let { ErrorBanner(it, actions.onDismissError) }
                    key(selectedTab) {
                        when (selectedTab) {
                            0 -> OverviewPage(state, accent) { detail = it }
                            1 -> BatteryPage(state, actions, accent, { detail = it }) { confirmClear = true }
                            else -> SettingsPage(state, actions, accent) { confirmClear = true }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        if (confirmClear) {
            ClearHistoryDialog(
                onDismiss = { confirmClear = false },
                onConfirm = {
                    confirmClear = false
                    actions.onClearHistory()
                },
            )
        }
        detail?.let { item -> MetricDetailSheet(item, onDismiss = { detail = null }) }
    }
}

@Composable
private fun AppHeader(state: MonitorUiState) {
    val live = liveState(state)
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Monitor,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(25.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "LibreMonitor",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "设备仪表盘",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(shape = RoundedCornerShape(50), color = live.container) {
                Row(
                    Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Box(Modifier.size(7.dp).background(live.content, CircleShape))
                    Text(live.label, style = MaterialTheme.typography.labelMedium, color = live.content)
                }
            }
        }
    }
}

@Composable
private fun liveState(state: MonitorUiState): LiveState {
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
private fun OverviewPage(state: MonitorUiState, accent: Color, onDetail: (MetricDetail) -> Unit) {
    val snapshot = state.snapshot
    if (snapshot == null) {
        HeroPlaceholder("CPU", Icons.Rounded.Speed)
        MetricGrid(
            listOf(
                pendingTile("CPU 温度", Icons.Rounded.Thermostat),
                pendingTile("电量", Icons.Rounded.BatteryFull),
                pendingTile("内存", Icons.Rounded.Memory),
            ),
            2,
            onDetail,
        )
        return
    }
    val stamp = snapshot.timestampEpochMillis
    CpuHero(snapshot.cpu, stamp, accent, onDetail)
    MetricGrid(
        listOf(
            tile("CPU 温度", snapshot.cpu.temperatureC, ::temperatureQuantity, validTemperature, stamp, Icons.Rounded.Thermostat),
            tile("电量", snapshot.battery.levelPercent, ::levelQuantity, validBatteryLevel, stamp, Icons.Rounded.BatteryFull),
            tile("内存已用", memoryUsedPercent(snapshot.memory), ::percentQuantity, validPercent, stamp, Icons.Rounded.Memory),
            tile("可用内存", snapshot.memory.availableBytes, ::bytesQuantity, nonnegativeLong, stamp, Icons.Rounded.Memory),
        ),
        2,
        onDetail,
    )
    val cpu = snapshot.cpu
    SectionCard("逐核负载", Icons.Rounded.DeveloperBoard) {
        val count = maxOf(cpu.perCoreUsagePercent.size, cpu.frequenciesMhz.size, cpu.coreIds.size)
        if (count == 0) {
            EmptyLine("—", "系统未返回逐核读数")
        } else {
            repeat(count) { index -> CoreRow(cpu, index, stamp, onDetail) }
        }
    }
    SectionCard("内存 / Swap", Icons.Rounded.Memory) {
        UsageRow("内存已用", memoryUsedPercent(snapshot.memory), validPercent, stamp, onDetail, Icons.Rounded.Memory)
        UsageRow("Swap 已用", swapUsedPercent(snapshot.memory), validPercent, stamp, onDetail, Icons.Rounded.Storage)
        MiniPair(
            tile("可用内存", snapshot.memory.availableBytes, ::bytesQuantity, nonnegativeLong, stamp, Icons.Rounded.Memory),
            tile("内存总计", snapshot.memory.totalBytes, ::bytesQuantity, { it > 0L }, stamp, Icons.Rounded.Memory),
            onDetail,
        )
    }
    SectionCard("网络", Icons.Rounded.Wifi) {
        MiniPair(
            tile("下载", snapshot.network.downloadBytesPerSecond, ::rateQuantity, nonnegativeDouble, stamp, Icons.Rounded.ArrowDownward),
            tile("上传", snapshot.network.uploadBytesPerSecond, ::rateQuantity, nonnegativeDouble, stamp, Icons.Rounded.ArrowUpward),
            onDetail,
        )
        MiniPair(
            tile("累计接收", snapshot.network.totalReceivedBytes, ::bytesQuantity, nonnegativeLong, stamp, Icons.Rounded.Download),
            tile("累计发送", snapshot.network.totalSentBytes, ::bytesQuantity, nonnegativeLong, stamp, Icons.Rounded.Upload),
            onDetail,
        )
    }
    SectionCard("存储", Icons.Rounded.Storage) {
        MiniPair(
            tile("可用空间", snapshot.storage.availableBytes, ::bytesQuantity, nonnegativeLong, stamp, Icons.Rounded.Storage),
            tile("分区总计", snapshot.storage.totalBytes, ::bytesQuantity, { it > 0L }, stamp, Icons.Rounded.Storage),
            onDetail,
        )
    }
    if (snapshot.gpu.usagePercent.available || snapshot.gpu.frequencyMhz.available) {
        SectionCard("图形", Icons.Rounded.Memory) {
            MiniPair(
                tile("GPU 占用", snapshot.gpu.usagePercent, ::percentQuantity, validPercent, stamp, Icons.Rounded.Memory),
                tile("GPU 频率", snapshot.gpu.frequencyMhz, ::frequencyQuantity, positiveDouble, stamp, Icons.Rounded.Speed),
                onDetail,
            )
        }
    }
}

@Composable
private fun CpuHero(cpu: CpuSample, stamp: Long?, accent: Color, onDetail: (MetricDetail) -> Unit) {
    val tile = tile("CPU 占用", cpu.usagePercent, ::percentQuantity, validPercent, stamp, Icons.Rounded.Speed)
    Card(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { onDetail(tile.detail) },
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Icon(Icons.Rounded.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text("CPU", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        tile.quantity?.value ?: "—",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    tile.quantity?.unit?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                        )
                    }
                }
                Text(
                    "温度 ${cpu.temperatureC.validValue(validTemperature)?.let(::temperatureQuantity)?.display ?: "—"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
            Gauge(
                fraction = tile.quantity?.value?.toDoubleOrNull()?.div(100.0),
                color = MaterialTheme.colorScheme.primary,
                track = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.14f),
                label = "占用",
                size = 100.dp,
            )
        }
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
                tile("功率", battery.powerW, ::powerQuantity, finiteNumber, stamp, Icons.Rounded.Power),
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
private fun SettingsPage(state: MonitorUiState, actions: MonitorActions, accent: Color, requestClear: () -> Unit) {
    SectionCard("刷新频率", Icons.Rounded.Tune) {
        listOf(listOf(1_000L, 2_000L), listOf(5_000L, 10_000L)).forEach { intervals ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                intervals.forEach { interval ->
                    FilterChip(
                        selected = state.settings.sampleIntervalMs == interval,
                        onClick = { actions.onSampleIntervalChange(interval) },
                        label = { Text(ReadingFormat.interval(interval)) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    )
                }
            }
        }
    }
    SectionCard("悬浮窗", Icons.Rounded.Layers) {
        SwitchRow(
            icon = Icons.Rounded.Layers,
            title = "显示悬浮监测",
            subtitle = if (state.overlayEnabled) "已开启" else "已关闭",
            checked = state.overlayEnabled,
            onCheckedChange = { actions.onOverlayToggle() },
        )
    }
    SectionCard("电池记录", Icons.Rounded.History) {
        SwitchRow(
            icon = Icons.Rounded.History,
            title = "保存电池历史",
            subtitle = "每 ${ReadingFormat.interval(state.settings.historyIntervalMs)} · 保留 ${state.settings.retentionDays} 天",
            checked = state.settings.historyEnabled,
            onCheckedChange = actions.onHistoryEnabledChange,
        )
        Text(
            "当前 ${state.history.size} 条",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    SectionCard("访问能力", Icons.Rounded.Shield) {
        InfoRow("普通模式", "逐项检测")
        InfoRow("Shizuku", "未接入")
        RootControl(state, actions)
    }
    SectionCard("设备", Icons.Rounded.Smartphone) {
        DeviceRows(state.device)
    }
    SectionCard("关于", Icons.Rounded.Info) {
        InfoRow("版本", "0.3.1-dev")
        InfoRow("许可证", "Apache-2.0")
        InfoRow("网络", "无联网权限")
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
                Text(if (state.permissionGrantRunning) "正在授权…" else "一键授权通知、悬浮窗、电池白名单")
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
                Text("通过现有 Root 会话仅对本应用执行固定命令：\n· 通知权限\n· 悬浮窗权限\n· 电池优化白名单\n\n不开放 shell，不修改其他应用或系统文件。")
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
