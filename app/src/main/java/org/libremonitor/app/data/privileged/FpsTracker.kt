/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.data.privileged

import org.libremonitor.core.FpsSample
import org.libremonitor.core.Reading
import kotlin.math.ceil

/** One tracker per Root session. SurfaceFlinger timestamps use CLOCK_MONOTONIC, not elapsedRealtime. */
internal class FpsTracker {
    private var previousPackage: String? = null
    private var latest = emptyMap<String, Long>()

    @Synchronized fun reset() { previousPackage = null; latest = emptyMap() }

    @Synchronized fun sample(capture: FpsProtocol.Capture, nowNanos: Long): FpsSample {
        capture.failure?.let { return unavailable(it + (capture.packageName?.let { pkg -> "（前台应用：$pkg）" } ?: "")) }
        if (capture.packageName != previousPackage) { latest = emptyMap(); previousPackage = capture.packageName }
        val previous = latest
        val fresh = capture.layers.mapNotNull { layer ->
            val times = layer.timestamps.filter { it <= nowNanos && it >= nowNanos - WINDOW_NS }
            val last = times.lastOrNull() ?: return@mapNotNull null
            if (nowNanos - last > MAX_AGE_NS || last <= (previous[layer.name] ?: 0L) || times.size < 3) null
            else layer to times
        }
        // Keep only current candidates: a bounded map, including non-advancing layers.
        latest = capture.layers.mapNotNull { l -> l.timestamps.lastOrNull { it <= nowNanos }?.let { l.name to it } }.toMap()
        val selected = fresh.sortedWith(compareByDescending<Pair<FpsProtocol.Layer, List<Long>>> { it.first.name.contains("SurfaceView") }
            .thenByDescending { it.second.last() }.thenByDescending { it.second.size }).firstOrNull()
            ?: return unavailable(capture.layers.firstNotNullOfOrNull { it.failure }
                ?: "没有新的有效帧；画面可能静止、图层已失效或帧时间已过期", capture.layers.firstOrNull()?.name)
        val (layer, times) = selected
        // Long positive intervals are REAL stalls. Do not drop them at the old one-second cutoff.
        val intervals = times.zipWithNext { a, b -> b - a }.filter { it > 0L }
        if (intervals.size < 2) return unavailable("有效帧间隔不足", layer.name)
        val mean = intervals.average()
        val source = "SurfaceFlinger --latency (${layer.name})"
        val note = "最近可用 ${intervals.size} 个帧间隔，窗口最多 10 秒；非整段游戏统计"
        val low: Reading<Double> = if (intervals.size < 100) Reading.unavailable(source, "1% low 至少需要 100 个有效帧间隔；当前 ${intervals.size} 个")
        else {
            val count = ceil(intervals.size * 0.01).toInt()
            Reading(1_000_000_000.0 / intervals.sortedDescending().take(count).average(), source,
                reason = "$note；最慢 1% 帧间隔均值的倒数")
        }
        return FpsSample(Reading(1_000_000_000.0 / mean, source, reason = note), low,
            Reading(mean / 1_000_000.0, source, reason = note), Reading(layer.name, source))
    }

    companion object {
        private const val WINDOW_NS = 10_000_000_000L
        private const val MAX_AGE_NS = 3_000_000_000L
        fun unavailable(reason: String, target: String? = null): FpsSample = FpsSample(
            Reading.unavailable("SurfaceFlinger", reason), Reading.unavailable("SurfaceFlinger", reason),
            Reading.unavailable("SurfaceFlinger", reason), target?.let { Reading(it, "SurfaceFlinger") }
                ?: Reading.unavailable("SurfaceFlinger", "未定位到前台图层"))
    }
}
