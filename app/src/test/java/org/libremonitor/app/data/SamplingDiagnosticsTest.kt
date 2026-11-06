/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.data

import org.junit.Assert.*
import org.junit.Test
import org.libremonitor.core.*

class SamplingDiagnosticsTest {
    private fun trace(start: Long, valid: Boolean = false, requested: Boolean = true) = SamplingTrace(start, 100, 40,
        "PERFORMANCE", requested, if (valid) FpsSample(fps = Reading(60.0, "fixture")) else FpsSample(), SamplerTimings(), null)

    @Test fun actualIntervalsExcludeScreenOffAndDemandSwitchGaps() {
        val diagnostics = SamplingDiagnostics()
        diagnostics.record(trace(1_000), 1)
        diagnostics.record(trace(1_150, valid = true), 1)
        diagnostics.record(trace(100_000, requested = false), 2)
        diagnostics.record(trace(100_200), 2)
        val data = diagnostics.snapshot()
        assertEquals(4L, data.cycles)
        assertEquals(3L, data.fpsQueries)
        assertEquals(1L, data.fpsValid)
        assertEquals(2L, data.intervalCount)
        assertEquals(175.0, data.intervalMeanMs!!, 0.0)
        assertEquals(200L, data.intervalMaximumMs!!)
    }

    @Test fun previewMemoryIsBoundedButTotalsAndFailureCountsCoverTheWholeRun() {
        val diagnostics = SamplingDiagnostics()
        repeat(1_000) { diagnostics.record(trace(it * 100L), 1) }
        val data = diagnostics.snapshot()
        assertEquals(120, data.recent.size)
        assertEquals(1_000L, data.fpsQueries)
        assertEquals(1_000L, data.failures.values.sum())
        assertEquals(88_000L, data.recent.first().startedElapsedMs)
        diagnostics.written(10, 5)
        diagnostics.written(3, 15)
        diagnostics.writeFailed()
        val writes = diagnostics.snapshot().writes
        assertEquals(13L, writes.points)
        assertEquals(10.0, writes.meanMs!!, 0.0)
        assertEquals(1L, writes.failures)
    }
}
