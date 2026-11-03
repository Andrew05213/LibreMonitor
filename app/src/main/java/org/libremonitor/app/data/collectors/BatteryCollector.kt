/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.data.collectors

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import org.libremonitor.core.BatterySample
import org.libremonitor.core.Reading

/**
 * Public broadcast health codes only. GOOD is a status, never a percentage.
 * Unknown or vendor-invented values stay unavailable instead of being softened to GOOD.
 */
internal fun batteryHealthReading(raw: Int, source: String): Reading<String> = when (raw) {
    BatteryManager.BATTERY_HEALTH_GOOD -> Reading("GOOD", source)
    BatteryManager.BATTERY_HEALTH_OVERHEAT -> Reading("OVERHEAT", source)
    BatteryManager.BATTERY_HEALTH_DEAD -> Reading("DEAD", source)
    BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> Reading("OVER_VOLTAGE", source)
    BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> Reading("UNSPECIFIED_FAILURE", source)
    BatteryManager.BATTERY_HEALTH_COLD -> Reading("COLD", source)
    BatteryManager.BATTERY_HEALTH_UNKNOWN -> unsupported(source, "系统报告健康状态未知")
    else -> unsupported(source, "未识别的健康状态（原始值 $raw）")
}

/** PLUGGED is a bitmask of external power sources; 0 means no external source, not unavailable. */
internal fun batteryChargingSourceReading(raw: Int, source: String): Reading<String> {
    if (raw == 0) return Reading("未连接外部电源", source)
    val sources = buildList {
        if ((raw and BatteryManager.BATTERY_PLUGGED_AC) != 0) add("AC")
        if ((raw and BatteryManager.BATTERY_PLUGGED_USB) != 0) add("USB")
        if ((raw and BatteryManager.BATTERY_PLUGGED_WIRELESS) != 0) add("无线")
    }
    return if (sources.isEmpty()) unsupported(source, "未识别的充电源（原始值 $raw）")
    else Reading(sources.joinToString("+"), source)
}

/**
 * EXTRA_CYCLE_COUNT is API 34+. A missing extra is unavailable, never 0.
 * A public 0 is preserved as system-reported data; it is not evidence of new cells.
 */
internal fun batteryCycleCountReading(
    sdkInt: Int,
    hasExtra: Boolean,
    raw: Int,
    source: String,
): Reading<Long> = when {
    sdkInt < 34 -> unsupported(source, "Android 14（API 34）起系统广播才提供循环次数")
    !hasExtra -> unsupported(source, "广播未提供 EXTRA_CYCLE_COUNT；缺失不当作 0 次")
    raw < 0 || raw == Int.MIN_VALUE || raw == Int.MAX_VALUE ->
        invalid(source, "循环次数不是有效的非负整数")
    else -> Reading(
        raw.toLong(),
        source,
        reason = "普通广播原值；0 仅表示系统报告值，不能据此推断电池健康",
    )
}

/** Public Android battery API only. CURRENT_NOW's vendor sign is never reinterpreted. */
internal object BatteryCollector {
    private const val BROADCAST = "ACTION_BATTERY_CHANGED（系统粘性广播）"
    private const val CURRENT = "BatteryManager.CURRENT_NOW（µA → mA，厂商原始符号未校准）"
    private const val CHARGE = "BatteryManager.CHARGE_COUNTER（µAh → mAh，剩余电荷而非健康度）"
    private const val POWER = "电池端估算 V × CURRENT_NOW（W）；非充电器/SoC功率，符号未校准"
    private const val HEALTH = "$BROADCAST:health"
    private const val PLUGGED = "$BROADCAST:plugged"
    private const val CYCLE = "BatteryManager.EXTRA_CYCLE_COUNT（循环次数）"
    private const val HEALTH_PERCENT = "电池健康百分比（FCC/设计容量）"

    fun sample(context: Context): BatterySample {
        val broadcast: Reading<Intent> = safely(BROADCAST) {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (intent == null) unsupported(BROADCAST, "系统没有提供电池广播")
            else Reading(intent, BROADCAST)
        }
        val present = broadcast.mapReading(source = "$BROADCAST:present") { intent ->
            !intent.hasExtra(BatteryManager.EXTRA_PRESENT) ||
                intent.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true)
        }
        val batteryAbsent = present.value == false
        val manager: Reading<BatteryManager> = safely("BatteryManager") {
            val service = context.getSystemService(BatteryManager::class.java)
            if (service == null) unsupported("BatteryManager", "公共电池 API 不可用")
            else Reading(service, "BatteryManager")
        }

