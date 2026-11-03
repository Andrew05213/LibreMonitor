/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.ui

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

private val LightColors = lightColorScheme(
    primary = Color(0xFF006B59),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA5F2DB),
    onPrimaryContainer = Color(0xFF002019),
    secondary = Color(0xFF42645C),
    secondaryContainer = Color(0xFFD6E9E1),
    background = Color(0xFFF3F7F5),
    onBackground = Color(0xFF17231F),
    surface = Color(0xFFFCFEFC),
    onSurface = Color(0xFF17231F),
    surfaceVariant = Color(0xFFE4ECE7),
    onSurfaceVariant = Color(0xFF46534C),
    outline = Color(0xFF737F77),
    outlineVariant = Color(0xFFCAD5CE),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF80D7BD),
    onPrimary = Color(0xFF00382D),
    primaryContainer = Color(0xFF005141),
    onPrimaryContainer = Color(0xFFA5F2DB),
    secondary = Color(0xFFB3CCC2),
    secondaryContainer = Color(0xFF304C42),
    background = Color(0xFF0D1714),
    onBackground = Color(0xFFE0EAE3),
    surface = Color(0xFF16221D),
    onSurface = Color(0xFFE0EAE3),
    surfaceVariant = Color(0xFF293A31),
    onSurfaceVariant = Color(0xFFB9C9BE),
    outline = Color(0xFF869B8B),
    outlineVariant = Color(0xFF3C5143),
)

/** A presentation-only UI: all collection, permission and persistence work belongs to actions. */
@Composable
fun LibreMonitorScreen(state: MonitorUiState, actions: MonitorActions) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var detail by remember { mutableStateOf<MetricDetail?>(null) }
    val tabs = listOf("概览", "电池", "设置")

    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = { SampleStatusBar(state) },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    tabs.forEachIndexed { index, label ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = { NavigationGlyph(index) },
                            label = { Text(label) },
                        )
                    }
                }
            },
        ) { insets ->
            BoxWithConstraints(
                Modifier.fillMaxSize().padding(insets),
                contentAlignment = Alignment.TopCenter,
            ) {
                val columns = if (maxWidth >= 600.dp) 3 else 2
                key(selectedTab) {
                    Column(
                        Modifier
                            .widthIn(max = 840.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        state.error?.let { message -> ErrorBanner(message, actions.onDismissError) }
                        when (selectedTab) {
                            0 -> OverviewPage(state, columns) { detail = it }
                            1 -> BatteryPage(state, actions, columns, { detail = it }) { confirmClear = true }
                            else -> SettingsPage(state, actions) { confirmClear = true }
                        }
                    }
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
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("知道了") }
        }
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
    // A new sample may arrive just after the last tick. Read the clock again on that
    // recomposition instead of incorrectly labeling a newer sample as being in the future.
    return maxOf(tick.longValue, SystemClock.elapsedRealtime())
}

/** The top bar carries freshness only: live, stale, or waiting for a first sample. */
@Composable
private fun SampleStatusBar(state: MonitorUiState) {
    val snapshot = state.snapshot
    val elapsedNow = foregroundElapsedTime()
    val age = snapshot?.elapsedRealtimeMillis?.takeIf { it in 0L..elapsedNow }
        ?.let { elapsedNow - it }
    val stale = snapshot != null && (
        age == null || age > maxOf(15_000L, state.settings.sampleIntervalMs * 3)
        )
    val label = when {
        snapshot == null -> "等待中"
        stale -> "已过期"
        else -> "实时"
    }
    val container = when {
        snapshot == null -> MaterialTheme.colorScheme.surfaceVariant
        stale -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val content = when {
        snapshot == null -> MaterialTheme.colorScheme.onSurfaceVariant
        stale -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = RoundedCornerShape(50), color = container) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(8.dp).background(content, CircleShape))
                    Text(label, style = MaterialTheme.typography.labelLarge, color = content)
                }
            }
        }
    }
}

