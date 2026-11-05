/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.libremonitor.core.OverlayMetric
import org.libremonitor.core.OverlayOptions
import kotlin.math.roundToInt

@Composable
internal fun OverlayCustomization(options: OverlayOptions, change: (OverlayOptions) -> Unit) {
    Text("显示内容与顺序", style = MaterialTheme.typography.titleSmall)
    options.metrics.forEachIndexed { index, metric ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${index + 1}. ${metric.label}", Modifier.weight(1f))
            IconButton(onClick = {
                val values = options.metrics.toMutableList()
                java.util.Collections.swap(values, index, index - 1)
                change(options.copy(metrics = values))
            }, enabled = index > 0) { Icon(Icons.Rounded.ArrowUpward, contentDescription = "上移 ${metric.label}") }
            IconButton(onClick = {
                val values = options.metrics.toMutableList()
                java.util.Collections.swap(values, index, index + 1)
                change(options.copy(metrics = values))
            }, enabled = index < options.metrics.lastIndex) { Icon(Icons.Rounded.ArrowDownward, contentDescription = "下移 ${metric.label}") }
        }
    }
    OverlayMetric.entries.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { metric ->
                val selected = metric in options.metrics
                FilterChip(selected = selected, enabled = !selected || options.metrics.size > 1,
                    onClick = { change(options.copy(metrics = if (selected) options.metrics - metric else options.metrics + metric)) },
                    label = { Text(metric.label) }, modifier = Modifier.weight(1f))
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(1, 2).forEach { columns -> FilterChip(selected = options.columns == columns,
            onClick = { change(options.copy(columns = columns)) }, label = { Text("$columns 列布局") }) }
    }
    Text("背景不透明度 ${options.opacityPercent}%")
    Slider(value = options.opacityPercent.toFloat(), onValueChange = { change(options.copy(opacityPercent = it.roundToInt())) },
        valueRange = 30f..100f, steps = 13)
    Text("数字大小 ${options.textSizeSp} sp")
    Slider(value = options.textSizeSp.toFloat(), onValueChange = { change(options.copy(textSizeSp = it.roundToInt())) },
        valueRange = 12f..24f, steps = 11)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("拖至边缘吸附收起", Modifier.weight(1f))
        Switch(checked = options.edgeSnap, onCheckedChange = { change(options.copy(edgeSnap = it)) })
    }
    Text("靠近左右边缘松手后收为半透明箭头；点击展开，长按展开窗的拖动柄可再次收起。频率、温度等详细字段可能增加采集耗时。",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    TextButton(onClick = { change(OverlayOptions()) }) { Text("恢复默认悬浮窗") }
}