        fun extraInt(name: String): Reading<Int> {
            val source = "$BROADCAST:$name"
            if (batteryAbsent) return unsupported(source, "设备报告没有电池")
            if (!broadcast.available) return Reading.unavailable(
                source, broadcast.reason ?: "没有电池广播", broadcast.status,
            )
            return safely(source) {
                val intent = broadcast.value!!
                if (!intent.hasExtra(name)) return@safely unsupported(source, "广播未提供此字段")
                val raw = intent.getIntExtra(name, Int.MIN_VALUE)
                if (raw == Int.MIN_VALUE || raw == Int.MAX_VALUE) {
                    unsupported(source, "系统返回未支持的哨兵值")
                } else Reading(raw, source)
            }
        }

        fun property(id: Int, source: String): Reading<Int> {
            if (batteryAbsent) return unsupported(source, "设备报告没有电池")
            if (!manager.available) return Reading.unavailable(
                source, manager.reason ?: "BatteryManager 不可用", manager.status,
            )
            return safely(source) {
                // The app targets API >= 28: getIntProperty signals unsupported with MIN_VALUE.
                // Some vendor implementations use MAX_VALUE instead. Zero is a valid current.
                val raw = manager.value!!.getIntProperty(id)
                if (raw == Int.MIN_VALUE || raw == Int.MAX_VALUE) {
                    unsupported(source, "BatteryManager 未支持此属性（哨兵值）")
                } else Reading(raw, source)
            }
        }

