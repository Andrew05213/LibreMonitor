/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.core

enum class OverlayMetric(val label: String, val performance: PerformanceMetric? = null) {
    CPU("CPU", PerformanceMetric.CPU), GPU("GPU", PerformanceMetric.GPU), MEMORY("内存", PerformanceMetric.MEMORY),
    FPS("FPS", PerformanceMetric.FPS), POWER("电池端功率", PerformanceMetric.POWER),
    CPU_FREQUENCY("CPU 频率", PerformanceMetric.CPU_FREQUENCY), GPU_FREQUENCY("GPU 频率", PerformanceMetric.GPU_FREQUENCY),
    CPU_TEMPERATURE("CPU 温度", PerformanceMetric.CPU_TEMPERATURE), BATTERY_TEMPERATURE("电池温度", PerformanceMetric.BATTERY_TEMPERATURE),
    BATTERY_LEVEL("电量", PerformanceMetric.BATTERY_LEVEL), DOWNLOAD("下载"), UPLOAD("上传");
}

data class OverlayOptions(
    val metrics: List<OverlayMetric> = listOf(OverlayMetric.CPU, OverlayMetric.GPU, OverlayMetric.MEMORY, OverlayMetric.FPS),
    val columns: Int = 2,
    val opacityPercent: Int = 85,
    val textSizeSp: Int = 18,
    val edgeSnap: Boolean = true,
) {
    init {
        require(metrics.isNotEmpty() && metrics.size <= OverlayMetric.entries.size && metrics.distinct() == metrics)
        require(columns in 1..2 && opacityPercent in 30..100 && textSizeSp in 12..24)
    }
    val needsHardwareDetails: Boolean get() = metrics.any { it in listOf(OverlayMetric.CPU_FREQUENCY,
        OverlayMetric.CPU_TEMPERATURE, OverlayMetric.BATTERY_TEMPERATURE, OverlayMetric.POWER) }
    val needsNetwork: Boolean get() = OverlayMetric.DOWNLOAD in metrics || OverlayMetric.UPLOAD in metrics

    companion object {
        fun decode(values: Map<String, *>): OverlayOptions {
            val defaults = OverlayOptions()
            val selected = (values["overlay_metrics"] as? String)?.split(',')?.mapNotNull { key ->
                OverlayMetric.entries.firstOrNull { it.name == key }
            }?.distinct()?.takeIf { it.isNotEmpty() } ?: defaults.metrics
            return OverlayOptions(selected, (values["overlay_columns"] as? Int ?: 2).coerceIn(1, 2),
                (values["overlay_opacity"] as? Int ?: 85).coerceIn(30, 100),
                (values["overlay_text_size"] as? Int ?: 18).coerceIn(12, 24),
                values["overlay_edge_snap"] as? Boolean ?: true)
        }
    }
}

enum class DockEdge { NONE, LEFT, RIGHT }
object OverlayDocking {
    fun edge(x: Int, width: Int, screenWidth: Int, threshold: Int): DockEdge = when {
        x <= threshold -> DockEdge.LEFT
        x + width >= screenWidth - threshold -> DockEdge.RIGHT
        else -> DockEdge.NONE
    }
    fun clampY(y: Int, height: Int, screenHeight: Int): Int = y.coerceIn(0, (screenHeight - height).coerceAtLeast(0))
}
