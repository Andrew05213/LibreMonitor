/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.data.collectors

import android.net.TrafficStats
import android.os.SystemClock
import org.libremonitor.core.CounterRate
import org.libremonitor.core.NetworkSample
import org.libremonitor.core.Reading
import org.libremonitor.core.PrivilegedNodeFrame
import org.libremonitor.core.BluetoothPanRates

/** Aggregate device counters, not a process/UID scan, packet capture or active network probe. */
internal class NetworkCollector {
    private data class Baseline(val bytes: Long, val elapsedMillis: Long)
    private var previousRx: Baseline? = null
    private var previousTx: Baseline? = null
    private val pan = BluetoothPanRates()

    fun resetBaseline() { previousRx = null; previousTx = null; pan.reset() }

    fun sample(frame: PrivilegedNodeFrame? = null): NetworkSample {
        val rxSource = "TrafficStats.getTotalRxBytes（系统累计字节，非本应用流量）"
        val txSource = "TrafficStats.getTotalTxBytes（系统累计字节，非本应用流量）"
        val received = counter(rxSource) { TrafficStats.getTotalRxBytes() }
        // Timestamp each counter close to its read; monotonic time is not wall clock.
        val rxTime = SystemClock.elapsedRealtime()
        val sent = counter(txSource) { TrafficStats.getTotalTxBytes() }
        val txTime = SystemClock.elapsedRealtime()
        val download = rate(received, previousRx, rxTime)
        val upload = rate(sent, previousTx, txTime)
        // On failure clear the baseline; on reset adopt the new counter but emit no rate.
        previousRx = received.value?.let { Baseline(it, rxTime) }
        previousTx = sent.value?.let { Baseline(it, txTime) }
        val root = frame?.readings?.get("/proc/net/dev")?.takeIf { it.available }?.let {
            it.copy(source = "Root ${frame.sessionId} /proc/net/dev")
        }
        val pair = pan.sample(root ?: readBounded("/proc/net/dev", 65_536), SystemClock.elapsedRealtime())
        return NetworkSample(download, upload, received, sent, pair.first, pair.second)
    }

    private fun counter(source: String, read: () -> Long): Reading<Long> = safely(source) {
        val value = read()
        when {
            value == TrafficStats.UNSUPPORTED.toLong() -> unsupported(source, "系统不支持全设备流量计数")
            value < 0L || value == Long.MAX_VALUE -> invalid(source, "系统返回无效的流量计数")
            else -> Reading(value, source)
        }
    }

    private fun rate(counter: Reading<Long>, before: Baseline?, now: Long): Reading<Double> {
        val source = "${counter.source}；单调时间差分（B/s）"
        val value = counter.value ?: return Reading.unavailable(
            source, counter.reason ?: "累计计数不可用", counter.status,
        )
        if (before == null) return unsupported(source, "首次有效样本，需下一次计数计算速率")
        val elapsed = now - before.elapsedMillis
        val result = CounterRate.bytesPerSecond(before.bytes, value, elapsed)
        return if (result != null && result.isFinite()) Reading(result, source) else unsupported(
            source,
            if (value < before.bytes) "计数发生重置，已重建基线" else "单调时间间隔无效，等待下一次样本",
        )
    }

    fun failure(error: Exception): NetworkSample {
        resetBaseline()
        return NetworkSample(
            failedReading<Double>("TrafficStats:Rx B/s", error),
            failedReading<Double>("TrafficStats:Tx B/s", error),
            failedReading<Long>("TrafficStats:Rx bytes", error),
            failedReading<Long>("TrafficStats:Tx bytes", error),
        )
    }
}
