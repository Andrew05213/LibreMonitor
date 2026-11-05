/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.core

import org.junit.Assert.*
import org.junit.Test

class PerformanceTest {
    private fun session(count: Int) = PerformanceSession(1, "Game", 123, durationMillis = count * 100L,
        sampleIntervalMs = 100, sampleCount = count, status = PerformanceSession.COMPLETED)
    private fun point(time: Long, vararg values: Pair<PerformanceMetric, Reading<Double>>) =
        PerformancePoint(time, 123 + time, values.toMap())

    @Test fun summaryKeepsMissingValuesOutOfDenominatorsAndPreservesZeroAndPowerSign() {
        val points = sequenceOf(
            point(0, PerformanceMetric.FPS to Reading(60.0, "frames"), PerformanceMetric.CPU to Reading(0.0, "cpu"),
                PerformanceMetric.POWER to Reading(-4.0, "battery")),
            point(100, PerformanceMetric.FPS to Reading.unavailable("frames", "permission", ReadingStatus.PERMISSION_DENIED),
                PerformanceMetric.CPU to Reading(50.0, "cpu"), PerformanceMetric.POWER to Reading(5.0, "battery")),
            point(200, PerformanceMetric.FPS to Reading(30.0, "frames"), PerformanceMetric.CPU to Reading(Double.NaN, "bad")),
        )
        val review = PerformanceReview.build(session(3), points)
        assertEquals(2, review.stats[PerformanceMetric.FPS]!!.validCount)
        assertEquals(45.0, review.stats[PerformanceMetric.FPS]!!.average!!, 0.0)
        assertEquals(25.0, review.stats[PerformanceMetric.CPU]!!.average!!, 0.0)
        assertEquals(0.0, review.stats[PerformanceMetric.CPU]!!.minimum!!, 0.0)
        assertEquals(0.5, review.stats[PerformanceMetric.POWER]!!.average!!, 0.0)
        assertNull(review.stats[PerformanceMetric.GPU]!!.average)
    }

    @Test fun summaryUsesAllRowsEvenWhenChartPreviewIsBounded() {
        val review = PerformanceReview.build(session(10_000), (0 until 10_000).asSequence().map {
            point(it * 100L, PerformanceMetric.FPS to Reading(if (it == 5_001) 1.0 else 60.0, "synthetic"))
        })
        assertEquals(10_000, review.stats[PerformanceMetric.FPS]!!.validCount)
        assertEquals(1.0, review.stats[PerformanceMetric.FPS]!!.minimum!!, 0.0)
        assertTrue(review.timeline.size <= 601)
        assertEquals(999_900L, review.timeline.last().elapsedMillis)
    }

    @Test fun emptySessionHasNoFabricatedStatistics() {
        val review = PerformanceReview.build(session(0), emptySequence())
        assertTrue(review.timeline.isEmpty())
        review.stats.values.forEach { assertEquals(0, it.validCount); assertNull(it.average); assertNull(it.minimum); assertNull(it.maximum) }
    }

    @Test fun missingRowsBetweenPreviewPointsBreakTheChartLine() {
        val review = PerformanceReview.build(session(1_200), (0 until 1_200).asSequence().map {
            point(it * 100L, PerformanceMetric.FPS to if (it == 1) Reading.unavailable("frames", "gap") else Reading(60.0, "frames"))
        })
        assertEquals(1_199, review.stats[PerformanceMetric.FPS]!!.validCount)
        assertTrue(PerformanceMetric.FPS in review.gapsBefore[200L].orEmpty())
    }

    @Test fun compressedPayloadRoundTripRetainsReasonsSourcesClockAndFrameTarget() {
        val point = point(500, PerformanceMetric.GPU to Reading.unavailable("KGSL", "系统拒绝读取", ReadingStatus.PERMISSION_DENIED),
            PerformanceMetric.POWER to Reading(-4.5, "V × CURRENT_NOW", reason = "原始正负号"),
            PerformanceMetric.CPU to Reading(0.0, "proc"))
            .copy(target = Reading("SurfaceView[example.game/.Main]", "SurfaceFlinger"))
        assertEquals(point, PerformanceCodec.decode(PerformanceCodec.encode(point)))
    }

