/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.libremonitor.app.MonitorActions
import org.libremonitor.app.MonitorUiState
import org.libremonitor.core.PerformanceMetric
import org.libremonitor.core.PerformanceReview
import org.libremonitor.core.PerformanceSession
import java.util.Locale

@Composable
internal fun PerformancePage(state: MonitorUiState, actions: MonitorActions) {
    var name by rememberSaveable { mutableStateOf("") }
    val active = state.activePerformance
    PerformanceCard {
        Text("性能记录", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (active == null) {
            OutlinedTextField(name, { name = it.take(80) }, label = { Text("游戏 / 场次名称（选填）") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
        } else {
            Text(active.name, fontWeight = FontWeight.SemiBold)
            Text("● 记录中 · ${durationLabel(active.durationMillis)} · ${active.sampleCount} 个样本",
                color = MaterialTheme.colorScheme.primary)
        }
        Button(
            onClick = { if (active == null) actions.onPerformanceStart(name) else actions.onPerformanceStop() },
            enabled = state.performanceReady && !state.performanceBusy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(if (active == null) Icons.Rounded.PlayArrow else Icons.Rounded.Stop, contentDescription = null)
            Text(if (state.performanceBusy) "正在准备…" else if (active == null) "开始性能记录" else "结束并保存")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("游戏悬浮窗", fontWeight = FontWeight.Medium)
            }
            Switch(checked = state.overlayEnabled, onCheckedChange = { actions.onOverlayToggle() })
        }
    }
    if (state.performanceReviewLoading) {
        Text("正在读取性能记录…", color = MaterialTheme.colorScheme.primary)
    }
    state.performanceReview?.let { PerformanceReport(it) }
    PerformanceCard {
        Text("最近记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        val sessions = state.performanceSessions.filter { it.id != active?.id }
        if (sessions.isEmpty()) Text("还没有已保存的场次。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        sessions.forEach { session ->
            Card(onClick = { actions.onPerformanceReview(session.id) }, modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(session.name, fontWeight = FontWeight.SemiBold)
                    Text("${ReadingFormat.time(session.startedEpochMillis, withDate = true)} · ${durationLabel(session.durationMillis)} · ${session.sampleCount} 个样本",
                        style = MaterialTheme.typography.bodySmall)
                    if (session.status == PerformanceSession.INTERRUPTED) {
                        Text("记录曾中断 · 可回看已保存样本", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

    }
}

@Composable
private fun PerformanceReport(review: PerformanceReview) {
    var metricName by rememberSaveable(review.session.id) { mutableStateOf(PerformanceMetric.FPS.name) }
    val metric = PerformanceMetric.valueOf(metricName)
    var showDetails by rememberSaveable(review.session.id) { mutableStateOf(false) }
    PerformanceCard {
        Text(review.session.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("${durationLabel(review.session.durationMillis)} · ${review.session.sampleCount} 个样本",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            listOf(PerformanceMetric.FPS, PerformanceMetric.POWER).forEach { key ->
                Column(Modifier.weight(1f)) {
                    Text(if (key == PerformanceMetric.FPS) "FPS 样本均值" else "平均电池端功率",
                        style = MaterialTheme.typography.labelMedium)
                    Text(if (key == PerformanceMetric.FPS && !review.fpsSummaryAvailable) "样本不足" else formatPerformance(review.stats[key]?.average, key), style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        HorizontalDivider()
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val ordered = listOf(PerformanceMetric.FPS, PerformanceMetric.CPU, PerformanceMetric.GPU, PerformanceMetric.MEMORY) +
                PerformanceMetric.entries.filter { it !in listOf(PerformanceMetric.FPS, PerformanceMetric.CPU, PerformanceMetric.GPU, PerformanceMetric.MEMORY) }
            ordered.forEach { key ->
                FilterChip(selected = key == metric, onClick = { metricName = key.name }, label = { Text(key.label) })
            }
        }
        val stats = review.stats[metric]
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("样本均值" to stats?.average, "最低" to stats?.minimum, "最高" to stats?.maximum).forEach { (label, value) ->
                Column {
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatPerformance(value, metric), fontWeight = FontWeight.SemiBold)
                }
            }
        }
        PerformanceChart(review, metric)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("00:00", style = MaterialTheme.typography.labelSmall)
            Text(durationLabel(review.session.durationMillis), style = MaterialTheme.typography.labelSmall)
        }
        Text(if (metric == PerformanceMetric.FPS) "有效 ${stats?.validCount ?: 0} / ${review.fpsAttemptCount} 次帧率查询"
            else "有效 ${stats?.validCount ?: 0} / ${review.session.sampleCount} 个样本")
        TextButton(onClick = { showDetails = true }) { Text("数据详情") }
        if (showDetails) AlertDialog(onDismissRequest = { showDetails = false }, title = { Text(metric.label) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                review.lastReadings[metric]?.let { Text(it.source); it.reason?.let { reason -> Text(reason) } }
                Text("电池端功率保留原始方向。FPS 为最近帧窗口，沿用值不重复计入统计。")
                if (!review.fpsSummaryAvailable) Text("FPS 有效样本少于 10 个或成功率不足 50%，主摘要显示样本不足。")
            }
        }, confirmButton = { TextButton(onClick = { showDetails = false }) { Text("关闭") } })
    }
}

@Composable
private fun PerformanceChart(review: PerformanceReview, metric: PerformanceMetric) {
    val color = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val stats = review.stats[metric]
    val min = stats?.minimum
    val max = stats?.maximum
    if (min == null || max == null) {
        Text("此项没有可用数据。", modifier = Modifier.padding(vertical = 28.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Canvas(Modifier.fillMaxWidth().height(150.dp).semantics {
        contentDescription = "${metric.label}随时间变化；最低 $min，最高 $max ${metric.unit}"
    }) {
        repeat(4) { index ->
            val y = size.height * index / 3f
            drawLine(grid, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        }
        val duration = maxOf(1L, review.session.durationMillis, review.timeline.lastOrNull()?.elapsedMillis ?: 0L)
        val spread = (max - min).takeIf { it > 0 } ?: maxOf(1.0, kotlin.math.abs(max) * 0.1)
        val floor = if (max == min) min - spread / 2 else min
        var previous: Offset? = null
        var previousTime = 0L
        val previewStride = ((review.session.sampleCount + 599) / 600).coerceAtLeast(1)
        val gapLimit = maxOf(2_000L, review.session.sampleIntervalMs * previewStride * 3)
        review.timeline.forEach { point ->
            if (metric in review.gapsBefore[point.elapsedMillis].orEmpty()) previous = null
            val reading = point.readings[metric]
            val value = reading?.value?.takeIf { reading.available && metric.accepts(it) }
            if (value == null) { previous = null; return@forEach }
            val next = Offset((point.elapsedMillis.toDouble() / duration * size.width).toFloat(),
                size.height * (0.92f - ((value - floor) / spread).toFloat() * 0.84f))
            if (previous != null && point.elapsedMillis - previousTime <= gapLimit) {
                drawLine(color, previous!!, next, 2.dp.toPx(), StrokeCap.Round)
            } else drawCircle(color, 2.dp.toPx(), next)
            previous = next
            previousTime = point.elapsedMillis
        }
    }
}

private fun formatPerformance(value: Double?, metric: PerformanceMetric): String =
    value?.let { String.format(Locale.getDefault(), "%.1f %s", it, metric.unit) } ?: "—"

internal fun durationLabel(millis: Long): String {
    val seconds = millis.coerceAtLeast(0) / 1_000
    return if (seconds >= 3_600) String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3_600, seconds / 60 % 60, seconds % 60)
        else String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60)
}

@Composable
private fun PerformanceCard(content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
