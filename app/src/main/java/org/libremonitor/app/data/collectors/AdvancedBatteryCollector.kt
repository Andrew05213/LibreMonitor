/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.data.collectors

import android.os.SystemClock
import org.libremonitor.core.BatterySample
import org.libremonitor.core.PrivilegedNodeFrame
import org.libremonitor.core.Reading
import org.libremonitor.core.SensorMath

/**
 * Read-only capacity/cycle/health enrichment from the fixed power_supply nodes only.
 *
 * This is not a privileged backend. A frame from RootReader is used only while it is fresh;
 * otherwise the exact same fixed paths are read with bounded ordinary-permission reads.
 * A stale frame is never reused. No node is written, no process is spawned, and no value is
 * inferred from SoC, remaining charge, voltage or a dual-cell multiplier.
 */
internal object AdvancedBatteryCollector {
    private const val NODE_MAX_BYTES = 256
    /** No sampling interval is part of this interface, so the contract fixes the window. */
    private const val FRAME_MAX_AGE_MS = 2_000L
    private val SUPPLY_DIRS = listOf("battery", "bms")
    private const val HEALTH_PERCENT_SOURCE = "电池健康百分比（FCC/设计容量）"
    private const val CAPACITY_DESIGN_SOURCE = "power_supply:charge_full_design（µAh → mAh）"
    private const val CAPACITY_FULL_SOURCE = "power_supply:charge_full（µAh → mAh）"
    private const val CYCLE_SOURCE = "power_supply:cycle_count"
    private const val HEALTH_STATUS_SOURCE = "power_supply:health"

    /**
     * Public entry point used by the shared sampler. Two arguments match the v0.2 contract;
     * the injected clock/read overload below exists so unit tests never touch a real device.
     */
    fun enrich(base: BatterySample, frame: PrivilegedNodeFrame?): BatterySample =
        enrich(base, frame, SystemClock.elapsedRealtime(), ::readBounded)

    internal fun enrich(
        base: BatterySample,
        frame: PrivilegedNodeFrame?,
        nowElapsedRealtimeMs: Long,
        read: (String, Int) -> Reading<String>,
    ): BatterySample {
        val usableFrame = frame?.takeIf { isFrameFresh(it, nowElapsedRealtimeMs) }
        val staleNote =
            if (frame != null && usableFrame == null) "；特权帧超过 ${FRAME_MAX_AGE_MS}ms，未沿用旧值" else ""

        fun readNode(dir: String, key: String): Reading<String> {
            val path = nodePath(dir, key)
            val reading = if (usableFrame != null) {
                usableFrame.readings[path]
                    ?: Reading.unavailable(path, "只读特权帧未包含此节点")
            } else {
                safely(path) { read(path, NODE_MAX_BYTES) }
            }
            if (reading.available) return reading
            return Reading.unavailable(
                reading.source,
                (reading.reason ?: "节点不可用") + staleNote,
                reading.status,
            )
        }

        val typeReadings = SUPPLY_DIRS.map { dir -> dir to readNode(dir, "type") }
        // Identity is checked before any capacity field is read. A path name alone is not proof,
        // and bms values are never combined with battery values.
        val supplies = typeReadings.mapNotNull { (dir, type) ->
            val text = type.value?.trim()
            if (text.isNullOrEmpty() || !text.equals("Battery", ignoreCase = true)) null
            else readSupply(dir) { keyDir, key -> readNode(keyDir, key) }
        }

        var best: Supply? = null
        var bestScore = Int.MIN_VALUE
        for (supply in supplies) {
            val score = score(supply)
            if (score > bestScore) {
                best = supply
                bestScore = score
            }
        }
        val primary = best

        val typeSummary = typeReadings.joinToString("；") { (dir, reading) ->
            "$dir: ${describeType(reading)}"
        }
        val noBattery = "未找到 type=Battery 的供电对象（$typeSummary）"

        // Health percentage never comes from status=GOOD, SoC, charge_counter or a guessed cell count.
        val chargeHealth = primary?.let(::chargeHealthPercent)
        val energyHealth = if (chargeHealth == null) primary?.let(::energyHealthPercent) else null
        val capacityHealth = chargeHealth ?: energyHealth
        val healthPercent: Reading<Double> = when {
            capacityHealth != null && capacityHealth.available -> capacityHealth
            base.healthPercent.available -> base.healthPercent
            capacityHealth != null -> capacityHealth
            primary != null -> unsupported(HEALTH_PERCENT_SOURCE, "缺少同一电池、同单位的满充与设计容量")
            else -> unsupported(HEALTH_PERCENT_SOURCE, noBattery)
        }

        // Vendor state_of_health is not a verified same-unit capacity ratio.
        val designCapacity = prefer(
            primary?.chargeDesign, base.designCapacityMah, CAPACITY_DESIGN_SOURCE, noBattery,
        )
        val fullCapacity = prefer(
            primary?.chargeFull, base.fullChargeCapacityMah, CAPACITY_FULL_SOURCE, noBattery,
        )
        val cycleCount = prefer(primary?.cycle, base.cycleCount, CYCLE_SOURCE, noBattery)
        val healthStatus: Reading<String> = base.healthStatus.takeIf { it.available }
            ?: primary?.health?.takeIf { it.available }
            ?: primary?.health
            ?: unsupported(HEALTH_STATUS_SOURCE, noBattery)

        // voltage/current/power/chargeCounter and their signs are copied unchanged; Linux
        // charge_counter is deliberately never read or mapped onto the Android remaining charge.
        return base.copy(
            healthStatus = healthStatus,
            healthPercent = healthPercent,
            designCapacityMah = designCapacity,
            fullChargeCapacityMah = fullCapacity,
            cycleCount = cycleCount,
        )
    }

