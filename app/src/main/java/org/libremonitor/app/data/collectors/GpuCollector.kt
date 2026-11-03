/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.data.collectors

import org.libremonitor.core.GpuSample
import org.libremonitor.core.PrivilegedNodeFrame
import org.libremonitor.core.Reading

/** KGSL's percentage is reported directly; gpubusy is omitted without a verified counter contract. */
internal object GpuCollector {
    private const val BASE = "/sys/class/kgsl/kgsl-3d0"

    fun sample(frame: PrivilegedNodeFrame?): GpuSample {
        fun read(name: String): Reading<String> {
            val path = "$BASE/$name"
            val root = frame?.readings?.get(path)
            return if (root?.available == true) root else readBounded(path, 256)
        }
        val clock = read("gpuclk")
        val frequency = clock.mapReading(source = "${clock.source}（Hz → MHz；驱动报告值）") {
            it.trim().toLongOrNull()?.takeIf { hz -> hz in 1L..20_000_000_000L }?.div(1_000_000.0)
        }
        val busy = read("gpu_busy_percentage")
        val usage = busy.mapReading(source = "${busy.source}（KGSL 驱动报告百分比）") {
            it.trim().toDoubleOrNull()?.takeIf { value -> value.isFinite() && value in 0.0..100.0 }
        }
        return GpuSample(usagePercent = usage, frequencyMhz = frequency)
    }
}
