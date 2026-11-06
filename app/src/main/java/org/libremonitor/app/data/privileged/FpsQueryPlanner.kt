/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.data.privileged

import org.libremonitor.core.FpsSample

/** Bounded discovery rotates across polls. Cached layer text is validated data, never a command. */
internal class FpsQueryPlanner {
    private var current = FpsProtocol.Query()
    private var discoveredAt: Long? = null
    fun reset() { current = FpsProtocol.Query(); discoveredAt = null }
    fun query(nowNanos: Long): FpsProtocol.Query {
        val at = discoveredAt
        if (at != null && nowNanos - at !in 0..4_999_999_999L) {
            current = current.copy(offset = 0, cachedPackage = null, cachedLayer = null)
        }
        return current
    }
    fun accept(capture: FpsProtocol.Capture, sample: FpsSample, nowNanos: Long) {
        if (!capture.cached) discoveredAt = nowNanos
        val target = sample.target.value?.takeIf { sample.fps.available && capture.layers.any { layer -> layer.name == it } }
        current = FpsProtocol.Query(
            offset = if (target != null || capture.cached || capture.offset + FpsProtocol.MAX_CANDIDATES >= capture.totalCandidates) 0
                else capture.offset + FpsProtocol.MAX_CANDIDATES,
            millisecondsTimeout = capture.millisecondsTimeout ?: current.millisecondsTimeout,
            cachedPackage = capture.packageName.takeIf { target != null }, cachedLayer = target,
            discoveryPackage = capture.packageName,
        )
    }
}

internal data class FpsAttempt(val durationMillis: Long, val sample: FpsSample, val capture: FpsProtocol.Capture?,
    val timestampCounts: List<Int> = capture?.layers?.map { it.timestamps.size }.orEmpty(),
    val sampledMonotonicNanos: Long = 0)