    @Test fun longDiagnosticsRemainWithinPayloadLimits() {
        val point = PerformancePoint(100, 100, PerformanceMetric.entries.associateWith {
            Reading.unavailable<Double>("源".repeat(5_000), "原因".repeat(5_000), ReadingStatus.PERMISSION_DENIED)
        })
        val decoded = PerformanceCodec.decode(PerformanceCodec.encode(point))
        assertEquals(PerformanceMetric.entries.size, decoded.readings.size)
        assertTrue(decoded.readings.values.all { !it.available && it.status == ReadingStatus.PERMISSION_DENIED })
    }

    @Test fun corruptPayloadIsRejected() {
        try {
            PerformanceCodec.decode(byteArrayOf(0, 1, 2, 3))
            fail("Corrupt payload was accepted")
        } catch (_: java.io.IOException) { }
    }

    @Test fun conversionKeepsFrequencyAndPowerAndRejectsInvalidMemory() {
        val snapshot = MonitorSnapshot(5_000, 2_000,
            CpuSample(Reading(20.0, "cpu"), emptyList(), listOf(Reading(1_000L, "cpu0"), Reading(2_000L, "cpu1")), Reading(50.0, "thermal")),
            MemorySample(Reading(100L, "memory"), Reading(25L, "memory"), Reading(0L, "swap"), Reading(0L, "swap")),
            BatterySample(Reading(80, "battery"), Reading(35.0, "battery"), Reading(4.0, "battery"), Reading(-1_000.0, "battery"),
                Reading(1_000.0, "battery"), Reading(-4.0, "battery"), "discharging", null),
            NetworkSample(Reading.unavailable("network", "skipped"), Reading.unavailable("network", "skipped"),
                Reading.unavailable("network", "skipped"), Reading.unavailable("network", "skipped")),
            StorageSample(Reading.unavailable("storage", "skipped"), Reading.unavailable("storage", "skipped")),
            GpuSample(usagePercent = Reading.unavailable("KGSL", "denied", ReadingStatus.PERMISSION_DENIED), frequencyMhz = Reading(600.0, "KGSL")))
        val point = PerformancePoint.from(snapshot, 1_000)
        assertEquals(1_000L, point.elapsedMillis)
        assertEquals(75.0, point.readings[PerformanceMetric.MEMORY]!!.value!!, 0.0)
        assertEquals(1_500.0, point.readings[PerformanceMetric.CPU_FREQUENCY]!!.value!!, 0.0)
        assertEquals(600.0, point.readings[PerformanceMetric.GPU_FREQUENCY]!!.value!!, 0.0)
        assertEquals(-4.0, point.readings[PerformanceMetric.POWER]!!.value!!, 0.0)
        assertEquals(ReadingStatus.PERMISSION_DENIED, point.readings[PerformanceMetric.GPU]!!.status)
        val invalidMemory = snapshot.copy(memory = snapshot.memory.copy(availableBytes = Reading(101L, "bad")))
        assertFalse(PerformancePoint.from(invalidMemory, 1_000).readings[PerformanceMetric.MEMORY]!!.available)
        val denied = snapshot.copy(cpu = snapshot.cpu.copy(frequenciesMhz = listOf(
            Reading.unavailable("cpufreq", "denied", ReadingStatus.PERMISSION_DENIED))),
            memory = snapshot.memory.copy(availableBytes = Reading.unavailable("mem", "denied", ReadingStatus.PERMISSION_DENIED)))
        val deniedPoint = PerformancePoint.from(denied, 1_000)
        assertEquals(ReadingStatus.PERMISSION_DENIED, deniedPoint.readings[PerformanceMetric.CPU_FREQUENCY]!!.status)
        assertEquals(ReadingStatus.PERMISSION_DENIED, deniedPoint.readings[PerformanceMetric.MEMORY]!!.status)
    }
}
