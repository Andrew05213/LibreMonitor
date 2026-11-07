/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.data

import kotlinx.coroutines.*
import org.libremonitor.app.data.privileged.FpsAttempt
import org.libremonitor.core.FpsSample

/** Driven exclusively by the shared sampling engine: one pending query, no independent polling loop. */
internal class FpsPoller(private val scope: CoroutineScope, private val clock: () -> Long,
    private val query: suspend () -> Result) {
    data class Result(val sample: FpsSample, val attempt: FpsAttempt? = null, val completedAt: Long = 0)
    private var pending: Job? = null
    private var latest: Result? = null
    private var generation = 0L
    private var sequence = 0L
    private var consumed = 0L
    private var started: Long? = null

    @Synchronized fun poll(interval: Long): Result {
        val now = clock()
        val result = latest
        val fresh = sequence != consumed
        consumed = sequence
        val selected = result?.copy(sample = result.sample.copy(hasNewReading = fresh,
            sampledElapsedRealtimeMillis = result.completedAt,
            fps = if (!fresh && result.sample.fps.available) result.sample.fps.copy(reason =
                listOfNotNull(result.sample.fps.reason, "沿用最近帧率，距采集 ${(now - result.completedAt).coerceAtLeast(0)} ms").joinToString("；")) else result.sample.fps))
            ?: Result(FpsSample(hasNewReading = false), completedAt = now)
        if (pending == null && (started == null || now - started!! >= interval.coerceAtLeast(100))) {
            val active = generation
            started = now
            pending = scope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
                try {
                    val value = query().copy(completedAt = clock())
                    synchronized(this@FpsPoller) {
                        if (active == generation) { latest = value; sequence++; pending = null }
                    }
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) {
                    synchronized(this@FpsPoller) {
                        if (active == generation) { latest = Result(FpsSample(), completedAt = clock()); sequence++; pending = null }
                    }
                }
            }.also { it.start() }
        }
        return selected
    }
    @Synchronized fun reset() {
        generation++; pending?.cancel(); pending = null; latest = null; consumed = 0; sequence = 0; started = null
    }
}
