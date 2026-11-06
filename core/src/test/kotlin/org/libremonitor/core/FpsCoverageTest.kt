/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.core

import org.junit.Assert.*
import org.junit.Test

class FpsCoverageTest {
    @Test fun oneValidSampleCannotBecomeAHeadlineGameAverageAndFailuresSurviveReview() {
        val session = PerformanceSession(1, "fixture", 1_000, sampleIntervalMs = 100, sampleCount = 194)
        val review = PerformanceReview.build(session, (0 until 194).asSequence().map { index ->
            PerformancePoint(index * 100L, 1_000 + index * 100L, mapOf(PerformanceMetric.FPS to
                if (index == 0) Reading(120.8, "fixture") else Reading.unavailable("SurfaceFlinger", "header only")),
                Reading(if (index == 0) "monitor/layer" else "game/layer", "fixture"))
        })
        assertFalse(review.fpsSummaryAvailable)
        assertEquals(1, review.stats[PerformanceMetric.FPS]!!.validCount)
        assertEquals(193, review.fpsFailures["header only"])
        assertEquals(193, review.fpsTargets["game/layer"])
        assertEquals(mapOf("monitor/layer" to 1), review.fpsValidTargets)
        assertEquals(120.8, review.stats[PerformanceMetric.FPS]!!.average!!, 0.0)
    }

    @Test fun headlineRequiresBothMinimumCountAndCoverage() {
        fun review(total: Int, valid: Int): PerformanceReview = PerformanceReview.build(
            PerformanceSession(1, "fixture", 1_000, sampleIntervalMs = 100, sampleCount = total),
            (0 until total).asSequence().map { i -> PerformancePoint(i.toLong(), i.toLong(), mapOf(PerformanceMetric.FPS to
                if (i < valid) Reading(60.0, "fixture") else Reading.unavailable("fixture", "missing"))) })
        assertFalse(review(10, 9).fpsSummaryAvailable)
        assertFalse(review(100, 49).fpsSummaryAvailable)
        assertTrue(review(100, 50).fpsSummaryAvailable)
    }
}
