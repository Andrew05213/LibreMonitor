/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.data

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.libremonitor.core.PerformancePoint

class PerformanceWriterTest {
    private fun point(index: Int) = PerformancePoint(index.toLong(), index.toLong(), emptyMap())

    @Test fun stopFlushesEveryAcceptedPointInOrderIncludingThePartialBatch() = runBlocking {
        val batches = mutableListOf<List<PerformancePoint>>()
        val committed = mutableListOf<Int>()
        val writer = PerformanceWriter(this, { batches.add(it); true }, { throw AssertionError(it) },
            onCommitted = { size, millis -> assertTrue(millis >= 0); committed.add(size) })
        repeat(23) { writer.append(point(it)) }
        writer.closeAndFlush()
        assertEquals(listOf(10, 10, 3), batches.map { it.size })
        assertEquals(listOf(10, 10, 3), committed)
        assertEquals((0..22).map { it.toLong() }, batches.flatten().map { it.elapsedMillis })
    }

    @Test fun sparseSamplesAreCommittedWithoutWaitingForTheNextSample() = runBlocking {
        val committed = CompletableDeferred<List<PerformancePoint>>()
        val writer = PerformanceWriter(this, { committed.complete(it); true }, { throw AssertionError(it) }, flushMillis = 20)
        writer.append(point(1))
        assertEquals(listOf(point(1)), withTimeout(2_000) { committed.await() })
        writer.closeAndFlush()
    }

    @Test fun slowDiskAppliesBoundedBackpressureWithoutDroppingSamples() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val stored = mutableListOf<PerformancePoint>()
        val writer = PerformanceWriter(this, {
            entered.complete(Unit); release.await(); stored.addAll(it); true
        }, { throw AssertionError(it) })
        repeat(10) { writer.append(point(it)) }
        withTimeout(2_000) { entered.await() }
        val producer = async(start = CoroutineStart.UNDISPATCHED) { repeat(21) { writer.append(point(10 + it)) } }
        assertFalse(producer.isCompleted)
        release.complete(Unit)
        withTimeout(2_000) { producer.await(); writer.closeAndFlush() }
        assertEquals((0..30).map { it.toLong() }, stored.map { it.elapsedMillis })
    }

    @Test fun failedTransactionIsReportedAndCannotPretendStopSavedTheQueue() = runBlocking {
        val error = CompletableDeferred<Exception>()
        val writer = PerformanceWriter(this, { false }, { error.complete(it) })
        repeat(10) { writer.append(point(it)) }
        withTimeout(2_000) { error.await() }
        try { writer.append(point(11)); fail("append after writer failure") } catch (_: IllegalStateException) { }
        try { writer.closeAndFlush(); fail("flush after writer failure") } catch (_: IllegalStateException) { }
    }
}
