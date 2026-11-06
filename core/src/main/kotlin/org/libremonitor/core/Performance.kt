/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.core

enum class PerformanceMetric(val label: String, val unit: String) {
    CPU("CPU 占用", "%"), GPU("GPU 占用", "%"), MEMORY("内存占用", "%"),
    FPS("FPS", "fps"), FPS_LOW("窗口 1% low", "fps"), FRAME_TIME("窗口帧时间", "ms"),
    POWER("电池端功率估算", "W"), CPU_FREQUENCY("CPU 平均频率", "MHz"), GPU_FREQUENCY("GPU 频率", "MHz"),
    CPU_TEMPERATURE("CPU 区域温度", "°C"), BATTERY_TEMPERATURE("电池温度", "°C"),
    BATTERY_LEVEL("电量", "%"), CURRENT("电池原始电流", "mA"), VOLTAGE("电池电压", "V");

    fun accepts(value: Double): Boolean = value.isFinite() && when (this) {
        CPU, GPU, MEMORY, BATTERY_LEVEL -> value in 0.0..100.0
        FPS, FPS_LOW, FRAME_TIME -> value >= 0.0
        CPU_FREQUENCY, GPU_FREQUENCY, VOLTAGE -> value > 0.0
        CPU_TEMPERATURE, BATTERY_TEMPERATURE -> value in -100.0..200.0
        else -> true
    }
}

data class PerformanceSession(
    val id: Long,
    val name: String,
    val startedEpochMillis: Long,
    val endedEpochMillis: Long? = null,
    val durationMillis: Long = 0,
    val sampleIntervalMs: Long,
    val sampleCount: Int = 0,
    val status: String = RECORDING,
) {
    companion object {
        const val RECORDING = "recording"
        const val COMPLETED = "completed"
        const val INTERRUPTED = "interrupted"
    }
}

/** Readings retain missing-data reasons and provenance in the saved session. */
data class PerformancePoint(
    val elapsedMillis: Long,
    val timestampEpochMillis: Long,
    val readings: Map<PerformanceMetric, Reading<Double>>,
    val target: Reading<String> = Reading.unavailable("SurfaceFlinger", "未读取图层"),
) {
    companion object {
        fun from(snapshot: MonitorSnapshot, sessionStartedElapsedMillis: Long,
            metrics: Set<PerformanceMetric> = PerformanceMetric.entries.toSet()): PerformancePoint {
            fun Reading<out Number>.number(): Reading<Double> =
                Reading(value?.toDouble(), source, status, reason)
            fun missing(source: String, explanation: String, inputs: List<Reading<*>>): Reading<Double> {
                val cause = inputs.firstOrNull { it.status == ReadingStatus.PERMISSION_DENIED }
                    ?: inputs.firstOrNull { it.status == ReadingStatus.ERROR }
                    ?: inputs.firstOrNull { !it.available }
                return Reading.unavailable(source, cause?.reason?.let { "$explanation：$it" } ?: explanation,
                    cause?.status ?: ReadingStatus.UNSUPPORTED)
            }
            val frequencies by lazy { snapshot.cpu.frequenciesMhz.filter { it.available && (it.value ?: 0) > 0 } }
            val cpuFrequency by lazy { if (frequencies.isEmpty()) missing("CPU 频率", "没有可读的核心频率", snapshot.cpu.frequenciesMhz)
                else Reading(frequencies.map { it.value!!.toDouble() }.average(), "已读取的 ${frequencies.size} 个核心频率均值",
                    reason = "仅统计本次可读核心，不是所有核心的固定频率") }
            val total = snapshot.memory.totalBytes
            val available = snapshot.memory.availableBytes
            val memory by lazy { if (total.available && available.available && total.value!! > 0 && available.value!! in 0..total.value)
                Reading((total.value - available.value).toDouble() / total.value * 100.0, "系统总内存减可用内存")
                else missing("系统内存", "总内存或可用内存无效，无法计算占用", listOf(total, available)) }
            val values = with(snapshot) {
                metrics.associateWith { metric -> when (metric) {
                    PerformanceMetric.CPU -> cpu.usagePercent
                    PerformanceMetric.GPU -> gpu.usagePercent
                    PerformanceMetric.MEMORY -> memory
                    PerformanceMetric.FPS -> fps.fps
                    PerformanceMetric.FPS_LOW -> fps.onePercentLow
                    PerformanceMetric.FRAME_TIME -> fps.frameTimeMs
                    PerformanceMetric.POWER -> battery.powerW
                    PerformanceMetric.CPU_FREQUENCY -> cpuFrequency
                    PerformanceMetric.GPU_FREQUENCY -> gpu.frequencyMhz
                    PerformanceMetric.CPU_TEMPERATURE -> cpu.temperatureC
                    PerformanceMetric.BATTERY_TEMPERATURE -> battery.temperatureC
                    PerformanceMetric.BATTERY_LEVEL -> battery.levelPercent.number()
                    PerformanceMetric.CURRENT -> battery.currentMa
                    PerformanceMetric.VOLTAGE -> battery.voltageV
                } }
            }.mapValues { (metric, reading) ->
                if (!reading.available || reading.value?.let(metric::accepts) == true) reading
                else Reading.unavailable(reading.source, "样本无效，未作为 0 记录", ReadingStatus.ERROR)
            }
            return PerformancePoint((snapshot.elapsedRealtimeMillis - sessionStartedElapsedMillis).coerceAtLeast(0),
                snapshot.timestampEpochMillis, values, snapshot.fps.target)
        }
    }
}

