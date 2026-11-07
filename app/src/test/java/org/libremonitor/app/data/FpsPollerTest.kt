/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.data

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.libremonitor.core.*
import java.util.concurrent.atomic.AtomicInteger

class FpsPollerTest {
    @Test fun aSlowQueryDoesNotBlockFastPollsOrLaunchOverlappingQueries() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        var now = 1_000L
        val poller = FpsPoller(this, { now }) {
            calls.incrementAndGet(); started.complete(Unit); release.await()
            FpsPoller.Result(FpsSample(fps = Reading(60.0, "fixture")))
        }
        assertFalse(poller.poll(100).sample.hasNewReading)
        withTimeout(2_000) { started.await() }
        repeat(20) { now += 100; assertFalse(poller.poll(100).sample.hasNewReading) }
        assertEquals(1, calls.get())
        poller.reset()
        release.complete(Unit)
        Unit
    }

    @Test fun oneCompletedQueryIsConsumedOnceRatherThanInflatingTheRecordedFpsCoverage() = runBlocking {
        val completed = CompletableDeferred<Unit>()
        val poller = FpsPoller(this, { 1_000L }) {
            completed.complete(Unit); FpsPoller.Result(FpsSample(fps = Reading(60.0, "fixture")))
        }
        poller.poll(100)
        withTimeout(2_000) { completed.await() }
        var result: FpsPoller.Result
        withTimeout(2_000) {
            do { yield(); result = poller.poll(100) } while (!result.sample.hasNewReading)
        }
        assertFalse(poller.poll(100).sample.hasNewReading)
        poller.reset()
    }

    @Test fun resetRejectsALateResponseEvenWhenTheReaderIgnoresCancellation() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val returned = CompletableDeferred<Unit>()
        val poller = FpsPoller(this, { 1_000L }) {
            withContext(NonCancellable) { entered.complete(Unit); release.await() }
            returned.complete(Unit)
            FpsPoller.Result(FpsSample(fps = Reading(60.0, "late")))
        }
        poller.poll(100); withTimeout(2_000) { entered.await() }
        poller.reset(); release.complete(Unit); withTimeout(2_000) { returned.await() }
        assertFalse(poller.poll(100).sample.hasNewReading)
        poller.reset()
    }
}
