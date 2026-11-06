/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.data

import org.libremonitor.app.data.privileged.FpsAttempt
import org.libremonitor.core.FpsSample

internal data class SamplerTimings(val environmentMs: Long = 0, val fpsMs: Long = 0,
    val frameMs: Long = 0, val otherCollectorsMs: Long = 0)
internal data class SamplingTrace(val startedElapsedMs: Long, val targetIntervalMs: Long, val cycleMs: Long,
    val mode: String, val fpsRequested: Boolean, val fps: FpsSample, val timings: SamplerTimings, val attempt: FpsAttempt?,
    val enqueueWaitMs: Long = 0)
internal data class WriteTimings(val batches: Long, val points: Long, val failures: Long,
    val meanMs: Double?, val maximumMs: Long?)
internal data class DiagnosticsSnapshot(val cycles: Long, val fpsQueries: Long, val fpsValid: Long,
    val failures: Map<String, Long>, val collectionErrors: Long, val intervalCount: Long,
    val intervalMeanMs: Double?, val intervalMinimumMs: Long?, val intervalMaximumMs: Long?, val recent: List<SamplingTrace>,
    val writes: WriteTimings)

/** Memory only, bounded previews; export is a separate explicit user action. No extra sampling loop. */
internal class SamplingDiagnostics {
    private val recent = ArrayDeque<SamplingTrace>()
    private val failures = linkedMapOf<String, Long>()
    private var cycles = 0L
    private var queries = 0L
    private var valid = 0L
    private var errors = 0L
    private var continuity = -1L
    private var previous: Long? = null
    private var intervals = 0L
    private var sum = 0.0
    private var minimum: Long? = null
    private var maximum: Long? = null
    private var batches = 0L
    private var points = 0L
    private var writeFailures = 0L
    private var writeSum = 0.0
    private var writeMaximum: Long? = null

    @Synchronized fun error() { errors++ }
    @Synchronized fun written(count: Int, durationMs: Long) {
        batches++; points += count; writeSum += durationMs
        writeMaximum = maxOf(writeMaximum ?: durationMs, durationMs)
    }
    @Synchronized fun writeFailed() { writeFailures++ }
    @Synchronized fun record(trace: SamplingTrace, group: Long) {
        cycles++
        if (trace.fpsRequested) {
            queries++
            if (trace.fps.fps.available) valid++ else {
                val reason = trace.fps.fps.reason.orEmpty().take(512)
                val key = if (reason in failures || failures.size < 64) reason else "其他失败原因"
                failures[key] = (failures[key] ?: 0) + 1
            }
        }
        previous?.takeIf { continuity == group && trace.startedElapsedMs >= it }?.let {
            val interval = trace.startedElapsedMs - it
            intervals++; sum += interval
            minimum = minOf(minimum ?: interval, interval); maximum = maxOf(maximum ?: interval, interval)
        }
        continuity = group; previous = trace.startedElapsedMs
        if (recent.size == 120) recent.removeFirst()
        recent.addLast(trace)
    }
    @Synchronized fun snapshot() = DiagnosticsSnapshot(cycles, queries, valid, failures.toMap(), errors,
        intervals, if (intervals == 0L) null else sum / intervals, minimum, maximum, recent.toList(),
        WriteTimings(batches, points, writeFailures, if (batches == 0L) null else writeSum / batches, writeMaximum))
}
