/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.core

import org.junit.Assert.*
import org.junit.Test

class SamplingPlanTest {
    @Test fun fastLiveAndOverlayCadenceIncludesCollectionTime() {
        val settings = MonitorSettings(sampleIntervalMs = 100, historyEnabled = true)
        val live = SamplingPlan.forDemand(true, true, true, true, settings)
        val overlay = SamplingPlan.forDemand(false, true, true, true, settings)
        for (plan in listOf(live, overlay)) {
            var elapsed = 0L
            repeat(10) {
                val collectionTime = 40L
                elapsed += collectionTime + plan.delayAfterSampleMillis(collectionTime)
            }
            assertEquals(1_000L, elapsed)
            assertEquals(0L, plan.delayAfterSampleMillis(150))
            assertEquals(100L, plan.delayAfterSampleMillis(-1))
        }
        val history = SamplingPlan.forDemand(false, true, false, true, settings)
        assertEquals(SamplingPlan.Mode.HISTORY, history.mode)
        assertEquals(60_000L, history.delayAfterSampleMillis(40))
    }

    @Test fun exhaustiveConsumerAndScreenCombinations() {
        for (ui in listOf(false, true)) for (service in listOf(false, true)) {
            for (screen in listOf(false, true)) for (overlay in listOf(false, true)) {
                for (history in listOf(false, true)) {
                    val plan = SamplingPlan.forDemand(ui, service, screen, overlay,
                        MonitorSettings(historyEnabled = history))
                    val expected = when {
                        screen && ui -> SamplingPlan.Mode.LIVE
                        screen && service && overlay -> SamplingPlan.Mode.OVERLAY
                        service && history -> SamplingPlan.Mode.HISTORY
                        else -> SamplingPlan.Mode.IDLE
                    }
                    assertEquals("ui=$ui service=$service screen=$screen overlay=$overlay history=$history",
                        expected, plan.mode)
                    assertEquals(service && history, plan.recordHistory)
                }
            }
        }
    }

    @Test fun recordingSamplesInBackgroundWithoutAnOverlayAndPausesOnScreenOff() {
        val settings = MonitorSettings(sampleIntervalMs = 100)
        val recording = SamplingPlan.forDemand(false, true, true, false, settings, performanceRecording = true)
        assertEquals(SamplingPlan.Mode.PERFORMANCE, recording.mode)
        assertTrue(recording.needsSnapshot)
        assertTrue(recording.recordPerformance)
        assertEquals(60L, recording.delayAfterSampleMillis(40))
        val screenOff = SamplingPlan.forDemand(false, true, false, true, settings, performanceRecording = true)
        assertEquals(SamplingPlan.Mode.IDLE, screenOff.mode)
        assertFalse(screenOff.recordPerformance)
        val screenOffHistory = SamplingPlan.forDemand(false, true, false, true,
            settings.copy(historyEnabled = true), performanceRecording = true)
        assertEquals(SamplingPlan.Mode.HISTORY, screenOffHistory.mode)
        assertFalse(screenOffHistory.recordPerformance)
        val noService = SamplingPlan.forDemand(false, false, true, true, settings, performanceRecording = true)
        assertEquals(SamplingPlan.Mode.IDLE, noService.mode)
        assertFalse(noService.recordPerformance)
    }

    @Test fun backgroundRecordingWaitsForRemainingCadenceInsteadOfRestartingTheMinute() {
        val cadence = HistoryCadence()
        assertEquals(0L, cadence.remainingMillis(1_000, 60_000))
        cadence.recorded(1_000)
        assertEquals(40_000L, cadence.remainingMillis(21_000, 60_000))
        // Returning to UI or switching off the overlay does not reset the ledger.
        assertEquals(1L, cadence.remainingMillis(60_999, 60_000))
        assertEquals(0L, cadence.remainingMillis(61_000, 60_000))
        assertEquals(0L, cadence.remainingMillis(90_000, 60_000))
    }

    @Test fun intervalChangesClockRollbackAndExplicitRestart() {
        val cadence = HistoryCadence()
        cadence.recorded(50_000)
        assertEquals(5_000L, cadence.remainingMillis(55_000, 10_000))
        assertEquals(55_000L, cadence.remainingMillis(55_000, 60_000))
        assertEquals(0L, cadence.remainingMillis(49_000, 60_000))
        cadence.reset()
        assertEquals(0L, cadence.remainingMillis(55_000, 60_000))
    }

    @Test fun oneHourOfDefaultBackgroundRecordingNeedsOnlySixtyBatterySamples() {
        val plan = SamplingPlan.forDemand(false, true, true, false,
            MonitorSettings(historyEnabled = true))
        assertEquals(SamplingPlan.Mode.HISTORY, plan.mode)
        val cadence = HistoryCadence()
        var now = 0L
        var samples = 0
        while (now < 3_600_000L) {
            now += cadence.remainingMillis(now, plan.historyIntervalMs)
            if (now >= 3_600_000L) break
            samples++
            cadence.recorded(now)
        }
        assertEquals(60, samples)
    }
}
