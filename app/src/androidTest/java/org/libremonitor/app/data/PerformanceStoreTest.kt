/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.libremonitor.core.*

/** Requires an Android device/emulator; uses its own disposable database. */
@RunWith(AndroidJUnit4::class)
class PerformanceStoreTest {
    @Test fun finishedAndInterruptedSessionsSurviveReopeningAndRejectPostStopWrites() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "performance-test-${System.nanoTime()}.db"
        var store = PerformanceStore(context, name)
        try {
            store.initialize()
            val session = store.start("fixture", 1_000, 100)
            val point = PerformancePoint(100, 1_100, mapOf(
                PerformanceMetric.FPS to Reading(60.0, "synthetic frames"),
                PerformanceMetric.GPU to Reading.unavailable("KGSL", "denied", ReadingStatus.PERMISSION_DENIED)))
            assertTrue(store.append(session.id, point))
            store.finish(session.id, 1_200, 200)
            assertFalse(store.append(session.id, point.copy(elapsedMillis = 300)))
            val interrupted = store.start("interrupted", 2_000, 100)
            assertTrue(store.append(interrupted.id, point.copy(elapsedMillis = 500)))
            store.close()
            store = PerformanceStore(context, name)
            store.initialize()
            val recovered = store.sessions()
            assertEquals(2, recovered.size)
            assertEquals(PerformanceSession.INTERRUPTED, recovered.first().status)
            assertEquals(500L, recovered.first().durationMillis)
            assertEquals(PerformanceSession.COMPLETED, recovered.last().status)
            val review = store.review(session.id)
            assertEquals(1, review.session.sampleCount)
            assertEquals(60.0, review.stats[PerformanceMetric.FPS]!!.average!!, 0.0)
            assertEquals(ReadingStatus.PERMISSION_DENIED, review.lastReadings[PerformanceMetric.GPU]!!.status)
            assertEquals("denied", review.lastReadings[PerformanceMetric.GPU]!!.reason)
        } finally { store.close(); context.deleteDatabase(name) }
    }
}