data class PerformanceStats(val validCount: Int, val average: Double?, val minimum: Double?, val maximum: Double?)

/** Summary covers every row; the timeline is a bounded preview of the persisted samples. */
data class PerformanceReview(
    val session: PerformanceSession,
    val stats: Map<PerformanceMetric, PerformanceStats>,
    val timeline: List<PerformancePoint>,
    val lastReadings: Map<PerformanceMetric, Reading<Double>>,
    val gapsBefore: Map<Long, Set<PerformanceMetric>> = emptyMap(),
) {
    companion object {
        fun build(session: PerformanceSession, points: Sequence<PerformancePoint>): PerformanceReview =
            Builder(session).apply { points.forEach(::add) }.build()
    }

    class Builder(private val session: PerformanceSession) {
        private val count = IntArray(PerformanceMetric.entries.size)
        private val sum = DoubleArray(count.size)
        private val min = DoubleArray(count.size) { Double.POSITIVE_INFINITY }
        private val max = DoubleArray(count.size) { Double.NEGATIVE_INFINITY }
        private val stride = ((session.sampleCount + 599) / 600).coerceAtLeast(1)
        private val timeline = ArrayList<PerformancePoint>()
        private val gaps = LinkedHashMap<Long, Set<PerformanceMetric>>()
        private val missing = mutableSetOf<PerformanceMetric>()
        private var last: PerformancePoint? = null
        private var index = 0
        fun add(point: PerformancePoint) {
            PerformanceMetric.entries.forEach { metric ->
                val reading = point.readings[metric]
                if (reading?.value?.let { reading.available && metric.accepts(it) } != true) missing.add(metric)
            }
            point.readings.forEach { (metric, reading) ->
                val value = reading.value?.takeIf { reading.available && metric.accepts(it) }
                if (value != null) {
                    val i = metric.ordinal
                    count[i]++; sum[i] += value
                    min[i] = minOf(min[i], value); max[i] = maxOf(max[i], value)
                }
            }
            if (index % stride == 0) {
                timeline.add(point)
                if (missing.isNotEmpty()) gaps[point.elapsedMillis] = missing.toSet()
                missing.clear()
            }
            last = point
            index++
        }
        fun build(): PerformanceReview {
            if (last != null && timeline.lastOrNull() != last) {
                timeline.add(last!!)
                if (missing.isNotEmpty()) gaps[last!!.elapsedMillis] = missing.toSet()
            }
            return PerformanceReview(session, PerformanceMetric.entries.associateWith {
                val i = it.ordinal
                PerformanceStats(count[i], if (count[i] == 0) null else sum[i] / count[i],
                    min[i].takeIf { count[i] > 0 }, max[i].takeIf { count[i] > 0 })
            }, timeline.toList(), last?.readings.orEmpty(), gaps.toMap())
        }
    }
}
