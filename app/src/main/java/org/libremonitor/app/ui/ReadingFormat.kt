/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.ui

import org.libremonitor.core.Reading
import org.libremonitor.core.ReadingStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

internal object ReadingFormat {
    fun number(value: Double, digits: Int = 1): String =
        String.format(Locale.getDefault(), "%.${digits}f", value)

    fun percent(value: Double): String = "${number(value)}%"
    fun temperature(value: Double): String = "${number(value)} °C"
    fun voltage(value: Double): String = "${number(value, 2)} V"
    fun current(value: Double): String = "${number(value, 0)} mA"
    fun power(value: Double): String = "${number(value, 2)} W"

    fun bytes(value: Long): String = byteQuantity(value.toDouble())
    fun rate(value: Double): String = "${byteQuantity(value)}/s"

    private fun byteQuantity(value: Double): String =
        byteParts(value).let { (amount, unit) -> "$amount $unit" }

    fun byteParts(value: Double): Pair<String, String> {
        val units = listOf("B", "KiB", "MiB", "GiB", "TiB")
        var scaled = value
        var unit = 0
        while (abs(scaled) >= 1024.0 && unit < units.lastIndex) {
            scaled /= 1024.0
            unit++
        }
        return number(scaled, if (unit == 0) 0 else 1) to units[unit]
    }

    fun time(epochMillis: Long, withDate: Boolean = false): String =
        SimpleDateFormat(if (withDate) "MM-dd HH:mm:ss" else "HH:mm:ss", Locale.getDefault())
            .format(Date(epochMillis))

    fun interval(milliseconds: Long): String = when {
        milliseconds % 60_000L == 0L -> "${milliseconds / 60_000L} 分钟"
        milliseconds % 1_000L == 0L -> "${milliseconds / 1_000L} 秒"
        else -> "${number(milliseconds / 1_000.0)} 秒"
    }
}

/** Number and unit stay separate so primary cards can render a large value with its unit. */
internal data class Quantity(val value: String, val unit: String?) {
    val display: String get() = if (unit == null) value else "$value $unit"
}

internal fun percentQuantity(value: Double): Quantity = Quantity(ReadingFormat.number(value), "%")
internal fun levelQuantity(value: Int): Quantity = Quantity(value.toString(), "%")
internal fun temperatureQuantity(value: Double): Quantity = Quantity(ReadingFormat.number(value), "°C")
internal fun voltageQuantity(value: Double): Quantity = Quantity(ReadingFormat.number(value, 2), "V")
internal fun currentQuantity(value: Double): Quantity = Quantity(ReadingFormat.number(value, 0), "mA")
internal fun powerQuantity(value: Double): Quantity = Quantity(ReadingFormat.number(value, 2), "W")
internal fun capacityQuantity(value: Double): Quantity = Quantity(ReadingFormat.number(value, 0), "mAh")
internal fun cycleQuantity(value: Long): Quantity = Quantity(value.toString(), "次")
internal fun fpsQuantity(value: Double): Quantity = Quantity(ReadingFormat.number(value), "fps")
internal fun frequencyQuantity(value: Long): Quantity = Quantity(value.toString(), "MHz")
internal fun frequencyQuantity(value: Double): Quantity = Quantity(ReadingFormat.number(value, 0), "MHz")
internal fun bytesQuantity(value: Long): Quantity =
    ReadingFormat.byteParts(value.toDouble()).let { (amount, unit) -> Quantity(amount, unit) }
internal fun rateQuantity(value: Double): Quantity =
    ReadingFormat.byteParts(value).let { (amount, unit) -> Quantity(amount, "$unit/s") }

internal fun <T> Reading<T>.validValue(valid: (T) -> Boolean): T? =
    value?.takeIf { available && valid(it) }

internal fun <T> Reading<T>.missingExplanation(valid: (T) -> Boolean): String? {
    if (validValue(valid) != null) return null
    if (available) return "样本无效，未将其作为 0 显示"
    val statusText = when (status) {
        ReadingStatus.PERMISSION_DENIED -> "系统拒绝访问"
        ReadingStatus.ERROR -> "读取异常"
        ReadingStatus.UNSUPPORTED -> "暂不可用"
        ReadingStatus.AVAILABLE -> "样本无效"
    }
    return reason?.trim()?.takeIf { it.isNotEmpty() }?.let { "$statusText · $it" } ?: statusText
}

internal val validPercent: (Double) -> Boolean = { it.isFinite() && it in 0.0..100.0 }
internal val validHealthPercent: (Double) -> Boolean = { it.isFinite() && it in 1.0..150.0 }
internal val finiteNumber: (Double) -> Boolean = { it.isFinite() }
internal val nonnegativeDouble: (Double) -> Boolean = { it.isFinite() && it >= 0.0 }
internal val nonnegativeLong: (Long) -> Boolean = { it >= 0L }
internal val validBatteryLevel: (Int) -> Boolean = { it in 0..100 }
internal val validTemperature: (Double) -> Boolean = { it.isFinite() && it in -100.0..200.0 }
internal val positiveDouble: (Double) -> Boolean = { it.isFinite() && it > 0.0 }

internal fun batteryStatusLabel(status: String): String = when (status.trim().lowercase(Locale.ROOT)) {
    "charging", "2" -> "充电中"
    "discharging", "3" -> "放电中"
    "not charging", "not_charging", "4" -> "未在充电"
    "full", "5" -> "已充满"
    "unknown", "1", "" -> "充电状态未知"
    else -> status
}

internal fun sourceLabel(source: String): String = "来源：${source.ifBlank { "未提供来源" }}"

internal fun deviceText(value: String): String = value.trim().takeUnless {
    it.isEmpty() || it.equals("unknown", ignoreCase = true)
} ?: "未获取"