    /** Fixed, monotonic window. A future timestamp is invalid rather than "fresh". */
    internal fun isFrameFresh(frame: PrivilegedNodeFrame, nowElapsedRealtimeMs: Long): Boolean {
        val sampled = frame.sampledElapsedRealtimeMs
        return sampled <= nowElapsedRealtimeMs && nowElapsedRealtimeMs - sampled <= FRAME_MAX_AGE_MS
    }

    private fun nodePath(dir: String, key: String): String = "/sys/class/power_supply/$dir/$key"

    private fun <T> prefer(
        advanced: Reading<T>?,
        base: Reading<T>,
        source: String,
        reason: String,
    ): Reading<T> {
        val preferred = advanced
        if (preferred != null && preferred.available) return preferred
        if (base.available) return base
        return preferred ?: Reading.unavailable(source, reason)
    }

    private fun describeType(reading: Reading<String>): String {
        if (!reading.available) return reading.reason ?: reading.status.name
        val text = reading.value?.trim().orEmpty().take(32)
        return if (text.equals("Battery", ignoreCase = true)) "type=Battery"
        else "type=$text（非 Battery）"
    }

    private fun readSupply(
        dir: String,
        readNode: (String, String) -> Reading<String>,
    ): Supply = Supply(
        dir = dir,
        chargeFull = capacityMah(nodePath(dir, "charge_full"), readNode(dir, "charge_full")),
        chargeDesign = capacityMah(
            nodePath(dir, "charge_full_design"), readNode(dir, "charge_full_design"),
        ),
        energyFull = energyUwh(nodePath(dir, "energy_full"), readNode(dir, "energy_full")),
        energyDesign = energyUwh(
            nodePath(dir, "energy_full_design"), readNode(dir, "energy_full_design"),
        ),
        cycle = cycleCount(nodePath(dir, "cycle_count"), readNode(dir, "cycle_count")),
        health = healthStatus(nodePath(dir, "health"), readNode(dir, "health")),
    )

    /**
     * charge_full values are microamp-hours, so mAh conversion is valid. SensorMath's
     * 100..50,000 mAh bound rejects unit mistakes and absurd cells without rescaling.
     */
    private fun capacityMah(path: String, node: Reading<String>): Reading<Double> {
        if (!node.available) return Reading.unavailable(path, node.reason ?: "节点不可用", node.status)
        val mah = SensorMath.microAmpHoursToMah(node.value!!)
        return if (mah == null) {
            invalid(path, "不是非负整数微安时，或换算后不在 100..50000 mAh")
        } else {
            Reading(mah, "$path（µAh → mAh）")
        }
    }

