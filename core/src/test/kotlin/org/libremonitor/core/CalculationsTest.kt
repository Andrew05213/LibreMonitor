/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.core

import org.junit.Assert.*
import org.junit.Test
import java.io.StringWriter

class CalculationsTest {
    /** Synthetic /proc/stat-style row: user, nice, system, idle, iowait, irq, softirq, steal. */
    private fun timesOf(vararg counters: Int): CpuTimes {
        val components = counters.map(Int::toLong)
        return CpuTimes(components.sum(), components[3] + components.getOrElse(4) { 0L }, components)
    }

    @Test fun cpuParserDoesNotDoubleCountGuest() {
        val parsed = CpuStatParser.parse("cpu 1 2 3 4 5 6 7 8 100 200\ncpu0 10 0 20 70\nintr 99")
        assertEquals(CpuTimes(36, 9, listOf(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L)), parsed["cpu"])
        assertEquals(CpuTimes(100, 70, listOf(10L, 0L, 20L, 70L)), parsed["cpu0"])
        assertFalse(parsed.containsKey("intr"))
    }
    @Test fun malformedCpuRowsRemainMissing() {
        assertTrue(CpuStatParser.parse("cpu 1 2\ncpu0 -1 0 0 1\ncpu1 x 0 0 1\ncpuX 1 2 3 4").isEmpty())
    }
    @Test fun overflowCpuRowIsRejected() {
        assertTrue(CpuStatParser.parse("cpu ${Long.MAX_VALUE} 1 0 0").isEmpty())
    }
    @Test fun cpuPercentUsesIntervalNotBootAverage() {
        assertEquals(60.0, CpuUsage.percent(CpuTimes(100, 60), CpuTimes(200, 100))!!, 0.00001)
    }
    @Test fun idleCpuIsActuallyZero() {
        assertEquals(0.0, CpuUsage.percent(CpuTimes(100, 60), CpuTimes(200, 160))!!, 0.0)
    }
    @Test fun busyCpuIsActuallyOneHundred() {
        assertEquals(100.0, CpuUsage.percent(CpuTimes(100, 60), CpuTimes(200, 60))!!, 0.0)
    }
    @Test fun cpuResetIsNotZeroUsage() {
        assertNull(CpuUsage.percent(CpuTimes(100, 60), CpuTimes(90, 50)))
        assertNull(CpuUsage.percent(CpuTimes(100, 60), CpuTimes(100, 60)))
        assertNull(CpuUsage.percent(CpuTimes(100, 60), CpuTimes(110, 80)))
    }
    @Test fun maskedPerCoreResetIsMissingWhenTotalsGrow() {
        val before = CpuStatParser.parse("cpu 100 0 50 200 10 5 5 0").getValue("cpu")
        val after = CpuStatParser.parse("cpu 120 0 40 230 12 6 6 0").getValue("cpu")
        assertTrue(after.total > before.total && after.idle > before.idle)
        assertNull(CpuUsage.percent(before, after))
    }
    @Test fun maskedIowaitDropIsMissingWithoutClaimingReboot() {
        val before = CpuStatParser.parse("cpu 100 0 50 200 10 5 5 0").getValue("cpu")
        val after = CpuStatParser.parse("cpu 100 0 50 230 5 5 5 0").getValue("cpu")
        assertTrue(after.total > before.total && after.idle > before.idle)
        assertNull(CpuUsage.percent(before, after))
    }
    @Test fun guestColumnsAreIgnoredInsteadOfAccumulated() {
        val before = CpuStatParser.parse("cpu 10 0 5 20 1 1 1 1 100 50").getValue("cpu")
        assertEquals(39L, before.total)
        assertEquals(21L, before.idle)
        assertEquals(listOf(10L, 0L, 5L, 20L, 1L, 1L, 1L, 1L), before.components)
        val after = CpuStatParser.parse("cpu 15 0 5 20 1 1 1 1 900 700").getValue("cpu")
        assertEquals(100.0, CpuUsage.percent(before, after)!!, 0.0)
    }
    @Test fun componentStructureChangeHasNoValue() {
        val short = timesOf(20, 0, 10, 40, 2)
        val long = timesOf(30, 0, 15, 60, 3, 3, 3, 3)
        assertTrue(long.total > short.total && long.idle > short.idle)
        assertNull(CpuUsage.percent(short, long))
        assertNull(CpuUsage.percent(timesOf(1, 0, 1, 1, 0, 0, 0, 0), short))
    }
    @Test fun oneSidedComponentsFallBackToAggregateChecks() {
        assertEquals(100.0 / 3.0, CpuUsage.percent(timesOf(10, 0, 5, 50), CpuTimes(140, 100))!!, 0.00001)
    }
    @Test fun duplicateAggregateRowBecomesMissing() {
        val parsed = CpuStatParser.parse(
            "cpu 10 0 5 20 1 1 1 1\ncpu0 30 0 10 60\ncpu 50 0 5 20 1 1 1 1",
        )
        assertFalse(parsed.containsKey("cpu"))
        assertEquals(CpuTimes(100, 60, listOf(30L, 0L, 10L, 60L)), parsed["cpu0"])
    }
    @Test fun duplicateCoreRowDoesNotCorruptNeighbours() {
        val parsed = CpuStatParser.parse("cpu0 10 0 5 20\ncpu1 11 0 5 20\ncpu1 12 0 5 20\ncpu2 13 0 5 20")
        assertFalse(parsed.containsKey("cpu1"))
        assertEquals(CpuTimes(35, 20, listOf(10L, 0L, 5L, 20L)), parsed["cpu0"])
        assertEquals(CpuTimes(38, 20, listOf(13L, 0L, 5L, 20L)), parsed["cpu2"])
    }
    @Test fun sparseCoreNamesStayDistinct() {
        val parsed = CpuStatParser.parse("cpu0 10 0 5 20\ncpu4 14 0 5 20\ncpu7 17 0 5 20")
        assertEquals(setOf("cpu0", "cpu4", "cpu7"), parsed.keys)
        assertEquals(39L, parsed.getValue("cpu4").total)
        assertFalse(parsed.containsKey("cpu1"))
    }
    @Test fun hotPluggedCoreHasNoBorrowedBaseline() {
        val before = CpuStatParser.parse("cpu0 40 0 10 50\ncpu1 20 0 5 30")
        val after = CpuStatParser.parse("cpu0 50 0 10 60\ncpu1 25 0 5 35\ncpu2 5 0 1 2")
        assertFalse(before.containsKey("cpu2"))
        assertEquals(50.0, CpuUsage.percent(before.getValue("cpu0"), after.getValue("cpu0"))!!, 0.0)
        assertEquals(50.0, CpuUsage.percent(before.getValue("cpu1"), after.getValue("cpu1"))!!, 0.0)
    }
    @Test fun hotRemovedCoreStaysMissingNotZero() {
        val before = CpuStatParser.parse("cpu0 10 0 5 20\ncpu1 10 0 5 20")
        val after = CpuStatParser.parse("cpu0 15 0 5 25")
        assertTrue(before.containsKey("cpu1"))
        assertFalse(after.containsKey("cpu1"))
        assertEquals(50.0, CpuUsage.percent(before.getValue("cpu0"), after.getValue("cpu0"))!!, 0.0)
    }
    @Test fun overflowedRowDoesNotLeakIntoOtherCores() {
        val parsed = CpuStatParser.parse("cpu0 ${Long.MAX_VALUE} 1 0 0\ncpu1 5 0 1 10")
        assertFalse(parsed.containsKey("cpu0"))
        assertEquals(CpuTimes(16, 10, listOf(5L, 0L, 1L, 10L)), parsed["cpu1"])
    }
    @Test fun networkRateUsesElapsedMillis() {
        assertEquals(250.0, CounterRate.bytesPerSecond(1_000, 1_500, 2_000)!!, 0.0)
    }
    @Test fun networkNoTrafficIsZeroButResetIsMissing() {
        assertEquals(0.0, CounterRate.bytesPerSecond(10, 10, 1_000)!!, 0.0)
        assertNull(CounterRate.bytesPerSecond(10, 5, 1_000))
        assertNull(CounterRate.bytesPerSecond(-1, 5, 1_000))
        assertNull(CounterRate.bytesPerSecond(5, 10, 0))
        assertNull(CounterRate.bytesPerSecond(5, 10, -1))
    }
    @Test fun memoryKibibytesUse1024() {
        val parsed = MemInfoParser.parseBytes("MemTotal: 1024 kB\nSwapFree: 0 kB\nBytes: 7 B")
        assertEquals(1_048_576L, parsed["MemTotal"])
        assertEquals(0L, parsed["SwapFree"])
        assertEquals(7L, parsed["Bytes"])
    }
    @Test fun memoryInvalidAndOverflowAreAbsent() {
        assertTrue(MemInfoParser.parseBytes("MemTotal: -1 kB\nSwapTotal: x kB\nBad: 1 MB\nBig: ${Long.MAX_VALUE} kB").isEmpty())
    }
    @Test fun unavailableReadingHasNoValue() {
        val value = Reading.unavailable<Double>("proc", "permission denied", ReadingStatus.PERMISSION_DENIED)
        assertFalse(value.available)
        assertNull(value.value)
        assertEquals(ReadingStatus.PERMISSION_DENIED, value.status)
    }
    @Test(expected = IllegalArgumentException::class) fun invalidAvailableReadingFails() {
        Reading<Double>(null, "test", ReadingStatus.AVAILABLE)
    }
    @Test(expected = IllegalArgumentException::class) fun invalidMissingReadingFails() {
        Reading(42, "test", ReadingStatus.UNSUPPORTED)
    }
    @Test fun realZeroRemainsAvailable() {
        assertTrue(Reading(0.0, "test").available)
    }
    @Test fun csvPreservesNullAndNumericNegative() {
        val writer = StringWriter()
        BatteryCsv.writeHeader(writer)
        BatteryCsv.writeRecord(writer, BatteryRecord(1, null, 20.0, 4.0, -250.0, -1.0, "放电"))
        assertTrue(writer.toString().endsWith("1,,20.0,4.0,-250.0,-1.0,放电\r\n"))
    }
    @Test fun csvEscapesQuotesAndNewlines() {
        val writer = StringWriter()
        BatteryCsv.writeRecord(writer, BatteryRecord(1, 50, null, null, null, null, "x,\"y\"\nz"))
        assertTrue(writer.toString().endsWith("\"x,\"\"y\"\"\nz\"\r\n"))
    }
    @Test fun csvDefendsVendorStatusFormulaInjection() {
        listOf("=SUM(1)", "+cmd", "@cmd", "-cmd").forEach { text ->
            val writer = StringWriter()
            BatteryCsv.writeRecord(writer, BatteryRecord(1, null, null, null, null, null, text))
            assertTrue(writer.toString().contains("'$text"))
        }
    }
    @Test fun csvRejectsNonFiniteMeasurements() {
        val writer = StringWriter()
        BatteryCsv.writeRecord(writer, BatteryRecord(1, null, Double.NaN, Double.POSITIVE_INFINITY, null, null, "ok"))
        assertEquals("1,,,,,,ok\r\n", writer.toString())
    }
    @Test fun csvDefendsWhitespacePrefixedFormula() {
        val writer = StringWriter()
        BatteryCsv.writeRecord(writer, BatteryRecord(1, null, null, null, null, null, "\t=1+1"))
        assertTrue(writer.toString().endsWith("'\t=1+1\r\n"))
    }
    @Test fun malformedCpuObjectsHaveNoUsage() {
        assertNull(CpuUsage.percent(CpuTimes(-1, 0), CpuTimes(10, 2)))
        assertNull(CpuUsage.percent(CpuTimes(10, 11), CpuTimes(20, 12)))
    }
    @Test fun defaultSettingsAreOptInAndBounded() {
        val settings = MonitorSettings()
        assertFalse(settings.historyEnabled)
        assertEquals(2_000L, settings.sampleIntervalMs)
        assertEquals(60_000L, settings.historyIntervalMs)
        assertEquals(7, settings.retentionDays)
    }
    @Test(expected = IllegalArgumentException::class) fun invalidSamplingIntervalFails() {
        MonitorSettings(sampleIntervalMs = 0)
    }
    @Test fun fastSamplingIntervalsAndBoundaries() {
        listOf(100L, 200L, 500L, 1_000L, 2_000L, 5_000L, 10_000L).forEach {
            assertEquals(it, MonitorSettings(sampleIntervalMs = it).sampleIntervalMs)
        }
    }
    @Test(expected = IllegalArgumentException::class) fun samplingBelowOneHundredMillisFails() {
        MonitorSettings(sampleIntervalMs = 99)
    }
    @Test(expected = IllegalArgumentException::class) fun samplingAboveTenSecondsFails() {
        MonitorSettings(sampleIntervalMs = 10_001)
    }
    @Test fun unimplementedBackendsAreExplicitlyUnavailable() {
        assertTrue(BackendCapabilities.initial.single { it.backend == BackendKind.BASIC }.available)
        assertFalse(BackendCapabilities.initial.single { it.backend == BackendKind.SHIZUKU }.available)
        assertFalse(BackendCapabilities.initial.single { it.backend == BackendKind.ROOT }.available)
    }
}
