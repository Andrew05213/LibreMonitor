/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.data.privileged

import org.junit.Assert.*
import org.junit.Test

class SamplingNodesTest {
    @Test fun steadyStateKeepsLiveValuesButOmitsStaticDiscoveryAndBatteryMetadata() {
        val keys = RootProtocol.samplingNodes(true, false, false, setOf(1, 7)).map { it.key }
        assertEquals(20, keys.size) // CPU + 8 frequencies + 3 GPU + 2 discovered temperatures.
        assertTrue(keys.contains("/sys/class/thermal/thermal_zone7/temp"))
        assertFalse(keys.any { it.endsWith("/type") || it.endsWith("/charge_full") })
        assertFalse(keys.contains("/proc/net/dev"))
    }

    @Test fun discoveryAndNetworkRemainWithinTheExistingAllowlist() {
        val allowed = RootProtocol.compactNodes().toSet()
        val metadata = RootProtocol.samplingNodes(true, true, true, emptySet())
        assertEquals(allowed, metadata.toSet())
        assertEquals(5, RootProtocol.samplingNodes(false, true, false, emptySet()).size)
        RootProtocol.frameScript("a".repeat(32), metadata)
        try { RootProtocol.samplingNodes(true, false, false, setOf(32)); fail("unbounded zone") }
        catch (_: IllegalArgumentException) { }
        try { RootProtocol.frameScript("a".repeat(32), listOf(RootProtocol.Node("/tmp/arbitrary", 10, "echo bad"))); fail("arbitrary command") }
        catch (_: IllegalArgumentException) { }
    }
}