    /** Energy is kept in µWh for a ratio only. It is never converted into mAh via voltage. */
    private fun energyUwh(path: String, node: Reading<String>): Reading<Long> {
        if (!node.available) return Reading.unavailable(path, node.reason ?: "节点不可用", node.status)
        val value = SensorMath.nonNegativeCounter(node.value!!)?.takeIf { it > 0L }
        return if (value == null) invalid(path, "不是正整数微瓦时")
        else Reading(value, "$path（µWh）")
    }

    /** Linux ABI: cycle_count=0 means unknown, not a brand-new pack. */
    private fun cycleCount(path: String, node: Reading<String>): Reading<Long> {
        if (!node.available) return Reading.unavailable(path, node.reason ?: "节点不可用", node.status)
        val value = SensorMath.nonNegativeCounter(node.value!!)
        return when {
            value == null -> invalid(path, "cycle_count 不是非负整数")
            value == 0L -> unsupported(path, "Linux ABI 将 cycle_count=0 视为未知，不当作 0 次")
            value > Int.MAX_VALUE.toLong() -> invalid(path, "cycle_count 超出 32 位范围")
            else -> Reading(value, path)
        }
    }

    private fun healthStatus(path: String, node: Reading<String>): Reading<String> {
        if (!node.available) return Reading.unavailable(path, node.reason ?: "节点不可用", node.status)
        val normalized = when (node.value!!.trim().lowercase()) {
            "good" -> "GOOD"
            "overheat" -> "OVERHEAT"
            "dead" -> "DEAD"
            "over voltage" -> "OVER_VOLTAGE"
            "unspecified failure" -> "UNSPECIFIED_FAILURE"
            "cold" -> "COLD"
            "unknown" -> return unsupported(path, "sysfs health 报告 Unknown")
            else -> return unsupported(path, "未识别的 sysfs health 值")
        }
        return Reading(normalized, path)
    }

    /** Prefer the same object's charge pair; only fall back to its energy pair when charge is absent. */
    private fun chargeHealthPercent(supply: Supply): Reading<Double>? {
        if (!supply.chargeFull.available || !supply.chargeDesign.available) return null
        val fullPath = nodePath(supply.dir, "charge_full")
        val designPath = nodePath(supply.dir, "charge_full_design")
        val percent = SensorMath.healthPercent(
            supply.chargeFull.value!!, supply.chargeDesign.value!!,
        )
        val source = "$fullPath × 100 / $designPath（同一 ${supply.dir} 对象；µAh → mAh）"
        return if (percent == null) {
            invalid(source, "FCC/设计容量比不在 1..150% 或容量不可信")
        } else {
            Reading(
                percent,
                source,
                reason = "仅用同一供电对象的 charge_full 与 charge_full_design",
            )
        }
    }

    private fun energyHealthPercent(supply: Supply): Reading<Double>? {
        if (!supply.energyFull.available || !supply.energyDesign.available) return null
        val full = supply.energyFull.value!!
        val design = supply.energyDesign.value!!
        val percent = ratioPercent(full, design)
        val source = "${nodePath(supply.dir, "energy_full")} × 100 / " +
            "${nodePath(supply.dir, "energy_full_design")}" +
            "（同一 ${supply.dir} 对象；µWh 比值，未换算 mAh）"
        return if (percent == null) invalid(source, "能量容量比不在 1..150%")
        else Reading(percent, source)
    }

    private fun ratioPercent(full: Long, design: Long): Double? {
        if (full <= 0L || design <= 0L) return null
        val percent = full.toDouble() * 100.0 / design.toDouble()
        return percent.takeIf { it.isFinite() && it in 1.0..150.0 }
    }

    private fun score(supply: Supply): Int {
        var score = 0
        if (supply.chargeFull.available) score += 8
        if (supply.chargeDesign.available) score += 8
        if (supply.chargeFull.available && supply.chargeDesign.available) score += 32
        if (supply.energyFull.available && supply.energyDesign.available) score += 16
        if (supply.cycle.available) score += 4
        if (supply.health.available) score += 1
        return score
    }

    private class Supply(
        val dir: String,
        val chargeFull: Reading<Double>,
        val chargeDesign: Reading<Double>,
        val energyFull: Reading<Long>,
        val energyDesign: Reading<Long>,
        val cycle: Reading<Long>,
        val health: Reading<String>,
    )
}
