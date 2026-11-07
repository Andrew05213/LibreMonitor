/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.data.collectors

import android.os.SystemClock
import org.libremonitor.core.*

/** Same-supply, live V/I only. The Oplus unit profile is identified by a driver marker, never magnitude. */
internal object BatteryElectricalCollector {
    fun enrich(base: BatterySample, frame: PrivilegedNodeFrame?, mode: BatteryCurrentMode = BatteryCurrentMode.AUTO) =
        enrich(base, frame, mode, SystemClock.elapsedRealtime(), ::readBounded)

    internal fun enrich(base: BatterySample, frame: PrivilegedNodeFrame?, mode: BatteryCurrentMode, now: Long,
        read: (String, Int) -> Reading<String>): BatterySample {
        if (base.status == "无电池") return base
        val usable = frame?.takeIf { now - it.sampledElapsedRealtimeMs in 0..2_000 }
        val observed = linkedMapOf<String, Reading<String>>()
        fun node(supply: String, field: String): Reading<String> {
            val path = "/sys/class/power_supply/$supply/$field"
            val rootValue = usable?.readings?.get(path)
            val value = if (rootValue != null) rootValue.copy(source = "Root ${rootValue.source}") else safely(path) { read(path, 256) }
            observed[path] = value
            return value
        }
        val rawApi = base.electricalDiagnostics?.rawApiCurrent ?: base.currentMa.mapReading { (it * 1_000).toLong() }
        fun power(voltage: Reading<Double>, current: Reading<Double>, source: String): Reading<Double> = when {
            !voltage.available -> Reading.unavailable(source, voltage.reason ?: "缺少电压", voltage.status)
            !current.available -> Reading.unavailable(source, current.reason ?: "缺少电流", current.status)
            else -> Reading(voltage.value!! * current.value!! / 1_000, source,
                reason = "同一电池端电压 × 电流；保留原始符号；非充电器输入功率，未做双电芯倍率推断")
        }
        for (supply in listOf("battery", "bms")) {
            val type = node(supply, "type")
            if (!type.available || !type.value.orEmpty().trim().equals("Battery", true)) continue
            val scope = node(supply, "scope")
            if (scope.value.orEmpty().trim().equals("Device", true)) continue
            val present = node(supply, "present")
            if (present.available && present.value.orEmpty().trim() != "1") continue
            val model = node(supply, "model_name")
            val milli = when (mode) {
                BatteryCurrentMode.MILLIAMPS -> true
                BatteryCurrentMode.MICROAMPS -> false
                BatteryCurrentMode.AUTO -> supply == "battery" && model.available && model.value.orEmpty().trim().equals("oplus battery", true)
            }
            val volts = node(supply, "voltage_now")
            val amps = node(supply, "current_now")
            val v = volts.mapReading(source = "${volts.source}（µV → V）") {
                it.trim().toLongOrNull()?.takeIf { raw -> raw in 1_000_000..30_000_000 }?.div(1_000_000.0)
            }
            val unit = if (milli) "mA" else "µA"
            val selected = if (mode != BatteryCurrentMode.AUTO) "手动单位" else if (milli) "已识别 Oplus 驱动" else "Linux 标准单位"
            val i = amps.mapReading(source = "${amps.source}（$selected；$unit${if (milli) "" else " → mA"}）") {
                it.trim().toLongOrNull()?.takeIf { raw -> raw in -100_000_000..100_000_000 }
                    ?.let { raw -> if (milli) raw.toDouble().takeIf { value -> value in -100_000.0..100_000.0 } else raw / 1_000.0 }
            }
            if (!v.available || !i.available) continue
            val current = i.copy(reason = "原始 current_now=${amps.value?.trim()} $unit；Android API 原值=${rawApi.value}；符号未校准")
            return base.copy(voltageV = v, currentMa = current,
                powerW = power(v, current, "电池端估算 ${v.source} × ${current.source}"),
                electricalDiagnostics = BatteryElectricalDiagnostics(rawApi, supply, unit, observed.toMap()))
        }
        // Public API always follows its documented µA contract unless the user explicitly overrides it.
        val milli = mode == BatteryCurrentMode.MILLIAMPS
        val current = rawApi.mapReading(source = "BatteryManager.CURRENT_NOW（${if (milli) "手动 mA" else "标准 µA → mA"}；原始符号）") {
            it.takeIf { raw -> raw in -100_000_000..100_000_000 }?.let { raw ->
                if (milli) raw.toDouble().takeIf { value -> value in -100_000.0..100_000.0 } else raw / 1_000.0
            }
        }.let { it.copy(reason = "公共 API 原值=${rawApi.value}；未取得同一电池的有效节点电压/电流对") }
        return base.copy(currentMa = current, powerW = power(base.voltageV, current, "电池端估算 V × CURRENT_NOW（W）"),
            electricalDiagnostics = BatteryElectricalDiagnostics(rawApi, currentUnit = if (milli) "mA" else "µA", nodes = observed.toMap()))
    }
}
