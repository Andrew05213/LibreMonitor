/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.data.collectors

import android.os.SystemClock
import org.libremonitor.core.GpuSample
import org.libremonitor.core.PrivilegedNodeFrame
import org.libremonitor.core.Reading
import org.libremonitor.core.ReadingStatus

/** KGSL reports a percentage or a busy/total pair for its most recent driver window. */
internal object GpuCollector {
    private const val BASE = "/sys/class/kgsl/kgsl-3d0"
    private val percentage = Regex("([0-9]+(?:\\.[0-9]+)?)\\s*%?")
    private val counters = Regex("([0-9]+)\\s+([0-9]+)")

    fun sample(frame: PrivilegedNodeFrame?): GpuSample = sample(frame, SystemClock.elapsedRealtime(), ::readBounded)

    internal fun sample(
        frame: PrivilegedNodeFrame?,
        nowElapsedMillis: Long,
        readNode: (String, Int) -> Reading<String>,
    ): GpuSample {
        val freshFrame = frame?.takeIf {
            nowElapsedMillis >= it.sampledElapsedRealtimeMs && nowElapsedMillis - it.sampledElapsedRealtimeMs <= 2_000L
        }
        fun read(name: String): Reading<String> {
            val path = "$BASE/$name"
            val root = freshFrame?.readings?.get(path)
            if (root?.available == true) return root.copy(source = "Root ${root.source}")
            val basic = readNode(path, 256)
            return if (!basic.available && root?.status == ReadingStatus.ERROR) root else basic
        }
        val clock = read("gpuclk")
        val frequency = clock.mapReading(source = "${clock.source}（Hz → MHz；驱动报告值）") {
            it.trim().toLongOrNull()?.takeIf { hz -> hz in 1L..20_000_000_000L }?.div(1_000_000.0)
        }
        val busy = read("gpu_busy_percentage")
        val direct = busy.mapReading(source = "${busy.source}（KGSL 驱动报告百分比）",
            invalidReason = "GPU 百分比格式无效；应为 0..100 的数字，可带 %") {
            percentage.matchEntire(it.trim())?.groupValues?.get(1)?.toDoubleOrNull()
                ?.takeIf { value -> value.isFinite() && value in 0.0..100.0 }
        }
        val usage = if (direct.available) direct else {
            val pair = read("gpubusy")
            val ratio = pair.mapReading(source = "${pair.source}（KGSL 最近窗口 busy/total × 100）",
                invalidReason = "gpubusy 需提供有效的 busy total：0 ≤ busy ≤ total，且 total > 0；没有统计窗口不当作 0%") {
                val match = counters.matchEntire(it.trim())
                val active = match?.groupValues?.get(1)?.toLongOrNull()
                val total = match?.groupValues?.get(2)?.toLongOrNull()
                if (active == null || total == null || total <= 0 || active > total) null
                else active.toDouble() / total.toDouble() * 100.0
            }
            if (ratio.available) ratio.copy(reason = "百分比节点不可用；使用驱动最近统计窗口，非开机累计计数差分或应用专属占用")
            else {
                val status = listOf(direct.status, ratio.status).let { statuses ->
                    when {
                        ReadingStatus.PERMISSION_DENIED in statuses -> ReadingStatus.PERMISSION_DENIED
                        ReadingStatus.ERROR in statuses -> ReadingStatus.ERROR
                        else -> ReadingStatus.UNSUPPORTED
                    }
                }
                Reading.unavailable("$BASE（KGSL GPU 占用）",
                    "gpu_busy_percentage：${direct.reason}；gpubusy：${ratio.reason}。" +
                        if (status == ReadingStatus.PERMISSION_DENIED) "可在应用前台启用 Root 后重试；Root 也不保证节点可读。"
                        else "此实现仅支持已核实的 KGSL 节点；其他 GPU 驱动需适配。", status)
            }
        }
        return GpuSample(usagePercent = usage, frequencyMhz = frequency)
    }
}