@Composable
private fun OverviewPage(
    state: MonitorUiState,
    columns: Int,
    onDetail: (MetricDetail) -> Unit,
) {
    val snapshot = state.snapshot
    if (snapshot == null) {
        Section("概览") {
            MetricGrid(
                listOf(
                    pendingTile("CPU 占用"),
                    pendingTile("CPU 温度"),
                    pendingTile("电量"),
                    pendingTile("内存"),
                ),
                columns,
                onDetail,
            )
        }
        return
    }
    val stamp = snapshot.timestampEpochMillis
    val cpu = snapshot.cpu
    Section("概览") {
        MetricGrid(
            listOf(
                tile(
                    "CPU 占用", cpu.usagePercent, validPercent, ::percentQuantity, stamp,
                    note = "/proc/stat 相邻样本时间差分；iowait 计为空闲，首个样本没有使用率",
                ),
                tile(
                    "CPU 温度", cpu.temperatureC, validTemperature, ::temperatureQuantity, stamp,
                    note = "优先采用 HAL 当前 CPU 温度，否则采用 CPU/CPUSS/Cluster 命名的区域传感器最高值；并非验证过的核心结温",
                ),
                tile("电量", snapshot.battery.levelPercent, validBatteryLevel, ::levelQuantity, stamp),
                tile(
                    "内存已用", memoryUsedPercent(snapshot.memory), validPercent, ::percentQuantity, stamp,
                    note = "由内存总量与可用量推算；可用内存包含系统可回收空间，不是应用自身占用",
                ),
            ),
            columns,
            onDetail,
        )
    }
    Section("逐核") {
        val count = maxOf(cpu.perCoreUsagePercent.size, cpu.frequenciesMhz.size, cpu.coreIds.size)
        if (count == 0) {
            MetricGrid(
                listOf(
                    tile(
                        "逐核", Reading.unavailable<Double>("/proc/stat", "系统未返回逐核读数"),
                        { true }, ::percentQuantity, stamp,
                    ),
                ),
                columns,
                onDetail,
            )
        } else {
            MetricGrid((0 until count).map { index -> coreTile(cpu, index, stamp) }, columns, onDetail)
        }
    }
    Section("内存与 Swap") {
        MetricGrid(
            listOf(
                tile("可用内存", snapshot.memory.availableBytes, { it >= 0L }, ::bytesQuantity, stamp),
                tile("内存总计", snapshot.memory.totalBytes, { it > 0L }, ::bytesQuantity, stamp),
                tile(
                    "Swap 已用", swapUsedBytes(snapshot.memory), { it >= 0L }, ::bytesQuantity, stamp,
                    note = "由 Swap 总量与空闲量相减推算，系统未直接提供已用值",
                ),
                tile("Swap 总计", snapshot.memory.swapTotalBytes, { it > 0L }, ::bytesQuantity, stamp),
            ),
            columns,
            onDetail,
        )
    }
    Section("网络") {
        val network = snapshot.network
        MetricGrid(
            listOf(
                tile(
                    "下载", network.downloadBytesPerSecond, nonnegativeDouble, ::rateQuantity, stamp,
                    note = "相邻样本的接收计数差分除以单调时间；计数器重置或首个样本时为 —",
                ),
                tile(
                    "上传", network.uploadBytesPerSecond, nonnegativeDouble, ::rateQuantity, stamp,
                    note = "相邻样本的发送计数差分除以单调时间；计数器重置或首个样本时为 —",
                ),
                tile("累计接收", network.totalReceivedBytes, { it >= 0L }, ::bytesQuantity, stamp),
                tile("累计发送", network.totalSentBytes, { it >= 0L }, ::bytesQuantity, stamp),
            ),
            columns,
            onDetail,
        )
    }
    Section("存储") {
        MetricGrid(
            listOf(
                tile("可用空间", snapshot.storage.availableBytes, { it >= 0L }, ::bytesQuantity, stamp),
                tile(
                    "分区总计", snapshot.storage.totalBytes, { it > 0L }, ::bytesQuantity, stamp,
                    note = "应用所在数据分区，不代表全盘健康检测",
                ),
            ),
            columns,
            onDetail,
        )
    }
    Section("图形") {
        val gpu = snapshot.gpu
        val gpuTiles = mutableListOf(
            tile(
                "GPU 占用", gpu.usagePercent, validPercent, ::percentQuantity, stamp,
                note = "只显示驱动真实上报的 GPU 读数；不推测设备型号",
            ),
        )
        if (gpu.frequencyMhz.available) {
            gpuTiles += tile(
                "GPU 频率", gpu.frequencyMhz, positiveDouble, ::frequencyQuantity, stamp,
                note = "驱动上报的当前频率，不是硬件规格上限",
            )
        }
        if (gpu.temperatureC.available) {
            gpuTiles += tile("GPU 温度", gpu.temperatureC, validTemperature, ::temperatureQuantity, stamp)
        }
        MetricGrid(gpuTiles, columns, onDetail)
    }
}

