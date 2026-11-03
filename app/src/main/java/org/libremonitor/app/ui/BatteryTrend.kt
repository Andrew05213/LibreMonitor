/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.libremonitor.core.BatteryRecord
import kotlin.math.abs
import kotlin.math.max

private enum class TrendMetric(val label: String) {
    LEVEL("电量"), TEMPERATURE("温度"), POWER("估算功率");

    fun value(record: BatteryRecord): Double? = when (this) {
        LEVEL -> record.levelPercent?.takeIf { it in 0..100 }?.toDouble()
        TEMPERATURE -> record.temperatureC?.takeIf(validTemperature)
        POWER -> record.powerW?.takeIf(finiteNumber)
    }

    fun unitLabel(): String = when (this) {
        LEVEL -> "%"
        TEMPERATURE -> "°C"
        POWER -> "W"
    }

    fun format(value: Double): String = when (this) {
        LEVEL -> "${ReadingFormat.number(value, 0)}%"
        TEMPERATURE -> ReadingFormat.temperature(value)
        POWER -> ReadingFormat.power(value)
    }
}

/** Real stored samples only. Nulls, invalid readings, clock reversals and gaps break the line. */
@Composable
internal fun BatteryTrend(
    records: List<BatteryRecord>,
    historyEnabled: Boolean,
    expectedIntervalMs: Long,
) {
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    val metric = TrendMetric.entries[selectedIndex.coerceIn(0, TrendMetric.entries.lastIndex)]
    val samples = records.takeLast(360)
    val values = samples.map { record ->
        if (record.timestampEpochMillis > 0L) metric.value(record) else null
    }
    val presentValues = values.filterNotNull()
    val validTimes = samples.map { it.timestampEpochMillis }.filter { it > 0L }
    val firstTime = validTimes.minOrNull()
    val lastTime = validTimes.maxOrNull()
    val maxGapMs = expectedIntervalMs.coerceAtLeast(1L).toDouble() * 2.5

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TrendMetric.entries.forEachIndexed { index, item ->
                FilterChip(
                    selected = selectedIndex == index,
                    onClick = { selectedIndex = index },
                    label = { Text(item.label) },
                )
            }
        }

        when {
            samples.isEmpty() -> Text(
                if (historyEnabled) "记录已开启，等待首条记录。"
                else "记录已关闭；开启后显示真实趋势。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            presentValues.isEmpty() -> Text(
                "这些记录中没有可用的${metric.label}读数，未用 0 填补。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            else -> {
                val actualMin = presentValues.minOrNull()!!
                val actualMax = presentValues.maxOrNull()!!
                // Normalize before subtracting: even corrupt but finite extremes cannot overflow
                // the coordinate calculations or send NaN/Infinity into Canvas.
                val scale = if (metric == TrendMetric.LEVEL) 1.0 else
                    max(max(abs(actualMin), abs(actualMax)), 1.0)
                val scaledMin = actualMin / scale
                val scaledMax = actualMax / scale
                val padding = if (metric == TrendMetric.LEVEL) 0.0 else
                    max((scaledMax - scaledMin) * 0.15, max(abs(scaledMax) * 0.03, 0.5 / scale))
                val minimum = if (metric == TrendMetric.LEVEL) 0.0 else scaledMin - padding
                val maximum = if (metric == TrendMetric.LEVEL) 100.0 else scaledMax + padding
                val span = (maximum - minimum).coerceAtLeast(0.01)
                val axisMinimum = (minimum * scale).coerceIn(-Double.MAX_VALUE, Double.MAX_VALUE)
                val axisMaximum = (maximum * scale).coerceIn(-Double.MAX_VALUE, Double.MAX_VALUE)
                val chartColor = MaterialTheme.colorScheme.primary
                val gridColor = MaterialTheme.colorScheme.outlineVariant
                val newestText = values.lastOrNull()?.let(metric::format) ?: "—"
                val description = buildString {
                    append("${metric.label}历史趋势。共 ${samples.size} 条，")
                    append("${presentValues.size} 条有效。最新一条：$newestText。")
                    append("缺失、时间回退或间隔超过预期的 2.5 倍处不连线。")
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "最新 $newestText",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Row(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.width(64.dp).height(160.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(metric.format(axisMaximum), style = MaterialTheme.typography.labelSmall)
                        Text(metric.unitLabel(), style = MaterialTheme.typography.labelSmall)
                        Text(metric.format(axisMinimum), style = MaterialTheme.typography.labelSmall)
                    }
                    Canvas(
                        Modifier.weight(1f).height(160.dp)
                            .semantics { contentDescription = description },
                    ) {
                        val inset = 5.dp.toPx()
                        val plotWidth = (size.width - inset * 2f).coerceAtLeast(1f)
                        val plotHeight = (size.height - inset * 2f).coerceAtLeast(1f)
                        listOf(0f, 0.5f, 1f).forEach { fraction ->
                            val y = inset + plotHeight * fraction
                            drawLine(gridColor, Offset(inset, y), Offset(inset + plotWidth, y), 1.dp.toPx())
                        }
                        val start = firstTime ?: 0L
                        val timeSpan = ((lastTime ?: start).toDouble() - start.toDouble()).coerceAtLeast(1.0)
                        var previous: Offset? = null
                        var previousTime: Long? = null
                        samples.forEachIndexed { index, record ->
                            val value = values[index]
                            if (value == null) {
                                previous = null
                                previousTime = null
                            } else {
                                val x = if (firstTime == lastTime) inset + plotWidth / 2f else
                                    inset + (((record.timestampEpochMillis.toDouble() - start.toDouble()) /
                                        timeSpan).coerceIn(0.0, 1.0) * plotWidth).toFloat()
                                val y = inset + ((1.0 - (value / scale - minimum) / span)
                                    .coerceIn(0.0, 1.0) * plotHeight).toFloat()
                                val point = Offset(x, y)
                                val before = previous
                                val beforeTime = previousTime
                                val gap = if (beforeTime == null) 0.0 else
                                    record.timestampEpochMillis.toDouble() - beforeTime.toDouble()
                                if (before != null && beforeTime != null && gap > 0.0 && gap <= maxGapMs) {
                                    drawLine(chartColor, before, point, 2.dp.toPx(), StrokeCap.Round)
                                }
                                drawCircle(chartColor, 2.5.dp.toPx(), point)
                                previous = point
                                previousTime = record.timestampEpochMillis
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        firstTime?.let { ReadingFormat.time(it, true) } ?: "时间未知",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        lastTime?.let { ReadingFormat.time(it, true) } ?: "时间未知",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "有效 ${presentValues.size} / ${samples.size} 条" +
                        if (metric == TrendMetric.POWER) " · 电池端估算" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun ReadingMeter(fraction: Float?, description: String) {
    if (fraction == null || !fraction.isFinite()) return
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val fillColor = MaterialTheme.colorScheme.primary
    Canvas(
        Modifier.fillMaxWidth().height(6.dp)
            .semantics { contentDescription = description },
    ) {
        val halfHeight = size.height / 2f
        val left = Offset(halfHeight, halfHeight)
        val right = Offset((size.width - halfHeight).coerceAtLeast(halfHeight), halfHeight)
        drawLine(trackColor, left, right, size.height, StrokeCap.Round)
        val progress = fraction.coerceIn(0f, 1f)
        if (progress > 0f) {
            drawLine(
                fillColor,
                left,
                Offset(halfHeight + (right.x - halfHeight) * progress, halfHeight),
                size.height,
                StrokeCap.Round,
            )
        }
    }
}