        val levelRaw = extraInt(BatteryManager.EXTRA_LEVEL)
        val scale = extraInt(BatteryManager.EXTRA_SCALE)
        val broadcastLevel: Reading<Int> = when {
            !levelRaw.available -> Reading.unavailable(
                "$BROADCAST:level/scale", levelRaw.reason ?: "电量不可用", levelRaw.status,
            )
            !scale.available -> Reading.unavailable(
                "$BROADCAST:level/scale", scale.reason ?: "电量刻度不可用", scale.status,
            )
            else -> {
                val numerator = levelRaw.value!!
                val denominator = scale.value!!
                if (denominator <= 0 || numerator !in 0..denominator) {
                    invalid("$BROADCAST:level/scale", "电量或满刻度无效")
                } else Reading(
                    (numerator.toLong() * 100L / denominator.toLong()).toInt(),
                    "$BROADCAST:level/scale（%）",
                )
            }
        }
        val level = if (broadcastLevel.available || batteryAbsent) broadcastLevel else {
            val capacity = property(BatteryManager.BATTERY_PROPERTY_CAPACITY, "BatteryManager.CAPACITY（%）")
                .mapReading { it.takeIf { percent -> percent in 0..100 } }
            if (capacity.available) capacity else broadcastLevel
        }
        val temperature = extraInt(BatteryManager.EXTRA_TEMPERATURE).mapReading(
            source = "$BROADCAST:temperature（0.1°C → °C；电池传感器，非CPU）",
        ) { it.takeIf { tenths -> tenths in -1_000..2_000 }?.div(10.0) }
        val voltage = extraInt(BatteryManager.EXTRA_VOLTAGE).mapReading(
            source = "$BROADCAST:voltage（mV → V）",
        ) { it.takeIf { mv -> mv in 1..30_000 }?.div(1_000.0) }
        val current = property(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW, CURRENT).mapReading {
            it.takeIf { microamps -> microamps in -100_000_000..100_000_000 }?.div(1_000.0)
        }
        val charge = property(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER, CHARGE).mapReading {
            it.takeIf { microampHours -> microampHours in 0..100_000_000 }?.div(1_000.0)
        }
        val power: Reading<Double> = when {
            !voltage.available -> Reading.unavailable(
                POWER, "缺少有效电池电压：${voltage.reason}", voltage.status,
            )
            !current.available -> Reading.unavailable(
                POWER, "缺少有效瞬时电流：${current.reason}", current.status,
            )
            else -> Reading(
                voltage.value!! * current.value!! / 1_000.0,
                POWER,
                reason = "未经设备校准；厂商电流方向可能相反，负号不能直接解释为放电",
            )
        }
        val rawStatus = extraInt(BatteryManager.EXTRA_STATUS)
        val status = if (batteryAbsent) "无电池" else when (rawStatus.value) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "充电"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "放电"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "未充电"
            BatteryManager.BATTERY_STATUS_FULL -> "已充满"
            BatteryManager.BATTERY_STATUS_UNKNOWN -> "未知"
            else -> "未知（${rawStatus.reason ?: "系统未报告有效状态"}）"
        }
        val technology = safely("$BROADCAST:technology") {
            if (batteryAbsent) return@safely unsupported<String>("$BROADCAST:technology", "设备报告没有电池")
            broadcast.mapReading(source = "$BROADCAST:technology") {
                it.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)?.trim()?.take(128)?.takeIf(String::isNotEmpty)
            }
        }.value
        val healthStatus: Reading<String> = if (batteryAbsent) {
            unsupported(HEALTH, "设备报告没有电池")
        } else {
            extraInt(BatteryManager.EXTRA_HEALTH).let { raw ->
                if (raw.available) batteryHealthReading(raw.value!!, HEALTH)
                else Reading.unavailable(HEALTH, raw.reason ?: "健康状态不可用", raw.status)
            }
        }
        val chargingSource: Reading<String> = if (batteryAbsent) {
            unsupported(PLUGGED, "设备报告没有电池")
        } else {
            extraInt(BatteryManager.EXTRA_PLUGGED).let { raw ->
                if (raw.available) batteryChargingSourceReading(raw.value!!, PLUGGED)
                else Reading.unavailable(PLUGGED, raw.reason ?: "充电源不可用", raw.status)
            }
        }
        // EXTRA_CYCLE_COUNT exists only on API 34+; a missing extra must not become 0.
        val cycleCount: Reading<Long> = when {
            batteryAbsent -> unsupported(CYCLE, "设备报告没有电池")
            !broadcast.available -> Reading.unavailable(
                CYCLE, broadcast.reason ?: "没有电池广播", broadcast.status,
            )
            Build.VERSION.SDK_INT < 34 -> unsupported(
                CYCLE, "Android 14（API 34）起系统广播才提供循环次数",
            )
            else -> safely(CYCLE) {
                val intent = broadcast.value!!
                val hasExtra = intent.hasExtra(BatteryManager.EXTRA_CYCLE_COUNT)
                val raw = intent.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, Int.MIN_VALUE)
                batteryCycleCountReading(Build.VERSION.SDK_INT, hasExtra, raw, CYCLE)
            }
        }
        return BatterySample(
            levelPercent = level,
            temperatureC = temperature,
            voltageV = voltage,
            currentMa = current,
            chargeCounterMah = charge,
            powerW = power,
            status = status,
            technology = technology,
            healthStatus = healthStatus,
            healthPercent = Reading.unavailable(
                HEALTH_PERCENT,
                "普通广播不提供；需同一供电对象、同一单位的 full-charge/design 容量",
            ),
            designCapacityMah = Reading.unavailable(
                "power_supply:charge_full_design（µAh → mAh）",
                "普通广播不提供；等待只读节点补充",
            ),
            fullChargeCapacityMah = Reading.unavailable(
                "power_supply:charge_full（µAh → mAh）",
                "普通广播不提供；等待只读节点补充",
            ),
            cycleCount = cycleCount,
            chargingSource = chargingSource,
        )
    }

    fun failure(error: Exception) = BatterySample(
        levelPercent = failedReading(BROADCAST, error),
        temperatureC = failedReading("$BROADCAST:temperature", error),
        voltageV = failedReading("$BROADCAST:voltage", error),
        currentMa = failedReading(CURRENT, error),
        chargeCounterMah = failedReading(CHARGE, error),
        powerW = failedReading(POWER, error),
        status = "电池状态读取失败",
        technology = null,
        healthStatus = failedReading(HEALTH, error),
        healthPercent = failedReading(HEALTH_PERCENT, error),
        designCapacityMah = failedReading("power_supply:charge_full_design", error),
        fullChargeCapacityMah = failedReading("power_supply:charge_full", error),
        cycleCount = failedReading(CYCLE, error),
        chargingSource = failedReading(PLUGGED, error),
    )
}
