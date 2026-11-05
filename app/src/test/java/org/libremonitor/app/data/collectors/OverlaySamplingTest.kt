/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.data.collectors

import org.junit.Assert.*
import org.junit.Test
import org.libremonitor.app.data.privileged.RootProtocol
import org.libremonitor.core.PrivilegedNodeFrame
import org.libremonitor.core.Reading

class OverlaySamplingTest {
    @Test fun overlayRootFrameContainsOnlyTheFixedCpuCounter() {
        val nodes = RootProtocol.overlayNodes()
        assertEquals(listOf("/proc/stat"), nodes.map { it.key })
        assertTrue(RootProtocol.compactNodes().containsAll(nodes))
        val script = RootProtocol.frameScript("0123456789abcdef0123456789abcdef", nodes)
        assertTrue(script.contains("/proc/stat"))
        assertFalse(script.contains("/sys/"))
        assertFalse(script.contains("dumpsys"))
    }

    @Test fun overlayCalculatesAggregateCpuAndResumingRebuildsItsBaseline() {
        val collector = CpuCollector()
        fun sample(user: Int, idle: Int, elapsed: Long) = collector.sample(elapsed,
            PrivilegedNodeFrame(mapOf("/proc/stat" to Reading("cpu $user 0 0 $idle 0 0 0\n", "synthetic")), elapsed, 1),
            includeDetails = false)
        assertFalse(sample(100, 100, 1_000).usagePercent.available)
        val next = sample(125, 175, 2_000)
        assertEquals(25.0, next.usagePercent.value!!, 0.0)
        assertTrue(next.perCoreUsagePercent.isEmpty())
        assertTrue(next.frequenciesMhz.isEmpty())
        assertFalse(next.temperatureC.available)
        collector.resetBaseline()
        assertFalse(sample(300, 500, 60_000).usagePercent.available)
        assertEquals(50.0, sample(350, 550, 61_000).usagePercent.value!!, 0.0)
    }
}
