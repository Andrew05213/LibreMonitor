/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.data.collectors

import org.libremonitor.core.PrivilegedNodeFrame
import org.libremonitor.core.Reading
import org.libremonitor.core.SensorKind
import org.libremonitor.core.SensorMath
import org.libremonitor.core.ThermalSensor

/** CPU-labelled driver sensors are regional measurements, not validated core junctions. */
internal object AdvancedCpuCollector {
    internal data class Result(val temperature: Reading<Double>, val sensors: List<ThermalSensor>)
    private const val HAL_SOURCE = "thermalservice HAL 当前 CPU 温度（区域传感器，非验证过的核心结温）"
    private val entry = Regex(
        // Android ICU rejects a literal closing brace; escape it explicitly.
        "^Temperature\\{mValue=([+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?),\\s*mType=([0-9]+),\\s*mName=([^,}]+),\\s*mStatus=[^}]+\\}\\z"
    )

    /** Accept only a complete, recognisable HAL current-temperature section. */
    internal fun parseHal(text: String): List<ThermalSensor>? {
        val lines = text.lines()
        val ready = lines.indexOfFirst { it.trim() == "HAL Ready: true" }
        if (ready < 0) return null
        val start = lines.indexOfFirst { it.trim() == "Current temperatures from HAL:" }
        if (start <= ready || lines.count { it.trim() == "HAL Ready: true" } != 1 ||
            lines.count { it.trim() == "Current temperatures from HAL:" } != 1 ||
            lines.subList(ready + 1, start).any { it.trim() == "HAL Ready: false" }) return null
        val sensors = mutableListOf<ThermalSensor>()
        var ended = false
        for (line in lines.drop(start + 1)) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            if (!trimmed.startsWith("Temperature{")) {
                // A subsequent labelled section establishes that the current section ended.
                if (trimmed.endsWith(":")) { ended = true; break }
                return null
            }
            val match = entry.matchEntire(trimmed) ?: return null
            if (match.groupValues[2] != "0") continue
            val value = SensorMath.temperatureC(match.groupValues[1], milliCelsius = false) ?: return null
            val name = match.groupValues[3].trim().takeIf { it.isNotEmpty() } ?: return null
            sensors += ThermalSensor("hal:$name", name, Reading(value, HAL_SOURCE), SensorKind.CPU)
        }
        return sensors.takeIf { ended && it.isNotEmpty() }
    }

    fun sample(frame: PrivilegedNodeFrame?): Result = sample(frame, ::readBounded)

    /** Cache discovery only; every temperature still comes from this sampling cycle. */
    internal class Discovery {
        private var sampledAt: Long? = null
        private var identity: String? = null
        private var types = emptyMap<Int, String>()
        fun types(frame: PrivilegedNodeFrame?, now: Long, rootIdentity: String,
            read: (String, Int) -> Reading<String>): Map<Int, String> {
            val at = sampledAt
            if (identity == rootIdentity && at != null && now - at in 0..29_999) return types
            types = (0..95).mapNotNull { index ->
                val path = "/sys/class/thermal/thermal_zone$index/type"
                val type = frame?.readings?.get(path)?.takeIf { it.available } ?: read(path, 128)
                type.value?.trim()?.takeIf { it.isNotEmpty() }?.let { index to it }
            }.toMap()
            sampledAt = now; identity = rootIdentity
            return types
        }
    }

    fun sample(frame: PrivilegedNodeFrame?, discovery: Discovery, now: Long, identity: String): Result =
        sample(frame, ::readBounded, discovery.types(frame, now, identity, ::readBounded))

    internal fun sample(frame: PrivilegedNodeFrame?, read: (String, Int) -> Reading<String>): Result = sample(frame, read, null)

    internal fun sample(frame: PrivilegedNodeFrame?, read: (String, Int) -> Reading<String>, types: Map<Int, String>?): Result {
        val hal = frame?.readings?.get("thermalservice")?.value?.let(::parseHal)
        val sysfs = (types?.keys ?: (0..95).toSet()).mapNotNull { index ->
            val base = "/sys/class/thermal/thermal_zone$index"
            fun node(suffix: String): Reading<String> {
                val path = "$base/$suffix"
                val root = frame?.readings?.get(path)
                return if (root?.available == true) root else read(path, 128)
            }
            val type = types?.get(index) ?: node("type").value?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val kind = when {
                SensorMath.isCpuSensorType(type) -> SensorKind.CPU
                type.contains("gpu", ignoreCase = true) -> SensorKind.GPU
                type.contains("battery", ignoreCase = true) -> SensorKind.BATTERY
                type.contains("skin", ignoreCase = true) -> SensorKind.SKIN
                else -> SensorKind.OTHER
            }
            val raw = node("temp")
            val temperature = raw.mapReading(
                source = "${raw.source}（sysfs m°C → °C；驱动区域传感器，非验证过的核心结温）",
                invalidReason = "温度格式或范围无效",
            ) { SensorMath.temperatureC(it, milliCelsius = true) }
            ThermalSensor("thermal_zone$index", type, temperature, kind)
        }
        val selected = hal ?: sysfs.filter { it.kind == SensorKind.CPU && it.temperatureC.available }
        val maximum = selected.mapNotNull { it.temperatureC.value }.maxOrNull()
        val source = if (hal != null) HAL_SOURCE else "sysfs 驱动 CPU 传感器最大值（非验证过的核心结温）"
        val temperature = if (maximum == null) unsupported(source, "未找到有效 CPU 温度") else Reading(maximum, source)
        return Result(temperature, hal.orEmpty() + sysfs)
    }
}
