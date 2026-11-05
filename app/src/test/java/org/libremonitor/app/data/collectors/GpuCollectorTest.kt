/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.data.collectors

import org.junit.Assert.*
import org.junit.Test
import org.libremonitor.app.data.privileged.RootProtocol
import org.libremonitor.core.PrivilegedNodeFrame
import org.libremonitor.core.Reading
import org.libremonitor.core.ReadingStatus
import java.io.ByteArrayInputStream
import java.util.Base64

class GpuCollectorTest {
    private val base = "/sys/class/kgsl/kgsl-3d0"
    private fun collect(percent: String? = null, pair: String? = null, clock: String? = "500000000") =
        GpuCollector.sample(null, 1_000) { path, limit ->
            assertEquals(256, limit)
            val value = when (path) {
                "$base/gpu_busy_percentage" -> percent
                "$base/gpubusy" -> pair
                "$base/gpuclk" -> clock
                else -> error("Unexpected path: $path")
            }
            value?.let { Reading(it, path) } ?: Reading.unavailable(path, "synthetic missing")
        }

    @Test fun acceptsDriverPercentageWithOrWithoutThePercentSuffix() {
        for ((text, expected) in listOf("35 %\n" to 35.0, "35%" to 35.0, " 12.5 % " to 12.5,
            "0 %" to 0.0, "100 %" to 100.0, "42\n" to 42.0)) {
            assertEquals(text, expected, collect(percent = text).usagePercent.value!!, 0.0)
        }
        assertEquals(500.0, collect(percent = "35 %").frequencyMhz.value!!, 0.0)
    }

    @Test fun rejectsMalformedAndOutOfRangePercentagesRatherThanExtractingAnyNumber() {
        for (text in listOf("", "NaN", "Infinity", "-1", "101 %", "35%%", "35 % garbage", "1 2", "0x20", "1e2")) {
            val sample = collect(percent = text)
            assertFalse(text, sample.usagePercent.available)
            assertEquals(ReadingStatus.ERROR, sample.usagePercent.status)
        }
    }

    @Test fun fallsBackToTheSameWindowBusyTotalWithoutRequiringADelta() {
        assertEquals(25.0, collect(pair = "250 1000\n").usagePercent.value!!, 0.0)
        assertEquals(50.0, collect(pair = "5 10").usagePercent.value!!, 0.0)
        assertEquals(0.0, collect(pair = "0 1000").usagePercent.value!!, 0.0)
        assertEquals(100.0, collect(pair = "9223372036854775807 9223372036854775807").usagePercent.value!!, 0.0)
        assertTrue(collect(pair = "25 100").usagePercent.reason!!.contains("最近统计窗口"))
    }

    @Test fun emptyOrInvalidCounterWindowsAreNeverReportedAsIdle() {
        for (text in listOf("0 0", "1 0", "101 100", "-1 100", "1 -100", "1", "1 2 3",
            "1.0 2", "9223372036854775808 9223372036854775809")) {
            assertFalse(text, collect(pair = text).usagePercent.available)
        }
    }

    @Test fun validDirectPercentageDoesNotReadTheFallbackCounter() {
        GpuCollector.sample(null, 1_000) { path, _ ->
            assertFalse(path.endsWith("/gpubusy"))
            Reading(if (path.endsWith("/gpuclk")) "500000000" else "35 %", path)
        }
    }

    @Test fun missingFrequencyDoesNotHideValidUsageAndViceVersa() {
        assertEquals(35.0, collect(percent = "35 %", clock = null).usagePercent.value!!, 0.0)
        assertFalse(collect(clock = "0").frequencyMhz.available)
        assertFalse(collect().usagePercent.available)
        assertEquals(500.0, collect().frequencyMhz.value!!, 0.0)
    }

    @Test fun permissionDenialIsPreservedWhenTheFallbackIsMissing() {
        val sample = GpuCollector.sample(null, 1_000) { path, _ ->
            Reading.unavailable<String>(path, "synthetic denied or missing",
                if (path.endsWith("gpu_busy_percentage")) ReadingStatus.PERMISSION_DENIED else ReadingStatus.UNSUPPORTED)
        }
        assertEquals(ReadingStatus.PERMISSION_DENIED, sample.usagePercent.status)
        assertTrue(sample.usagePercent.reason!!.contains("Root"))
        assertTrue(sample.usagePercent.reason!!.contains("gpubusy"))
    }

    @Test fun malformedPrimaryCanFallBackWithoutDroppingTheMetric() {
        assertEquals(25.0, collect(percent = "broken", pair = "25 100").usagePercent.value!!, 0.0)
    }

    @Test fun actualRootProtocolDecodingFeedsTheCollectorWithoutOrdinaryReads() {
        val id = "0123456789abcdef0123456789abcdef"
        val nodes = RootProtocol.compactNodes()
        val wire = buildString {
            append("LM:$id:BEGIN\n")
            nodes.forEachIndexed { index, node ->
                val text = when (node.key) {
                    "$base/gpuclk" -> "500000000\n"
                    "$base/gpu_busy_percentage" -> "35 %\n"
                    "$base/gpubusy" -> "35 100\n"
                    else -> ""
                }
                append("LM:$id:$index:${Base64.getEncoder().encodeToString(text.toByteArray())}\n")
            }
            append("LM:$id:END\n")
        }
        val readings = RootProtocol.readFrame(ByteArrayInputStream(wire.toByteArray()), id, nodes)
        val sample = GpuCollector.sample(PrivilegedNodeFrame(readings, 1_000, 1), 3_000) { _, _ -> error("unnecessary ordinary read") }
        assertEquals(35.0, sample.usagePercent.value!!, 0.0)
        assertTrue(sample.usagePercent.source.startsWith("Root "))
        val gpuKeys = nodes.filter { it.key.startsWith(base) }.map { it.key }
        assertTrue(gpuKeys.indexOf("$base/gpu_busy_percentage") < gpuKeys.indexOf("$base/gpubusy"))
    }

    @Test fun staleAndFutureFramesAreNotReusedAsFreshData() {
        for (frameTime in listOf(999L, 3_001L)) {
            val frame = PrivilegedNodeFrame(mapOf("$base/gpu_busy_percentage" to Reading("99 %", "root")), frameTime, 1)
            val result = GpuCollector.sample(frame, 3_000) { path, _ ->
                if (path.endsWith("gpu_busy_percentage")) Reading("10 %", path)
                else Reading.unavailable(path, "synthetic missing")
            }
            assertEquals(10.0, result.usagePercent.value!!, 0.0)
        }
    }
}
