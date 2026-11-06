/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.data.collectors

import org.libremonitor.core.CpuSample
import org.libremonitor.core.CpuStatParser
import org.libremonitor.core.CpuTimes
import org.libremonitor.core.CpuUsage
import org.libremonitor.core.Reading
import org.libremonitor.core.PrivilegedNodeFrame

/** Serial caller only. No proc enumeration and no guesses about thermal-zone identity. */
internal class CpuCollector {
    private var previous: Map<String, CpuTimes> = emptyMap()
    private var previousElapsed: Long? = null
    private var previousSource: String? = null
    private var previousSession: Long? = null
    private var previousRootIdentity: String? = null
    var rootIdentity: String = "disabled"
    private val coreCount = boundedProcessorCount()
    private val thermalDiscovery = AdvancedCpuCollector.Discovery()

    fun resetBaseline() {
        previous = emptyMap()
        previousElapsed = null
        previousSource = null
        previousSession = null
        previousRootIdentity = null
    }

    fun sample(elapsedMillis: Long, frame: PrivilegedNodeFrame? = null, includeDetails: Boolean = true): CpuSample {
        fun parse(reading: Reading<String>) = reading.mapReading {
            CpuStatParser.parse(it).takeIf { times -> times.containsKey("cpu") }
        }
        val privileged = frame?.readings?.get("/proc/stat")?.let(::parse)
        val useRoot = privileged?.available == true
        val parsed = if (useRoot) privileged!! else parse(readBounded("/proc/stat", 262_144))
        val source = if (useRoot) "root" else "basic"
        val session = frame?.sessionId
        val sameBaseline = source == previousSource && session == previousSession && rootIdentity == previousRootIdentity
        val current = parsed.value.orEmpty()
        val before = if (sameBaseline) previous else emptyMap()
        val beforeElapsed = if (sameBaseline) previousElapsed else null
        // Failed samples, a source transition, or a Root session transition rebuilds the baseline.
        previous = current
        previousElapsed = if (parsed.available) elapsedMillis else null
        previousSource = source
        previousSession = session
        previousRootIdentity = rootIdentity

        fun usage(name: String): Reading<Double> {
            val source = "${if (useRoot) "Root" else "基本"} /proc/stat:$name（CPU 时间差分，iowait 计为空闲）"
            if (!parsed.available) return Reading.unavailable(
                source, parsed.reason ?: "CPU 计数不可用", parsed.status,
            )
            val after = current[name] ?: return unsupported(source, "此核心离线、未提供或计数无效")
            val earlier = before[name] ?: return unsupported(source, "首次有效样本，需下一次计数计算使用率")
            if (beforeElapsed == null || elapsedMillis <= beforeElapsed) {
                return unsupported(source, "单调时间间隔无效，等待下一次样本")
            }
            val percent = CpuUsage.percent(earlier, after)
                ?: return unsupported(source, "CPU 计数重置或差分无效，已重建基线")
            return Reading(percent, source)
        }

        val ids = current.keys.asSequence()
            .filter { it != "cpu" }
            .mapNotNull { it.removePrefix("cpu").toIntOrNull() }
            .filter { it in 0..255 }.distinct().sorted().toList()
        if (!includeDetails) return CpuSample(usage("cpu"), emptyList(), emptyList(),
            unsupported("CPU 温度", "悬浮窗模式不采集温度和核心详情"))
        val thermal = isolate({ AdvancedCpuCollector.sample(frame, thermalDiscovery, elapsedMillis, rootIdentity) }, {
            AdvancedCpuCollector.Result(unsupported("CPU 温度", "传感器读取失败"), emptyList())
        })
        return CpuSample(
            usagePercent = usage("cpu"),
            perCoreUsagePercent = ids.map { usage("cpu$it") },
            frequenciesMhz = ids.map { frequency(it, frame) },
            temperatureC = thermal.temperature,
            coreIds = ids,
            sensors = thermal.sensors,
        )
    }

    private fun frequency(index: Int, frame: PrivilegedNodeFrame?): Reading<Long> {
        val path = "/sys/devices/system/cpu/cpu$index/cpufreq/scaling_cur_freq"
        fun parse(input: Reading<String>) = input.mapReading(
            source = "${input.source}（kHz → MHz；驱动报告值，不保证实际瞬时频率）",
            invalidReason = "频率未报告、核心离线或不在有效范围（kHz）",
        ) { text ->
            text.trim().toLongOrNull()?.takeIf { it in 1_000L..20_000_000L }?.div(1_000L)
        }
        val root = frame?.readings?.get(path)?.let(::parse)
        return if (root?.available == true) root else parse(readBounded(path, 128))
    }

    fun failure(error: Exception): CpuSample {
        resetBaseline()
        return CpuSample(
            failedReading("/proc/stat", error),
            List(coreCount) { failedReading("/proc/stat:cpu$it", error) },
            List(coreCount) { failedReading("sysfs cpufreq:cpu$it", error) },
            unsupported("CPU 温度", "未建立可信传感器映射"),
        )
    }
}

internal object MemoryCollector {
    private const val SOURCE = "ActivityManager.MemoryInfo（整机内存，非进程内存）"

    fun sample(context: android.content.Context): org.libremonitor.core.MemorySample {
        val memory = safely(SOURCE) {
            val manager = context.getSystemService(android.app.ActivityManager::class.java)
                ?: return@safely unsupported(SOURCE, "ActivityManager 不可用")
            val info = android.app.ActivityManager.MemoryInfo()
            manager.getMemoryInfo(info)
            Reading(info, SOURCE)
        }
        val total = memory.mapReading { it.totalMem.takeIf { bytes -> bytes > 0L } }
        val available = memory.mapReading { info ->
            info.availMem.takeIf { it >= 0L && (info.totalMem <= 0L || it <= info.totalMem) }
        }
        val swap = readBounded("/proc/meminfo").mapReading {
            org.libremonitor.core.MemInfoParser.parseBytes(it)
        }
        val swapTotal = swap.mapReading(source = "/proc/meminfo:SwapTotal") { it["SwapTotal"] }
        val swapFree = swap.mapReading(source = "/proc/meminfo:SwapFree") { counters ->
            counters["SwapFree"]?.takeIf { free ->
                val totalBytes = counters["SwapTotal"]
                totalBytes == null || free <= totalBytes
            }
        }
        return org.libremonitor.core.MemorySample(total, available, swapTotal, swapFree)
    }

    fun failure(error: Exception) = org.libremonitor.core.MemorySample(
        failedReading<Long>(SOURCE, error), failedReading<Long>(SOURCE, error),
        failedReading<Long>("/proc/meminfo:SwapTotal", error),
        failedReading<Long>("/proc/meminfo:SwapFree", error),
    )
}

internal object StorageCollector {
    private const val SOURCE = "StatFs（应用内部数据分区，不代表应用配额）"

    fun sample(context: android.content.Context): org.libremonitor.core.StorageSample {
        val stats = safely(SOURCE) {
            Reading(android.os.StatFs(context.filesDir.absolutePath), SOURCE)
        }
        val total = stats.mapReading { it.totalBytes.takeIf { bytes -> bytes > 0L } }
        val totalBytes = total.value
        val available = stats.mapReading { fs ->
            fs.availableBytes.takeIf { it >= 0L && (totalBytes == null || it <= totalBytes) }
        }
        return org.libremonitor.core.StorageSample(total, available)
    }

    fun failure(error: Exception) = org.libremonitor.core.StorageSample(
        failedReading<Long>(SOURCE, error), failedReading<Long>(SOURCE, error),
    )
}
