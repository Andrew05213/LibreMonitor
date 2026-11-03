/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.core

import java.io.Writer

/**
 * [components] are the first eight /proc/stat columns (user..steal) in order, kept so a per-counter
 * reset that is masked by aggregate growth stays detectable. The default keeps two-Long callers
 * source compatible; an empty list means "no component evidence".
 */
data class CpuTimes(val total: Long, val idle: Long, val components: List<Long> = emptyList())

object CpuStatParser {
    private val whitespace = Regex("\\s+")
    private val counterName = Regex("cpu(?:[0-9]+)?")

    /**
     * guest/guest_nice are already included in user/nice and are NOT summed again.
     * A repeated name is ambiguous (not silently overwritten) and stays absent; the same applies to
     * a row that overflows arithmetic. Missing entries are controlled unavailability, never zero.
     */
    fun parse(text: String): Map<String, CpuTimes> {
        val parsed = LinkedHashMap<String, CpuTimes>()
        val seen = HashSet<String>()
        val ambiguous = HashSet<String>()
        text.lineSequence().forEach { line ->
            val fields = line.trim().split(whitespace)
            val name = fields.firstOrNull() ?: return@forEach
            if (!counterName.matches(name)) return@forEach
            if (!seen.add(name)) {
                ambiguous.add(name)
                return@forEach
            }
            if (fields.size < 5) return@forEach
            val counters = fields.drop(1).take(8).map { it.toLongOrNull() }
            if (counters.any { it == null || it < 0 }) return@forEach
            val values = counters.filterNotNull()
            val total = try { values.fold(0L, Math::addExact) }
                catch (_: ArithmeticException) { return@forEach }
            val idle = try { Math.addExact(values[3], values.getOrElse(4) { 0L }) }
                catch (_: ArithmeticException) { return@forEach }
            if (total < idle) return@forEach
            parsed[name] = CpuTimes(total, idle, values)
        }
        if (ambiguous.isEmpty()) return parsed
        return parsed.filterKeys { it !in ambiguous }
    }
}

object CpuUsage {
    /** Whole-device or per-core 0..100%; iowait is treated as idle. */
    fun percent(before: CpuTimes, after: CpuTimes): Double? {
        if (before.total < 0L || before.idle < 0L || before.idle > before.total ||
            after.total < 0L || after.idle < 0L || after.idle > after.total) return null
        if (after.total < before.total || after.idle < before.idle) return null
        if (!componentsAreMonotonic(before.components, after.components)) return null
        val total = after.total - before.total
        val idle = after.idle - before.idle
        if (total <= 0 || idle > total) return null
        return (total - idle).toDouble() * 100.0 / total
    }

    /**
     * A per-component drop (including an iowait drop) can hide behind growing totals; it is not
     * proof of a reboot, so there is simply no value. Only compare when both sides carry component
     * evidence: one empty side keeps the legacy aggregate checks.
     */
    private fun componentsAreMonotonic(before: List<Long>, after: List<Long>): Boolean {
        if (before.isEmpty() || after.isEmpty()) return true
        if (before.size != after.size) return false
        return before.indices.all { index ->
            before[index] >= 0L && after[index] >= before[index]
        }
    }
}

object CounterRate {
    /** The caller supplies monotonic elapsed time. Counter resets have no rate. */
    fun bytesPerSecond(before: Long, after: Long, elapsedMillis: Long): Double? {
        if (before < 0L || after < before || elapsedMillis <= 0L) return null
        return (after - before).toDouble() * 1_000.0 / elapsedMillis
    }
}

object MemInfoParser {
    fun parseBytes(text: String): Map<String, Long> = buildMap {
        text.lineSequence().forEach { line ->
            val separator = line.indexOf(':')
            if (separator <= 0) return@forEach
            val fields = line.substring(separator + 1).trim().split(Regex("\\s+"))
            val number = fields.firstOrNull()?.toLongOrNull() ?: return@forEach
            if (number < 0L) return@forEach
            val multiplier = when (fields.getOrNull(1)) {
                "kB" -> 1_024L
                "B", null -> 1L
                else -> return@forEach
            }
            try { put(line.substring(0, separator), Math.multiplyExact(number, multiplier)) }
            catch (_: ArithmeticException) { /* Invalid counters remain absent. */ }
        }
    }
}

object BatteryCsv {
    private fun cell(value: Any?): String {
        val text = when (value) {
            is Double -> if (value.isFinite()) value.toString() else ""
            is Float -> if (value.isFinite()) value.toString() else ""
            else -> value?.toString().orEmpty()
        }
        // Guard formulas even after leading whitespace; signed numeric measurements stay numeric.
        val candidate = text.trimStart(' ', '\t', '\r', '\n')
        val safe = if (value is String && candidate.firstOrNull() in listOf('=', '+', '-', '@'))
            "'$text" else text
        return if (safe.any { it == ',' || it == '"' || it == '\r' || it == '\n' })
            "\"${safe.replace("\"", "\"\"")}\"" else safe
    }

    fun writeHeader(writer: Writer) {
        writer.write("timestamp_epoch_ms,level_percent,temperature_c,voltage_v,current_ma_raw,power_w_estimated,status\r\n")
    }

    fun writeRecord(writer: Writer, record: BatteryRecord) {
        writer.write(listOf(record.timestampEpochMillis, record.levelPercent, record.temperatureC,
            record.voltageV, record.currentMa, record.powerW, record.status).joinToString(",", transform = ::cell))
        writer.write("\r\n")
    }
}
