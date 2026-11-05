/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.core

class BluetoothPanRates {
    private var previous: Map<String, InterfaceCounters> = emptyMap()
    private var previousTime: Long? = null
    private var previousSource: String? = null
    fun reset() { previous = emptyMap(); previousTime = null; previousSource = null }

    fun sample(input: Reading<String>, now: Long): Pair<Reading<Double>, Reading<Double>> {
        val source = "${input.source}；仅蓝牙 PAN 接口，非耳机音频码率"
        fun absent(reason: String, status: ReadingStatus = ReadingStatus.UNSUPPORTED): Pair<Reading<Double>, Reading<Double>> {
            val value = Reading.unavailable<Double>(source, reason, status)
            return value to value
        }
        if (!input.available) { reset(); return absent(input.reason ?: "接口计数不可用", input.status) }
        val current = NetworkInterfaceCounters.parse(input.value!!).filterKeys(NetworkInterfaceCounters::isBluetoothPan)
        val before = previous
        val beforeTime = previousTime
        val same = previousSource == input.source && current.keys == before.keys
        previous = current; previousTime = now; previousSource = input.source
        if (current.isEmpty()) return absent("没有蓝牙 PAN 网络接口；A2DP/HFP 耳机吞吐不由这些接口公开")
        if (!same || beforeTime == null) return absent("等待下一次相同接口的计数，建立蓝牙 PAN 速率基线")
        val rx = current.map { (name, value) -> CounterRate.bytesPerSecond(before.getValue(name).received, value.received, now - beforeTime) }
        val tx = current.map { (name, value) -> CounterRate.bytesPerSecond(before.getValue(name).sent, value.sent, now - beforeTime) }
        if (rx.any { it == null } || tx.any { it == null }) return absent("接口计数重置或时间无效，已重建基线")
        return Reading(rx.filterNotNull().sum(), source) to Reading(tx.filterNotNull().sum(), source)
    }
}
