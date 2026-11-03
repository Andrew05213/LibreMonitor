/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.core

/**
 * Pure parsing and bounded arithmetic, not evidence of a trustworthy sensor mapping or FCC source.
 * Callers must independently validate provenance, units and battery identity before presenting health.
 * No device access, unit guessing, dual-cell correction or source validation happens here.
 */
object SensorMath {
    private const val MIN_CAPACITY_MAH = 100.0
    private const val MAX_CAPACITY_MAH = 50_000.0
    private val decimalNumber = Regex("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?")
    private val unsignedCounter = Regex("\\+?[0-9]+")
    private val cpuType = Regex(
        "(?:cpu|cpuss)(?:[-_]?[0-9]+)?(?:[-_]therm(?:al)?(?:[-_]?[0-9]+)?)?",
        RegexOption.IGNORE_CASE,
    )
    private val clusterType = Regex(
        "(?:cpu[-_])?cluster(?:[-_]?[0-9]+)?(?:[-_]therm(?:al)?)?",
        RegexOption.IGNORE_CASE,
    )

    /** The explicit flag alone chooses the unit; a small milli-Celsius reading stays small. */
    fun temperatureC(raw: String, milliCelsius: Boolean = true): Double? {
        val text = raw.trim()
        if (!decimalNumber.matches(text)) return null
        val value = text.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return null
        val celsius = if (milliCelsius) value / 1_000.0 else value
        return celsius.takeIf { it in -273.15..180.0 }
    }

    /** Linux capacity input is an integer in microamp-hours, never inferred to be mAh. */
    fun microAmpHoursToMah(raw: String): Double? {
        val microAmpHours = nonNegativeCounter(raw) ?: return null
        val mah = microAmpHours.toDouble() / 1_000.0
        return mah.takeIf { credibleCapacity(it) }
    }

    /**
     * Both inputs must be mAh for the SAME battery object and use a validated FCC/design source.
     * The 100..50,000 mAh and 1..150% bounds only reject implausible arithmetic; they cannot detect
     * all mixed-pack/cell values. A legitimate value above 100% is not clamped or rescaled.
     */
    fun healthPercent(fullMah: Double, designMah: Double): Double? {
        if (!credibleCapacity(fullMah) || !credibleCapacity(designMah)) return null
        val percent = fullMah * 100.0 / designMah
        return percent.takeIf { it.isFinite() && it in 1.0..150.0 }
    }

    /** Only an optionally plus-prefixed ASCII decimal integer is accepted; zero is real data. */
    fun nonNegativeCounter(raw: String): Long? {
        val text = raw.trim()
        if (!unsignedCounter.matches(text)) return null
        return text.toLongOrNull()?.takeIf { it >= 0L }
    }

    /**
     * A deliberately narrow, whole-name CPU/CPUSS/cluster whitelist, not a hardware mapping proof.
     * In particular, an anonymous tsens index does not identify the measured component.
     */
    fun isCpuSensorType(type: String): Boolean {
        val name = type.trim()
        return cpuType.matches(name) || clusterType.matches(name)
    }

    private fun credibleCapacity(mah: Double): Boolean =
        mah.isFinite() && mah in MIN_CAPACITY_MAH..MAX_CAPACITY_MAH
}
