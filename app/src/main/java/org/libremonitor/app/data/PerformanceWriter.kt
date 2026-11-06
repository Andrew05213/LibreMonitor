/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.libremonitor.core.PerformancePoint

/** One bounded writer per recording. Close drains accepted points before Finish is allowed. */
internal class PerformanceWriter(
    scope: CoroutineScope,
    private val write: suspend (List<PerformancePoint>) -> Boolean,
    private val onFailure: (Exception) -> Unit,
    private val flushMillis: Long = 1_000,
    private val onCommitted: (Int, Long) -> Unit = { _, _ -> },
) {
    // At most 20 queued + 10 in the current transaction. Backpressure never silently drops points.
    private val points = Channel<PerformancePoint>(20)
    @Volatile private var failure: Exception? = null
    private val worker = scope.launch(Dispatchers.IO) {
        val batch = ArrayList<PerformancePoint>(10)
        var started = 0L
        suspend fun flush() {
            if (batch.isEmpty()) return
            val began = System.nanoTime()
            check(write(batch.toList())) { "记录已停止或达到样本上限" }
            onCommitted(batch.size, ((System.nanoTime() - began) / 1_000_000).coerceAtLeast(0))
            batch.clear()
        }
        try {
            while (true) {
                val next = if (batch.isEmpty()) points.receiveCatching() else {
                    val spent = (System.nanoTime() - started) / 1_000_000
                    if (spent >= flushMillis) { flush(); continue }
                    withTimeoutOrNull(flushMillis - spent) { points.receiveCatching() }
                }
                if (next == null) { flush(); continue }
                if (next.isClosed) { flush(); break }
                if (batch.isEmpty()) started = System.nanoTime()
                batch.add(next.getOrThrow())
                if (batch.size == 10) flush()
            }
        } catch (e: Exception) {
            failure = e
            points.close(e)
            onFailure(e)
        }
    }

    suspend fun append(point: PerformancePoint) {
        failure?.let { throw it }
        points.send(point)
    }

    suspend fun closeAndFlush() {
        points.close()
        worker.join()
        failure?.let { throw it }
    }
}