@Composable
private fun BatteryPage(
    state: MonitorUiState,
    actions: MonitorActions,
    columns: Int,
    onDetail: (MetricDetail) -> Unit,
    requestClear: () -> Unit,
) {
    val snapshot = state.snapshot
    val battery = snapshot?.battery
    val stamp = snapshot?.timestampEpochMillis
    Section("当前电池") {
        if (battery == null) {
            Text(
                "—",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
            MetricGrid(
                listOf(
                    pendingTile("电压"), pendingTile("电流"), pendingTile("功率"), pendingTile("温度"),
                    pendingTile("健康状态"), pendingTile("容量健康"), pendingTile("设计容量"),
                    pendingTile("满充容量"), pendingTile("剩余电荷"), pendingTile("循环次数"),
                    pendingTile("充电源"),
                ),
                columns,
                onDetail,
            )
        } else {
            val levelTile = tile("电量", battery.levelPercent, validBatteryLevel, ::levelQuantity, stamp)
            val level = levelTile.quantity
            Row(
                Modifier.fillMaxWidth()
                    .semantics(mergeDescendants = true) { contentDescription = "当前电量 ${level?.display ?: "不可用"}" }
                    .clickable(role = Role.Button) { onDetail(levelTile.detail) },
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    level?.value ?: "—",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (level == null) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.primary,
                )
                level?.unit?.let { unit ->
                    Text(
                        unit,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    batteryStatusLabel(battery.status),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ReadingMeter(
                battery.levelPercent.validValue(validBatteryLevel)?.div(100f),
                "当前电量 ${level?.display ?: "不可用"}",
            )
            MetricGrid(batteryTiles(battery, stamp), columns, onDetail)
            battery.technology?.let { technology ->
                Text(
                    "电池技术 · ${deviceText(technology)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    Section("电池趋势") {
        BatteryTrend(state.history, state.settings.historyEnabled, state.settings.historyIntervalMs)
    }
    HistoryControls(state, actions, requestClear)
}

private fun batteryTiles(battery: BatterySample, stamp: Long?): List<QuantityTile> = listOf(
    tile(
        "电压", battery.voltageV, positiveDouble, ::voltageQuantity, stamp,
        note = "电池端口电压；来自系统电池状态，不是充电器输出电压",
    ),
    tile(
        "电流", battery.currentMa, finiteNumber, ::currentQuantity, stamp,
        note = "BatteryManager.CURRENT_NOW 原始符号未校准，正负号不能直接判断充放电方向",
    ),
    tile(
        "功率", battery.powerW, finiteNumber, ::powerQuantity, stamp,
        note = "电池端估算：电压 × 电流；不是充电器或处理器功耗，未经设备校准",
    ),
    tile(
        "温度", battery.temperatureC, validTemperature, ::temperatureQuantity, stamp,
        note = "电池温度传感器，不是 CPU 温度",
    ),
    tile(
        "健康状态", battery.healthStatus, { it.isNotBlank() }, { Quantity(it, null) }, stamp,
        note = "GOOD 等状态由系统报告，不表示容量健康百分比",
    ),
    tile(
        "容量健康", battery.healthPercent, validHealthPercent, ::percentQuantity, stamp,
        note = "同一电池、同一单位的满充容量 ÷ 设计容量；无法验证时显示 —",
    ),
    tile(
        "设计容量", battery.designCapacityMah, positiveDouble, ::capacityQuantity, stamp,
        note = "需要可靠的 mAh 来源；没有有效换算条件时显示 —",
    ),
    tile(
        "满充容量", battery.fullChargeCapacityMah, positiveDouble, ::capacityQuantity, stamp,
        note = "与设计容量比较得到容量健康；不可混用 charge 与 energy 单位",
    ),
    tile(
        "剩余电荷", battery.chargeCounterMah, nonnegativeDouble, ::capacityQuantity, stamp,
        note = "CHARGE_COUNTER 剩余电荷计数，不是设计容量或健康度",
    ),
    tile(
        "循环次数", battery.cycleCount, { it >= 0L }, ::cycleQuantity, stamp,
        note = "由电池驱动报告；部分设备不提供此读数",
    ),
    tile(
        "充电源", battery.chargingSource, { it.isNotBlank() }, { Quantity(it, null) }, stamp,
        note = "系统电源节点报告的充电源；类型名称因厂商而异",
    ),
)

@Composable
private fun SettingsPage(state: MonitorUiState, actions: MonitorActions, requestClear: () -> Unit) {
    Section("刷新频率") {
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
    Section("轻量悬浮窗") {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text("显示悬浮监测", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (state.overlayEnabled) "已开启" else "已关闭",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = state.overlayEnabled,
                onCheckedChange = { actions.onOverlayToggle() },
                modifier = Modifier.semantics { contentDescription = "显示悬浮监测" },
            )
        }
    }
    HistoryControls(state, actions, requestClear)
    Section("访问能力") {
        InfoRow("普通模式", "逐项检测")
        InfoRow("Shizuku", "未接入")
        RootControl(state, actions)
    }
    Section("设备") {
        DeviceRows(state.device)
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
    InfoRow("Root 只读", status)
    if (root.message.isNotBlank()) {
        Text(
            root.message.take(160),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    state.permissionGrantMessage?.let {
        Text(it, style = MaterialTheme.typography.bodyMedium)
    }
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
                if (state.permissionGrantRunning) Text("正在授权…")
                else Text("一键授权通知、悬浮窗、电池白名单")
            }
            OutlinedButton(
                onClick = actions.onRootDisable,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("停用只读读取") }
        }
        else -> OutlinedButton(
            onClick = { confirmRequest = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text(if (root.access == RootAccess.DISABLED) "启用只读读取" else "手动重试")
        }
    }
    if (confirmGrant) {
        AlertDialog(
            onDismissRequest = { confirmGrant = false },
            title = { Text("一键授予所需权限？") },
            text = {
                Text(
                    "通过现有 Root 会话仅对本应用执行固定命令：\n" +
                        "· 通知权限\n· 悬浮窗权限\n· 电池优化白名单\n\n" +
                        "不开放 shell，不修改其他应用或系统文件；可随时在系统设置中撤销。",
                )
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
            title = { Text("启用只读 Root 读取？") },
            text = {
                Text(
                    "仅在授权后读取固定白名单的只读节点（CPU 计数、频率、温度、电池容量等）。" +
                        "不写入系统节点、不修改 SELinux、不常驻或开机自启；授权由系统 Root 管理器决定，" +
                        "请求可能超时。",
                )
            },
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
private fun DeviceRows(device: DeviceInfo?) {
    if (device == null) {
        Text(
            "设备信息尚未就绪",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    InfoRow("设备", listOf(deviceText(device.manufacturer), deviceText(device.model)).joinToString(" "))
    InfoRow(
        "系统",
        "Android ${deviceText(device.androidVersion)} · API ${device.sdkInt.takeIf { it > 0 } ?: "—"}",
    )
    InfoRow("架构", device.abis.takeIf { it.isNotEmpty() }?.joinToString(" / ") ?: "—")
    InfoRow("逻辑处理器", device.processorCount.takeIf { it > 0 }?.toString() ?: "—")
    InfoRow(
        "显示",
        if (device.screenWidthPx > 0 && device.screenHeightPx > 0)
            "${device.screenWidthPx} × ${device.screenHeightPx} px" else "—",
    )
    InfoRow(
        "刷新率",
        device.refreshRateHz.takeIf { it.isFinite() && it > 0f }
            ?.let { "${ReadingFormat.number(it.toDouble())} Hz" } ?: "—",
    )
}

@Composable
private fun HistoryControls(state: MonitorUiState, actions: MonitorActions, requestClear: () -> Unit) {
    Section("本地电池记录") {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "保存电池历史",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = state.settings.historyEnabled,
                onCheckedChange = actions.onHistoryEnabledChange,
                modifier = Modifier.semantics { contentDescription = "保存本地电池历史" },
            )
        }
        Text(
            "每 ${ReadingFormat.interval(state.settings.historyIntervalMs)} 一条 · " +
                "保留 ${state.settings.retentionDays} 天 · 当前 ${state.history.size} 条",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = actions.onExport,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text("导出 CSV") }
        OutlinedButton(
            onClick = requestClear,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) { Text("清除全部历史…") }
    }
}

@Composable
private fun ClearHistoryDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("清除全部电池记录？") },
        text = { Text("将永久删除本机保存的全部电池历史，无法撤销；已导出的 CSV 不受影响。") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("确认清除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            content()
        }
    }
}

/** Value plus unit per metric. The sheet keeps provenance; the tile keeps only the reading. */
private data class QuantityTile(
    val label: String,
    val quantity: Quantity?,
    val detail: MetricDetail,
    val subLabel: String? = null,
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
    valid: (T) -> Boolean,
    format: (T) -> Quantity,
    sampledAt: Long?,
    note: String? = null,
    subLabel: String? = null,
): QuantityTile {
    val quantity = reading.validValue(valid)?.let(format)
    return QuantityTile(
        label = label,
        quantity = quantity,
        detail = MetricDetail(
            label = label,
            valueText = quantity?.display,
            status = if (quantity == null) reading.status else ReadingStatus.AVAILABLE,
            source = reading.source,
            reason = if (quantity == null) reading.missingExplanation(valid) else reading.reason?.takeIf { it.isNotBlank() },
            note = note,
            sampledAtEpochMillis = sampledAt,
        ),
        subLabel = subLabel,
    )
}

private fun pendingTile(label: String): QuantityTile = tile(
    label = label,
    reading = Reading.unavailable<Double>("尚未采样", "尚未获得首个有效样本"),
    valid = { true },
    format = ::percentQuantity,
    sampledAt = null,
)

private fun coreTile(cpu: CpuSample, index: Int, stamp: Long?): QuantityTile {
    val coreId = cpu.coreIds.getOrNull(index) ?: index
    val usage = cpu.perCoreUsagePercent.getOrNull(index)
        ?: Reading.unavailable<Double>("逐核使用率", "系统未返回该核心的计数")
    val frequency = cpu.frequenciesMhz.getOrNull(index)?.validValue { it > 0L }
    return tile(
        label = "核心 $coreId",
        reading = usage,
        valid = validPercent,
        format = ::percentQuantity,
        sampledAt = stamp,
        note = "使用率来自 /proc/stat 相邻样本差分，iowait 计为空闲；编号为采集到的逻辑核心 ID",
        subLabel = frequency?.let { "${it} MHz" } ?: "频率 —",
    )
}

private fun memoryUsedPercent(memory: MemorySample): Reading<Double> {
    val total = memory.totalBytes.validValue { it > 0L }
        ?: return Reading.unavailable("内存占用率", "缺少有效的内存总量")
    val available = memory.availableBytes.validValue { it >= 0L && it <= total }
        ?: return Reading.unavailable("内存占用率", "缺少有效的可用内存")
    return Reading(
        (total - available).toDouble() * 100.0 / total.toDouble(),
        "由内存总量与可用量推算",
    )
}

private fun swapUsedBytes(memory: MemorySample): Reading<Long> {
    val total = memory.swapTotalBytes.validValue { it > 0L }
        ?: return Reading.unavailable("/proc/meminfo:SwapUsed", "设备未启用 Swap 或未提供有效总量")
    val free = memory.swapFreeBytes.validValue { it >= 0L && it <= total }
        ?: return Reading.unavailable("/proc/meminfo:SwapUsed", "缺少有效的 Swap 空闲量")
    return Reading(total - free, "/proc/meminfo:SwapTotal − SwapFree（推算）")
}

@Composable
private fun MetricGrid(specs: List<QuantityTile>, columns: Int, onDetail: (MetricDetail) -> Unit) {
    if (specs.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        specs.chunked(columns.coerceAtLeast(1)).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { spec ->
                    MetricTile(spec, onDetail, Modifier.weight(1f))
                }
                repeat(columns - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MetricTile(spec: QuantityTile, onDetail: (MetricDetail) -> Unit, modifier: Modifier = Modifier) {
    val quantity = spec.quantity
    Surface(
        modifier = modifier
            .heightIn(min = 78.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "${spec.label} ${quantity?.display ?: "不可用"}"
            }
            .clickable(role = Role.Button) { onDetail(spec.detail) },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                spec.label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    quantity?.value ?: "—",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (quantity == null) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                quantity?.unit?.let { unit ->
                    Spacer(Modifier.width(4.dp))
                    Text(
                        unit,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 5.dp),
                        maxLines = 1,
                    )
                }
            }
            spec.subLabel?.let { sub ->
                Text(
                    sub,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MetricDetailSheet(detail: MetricDetail, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(detail.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                detail.valueText ?: "—",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (detail.valueText == null) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            DetailRow("状态", readingStatusText(detail))
            DetailRow("来源", detail.source.ifBlank { "未提供来源" })
            detail.reason?.let { DetailRow(if (detail.valueText == null) "缺失原因" else "数据说明", it) }
            detail.note?.let { DetailRow("估算说明", it) }
            DetailRow(
                "更新时间",
                detail.sampledAtEpochMillis?.takeIf { it > 0L }
                    ?.let { ReadingFormat.time(it, true) } ?: "尚未采样",
            )
        }
    }
}

private fun readingStatusText(detail: MetricDetail): String = when {
    detail.valueText != null -> "有效读数"
    detail.status == ReadingStatus.PERMISSION_DENIED -> "系统拒绝访问"
    detail.status == ReadingStatus.ERROR -> "读取异常"
    detail.status == ReadingStatus.AVAILABLE -> "样本无效"
    else -> "暂不可用"
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(76.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.58f),
        )
    }
}

@Composable
private fun NavigationGlyph(index: Int) {
    val color = LocalContentColor.current
    Canvas(Modifier.size(24.dp)) {
        val stroke = 1.8.dp.toPx()
        when (index) {
            0 -> {
                val gap = 3.dp.toPx()
                val cell = (size.width - gap) / 2f
                for (row in 0..1) for (column in 0..1) {
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(column * (cell + gap), row * (cell + gap)),
                        size = Size(cell, cell),
                        cornerRadius = CornerRadius(2.dp.toPx()),
                        style = Stroke(stroke),
                    )
                }
            }
            1 -> {
                drawRoundRect(
                    color, Offset(3.dp.toPx(), 3.dp.toPx()),
                    Size(17.dp.toPx(), 18.dp.toPx()), CornerRadius(3.dp.toPx()),
                    style = Stroke(stroke),
                )
                drawLine(color, Offset(9.dp.toPx(), 0f), Offset(14.dp.toPx(), 0f), stroke)
                drawLine(color, Offset(7.dp.toPx(), 14.dp.toPx()), Offset(16.dp.toPx(), 14.dp.toPx()), stroke)
                drawLine(color, Offset(7.dp.toPx(), 17.dp.toPx()), Offset(16.dp.toPx(), 17.dp.toPx()), stroke)
            }
            else -> {
                listOf(5f, 12f, 19f).forEachIndexed { row, y ->
                    val actualY = y.dp.toPx()
                    drawLine(color, Offset(1.dp.toPx(), actualY), Offset(23.dp.toPx(), actualY), stroke)
                    drawCircle(color, 3.dp.toPx(), Offset((if (row == 1) 16 else 8).dp.toPx(), actualY))
                }
            }
        }
    }
}
