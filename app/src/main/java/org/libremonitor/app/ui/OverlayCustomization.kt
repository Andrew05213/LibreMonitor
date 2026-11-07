/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.libremonitor.core.OverlayMetric
import org.libremonitor.core.OverlayOptions
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OverlayCustomization(options: OverlayOptions, change: (OverlayOptions) -> Unit,
    preview: (OverlayOptions) -> Unit = change) {
    var panel by rememberSaveable { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf(options) }
    LaunchedEffect(options) { draft = options }
    ListItem(headlineContent = { Text("显示内容") }, leadingContent = { Icon(Icons.Rounded.Dashboard, null) },
        trailingContent = { Row(verticalAlignment = Alignment.CenterVertically) { Text("${options.metrics.size} 项"); Icon(Icons.Rounded.ChevronRight, null) } },
        modifier = Modifier.clickable { panel = "metrics" })
    HorizontalDivider()
    ListItem(headlineContent = { Text("排列顺序") }, leadingContent = { Icon(Icons.Rounded.Reorder, null) },
        trailingContent = { Icon(Icons.Rounded.ChevronRight, null) }, modifier = Modifier.clickable { panel = "order" })
    HorizontalDivider()
    ListItem(headlineContent = { Text("外观") }, leadingContent = { Icon(Icons.Rounded.Tune, null) },
        trailingContent = { Icon(Icons.Rounded.ChevronRight, null) }, modifier = Modifier.clickable { panel = "appearance" })
    HorizontalDivider()
    ListItem(headlineContent = { Text("屏幕边缘吸附") }, leadingContent = { Icon(Icons.Rounded.Layers, null) },
        trailingContent = { Switch(options.edgeSnap, { change(options.copy(edgeSnap = it)) }) })
    if (panel != null) {
        ModalBottomSheet(onDismissRequest = { if (panel == "appearance") change(draft); panel = null }) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(when (panel) { "metrics" -> "显示内容"; "order" -> "排列顺序"; else -> "悬浮窗外观" }, style = MaterialTheme.typography.titleLarge)
                when (panel) {
                    "metrics" -> OverlayMetric.entries.forEach { metric ->
                        val selected = metric in options.metrics
                        val enabled = !selected || options.metrics.size > 1
                        ListItem(headlineContent = { Text(metric.label) }, trailingContent = {
                            Switch(selected, { checked -> change(options.copy(metrics = if (checked) options.metrics + metric else options.metrics - metric)) }, enabled = enabled)
                        })
                    }
                    "order" -> options.metrics.forEachIndexed { index, metric ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(metric.label, Modifier.weight(1f))
                            IconButton(onClick = { val items = options.metrics.toMutableList(); java.util.Collections.swap(items, index, index - 1); change(options.copy(metrics = items)) }, enabled = index > 0) {
                                Icon(Icons.Rounded.ArrowUpward, "上移 ${metric.label}")
                            }
                            IconButton(onClick = { val items = options.metrics.toMutableList(); java.util.Collections.swap(items, index, index + 1); change(options.copy(metrics = items)) }, enabled = index < options.metrics.lastIndex) {
                                Icon(Icons.Rounded.ArrowDownward, "下移 ${metric.label}")
                            }
                        }
                    }
                    else -> {
                        ContinuousSlider("背景不透明度", draft.opacityPercent, 0f..100f, "%",
                            { draft = draft.copy(opacityPercent = it); preview(draft) }, { change(draft) })
                        ContinuousSlider("收起时不透明度", draft.dockOpacityPercent, 0f..100f, "%",
                            { draft = draft.copy(dockOpacityPercent = it); preview(draft) }, { change(draft) })
                        ContinuousSlider("字号", draft.textSizeSp, 12f..24f, "sp",
                            { draft = draft.copy(textSizeSp = it); preview(draft) }, { change(draft) })
                        Text("布局", style = MaterialTheme.typography.titleMedium)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            listOf(1, 2).forEach { columns ->
                                FilterChip(selected = options.columns == columns, onClick = { change(options.copy(columns = columns)) },
                                    label = { Text("$columns 列") }, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
                TextButton(onClick = { draft = OverlayOptions(); change(draft) }) { Text("恢复默认") }
            }
        }
    }
}

@Composable
private fun ContinuousSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, unit: String,
    update: (Float) -> Unit, finish: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(String.format(Locale.ROOT, "%.1f %s", value, unit), color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value, onValueChange = update, onValueChangeFinished = finish, valueRange = range, steps = 0)
    }
}
