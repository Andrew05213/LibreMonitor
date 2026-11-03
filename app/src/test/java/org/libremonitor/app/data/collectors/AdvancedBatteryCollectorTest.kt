/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.data.collectors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.libremonitor.core.BatterySample
import org.libremonitor.core.PrivilegedNodeFrame
import org.libremonitor.core.Reading

/**
 * Pure JVM tests. They exercise parsing, identity pairing, source/failure selection and
 * freshness boundaries with an injected byte reader; no Android device or node is touched.
 */
class AdvancedBatteryCollectorTest {
    private val now = 100_000L

    private fun node(dir: String, key: String) = "/sys/class/power_supply/$dir/$key"

    private class FakeNodes(private val values: Map<String, String>) {
        val reads = mutableListOf<String>()
        val limits = mutableListOf<Int>()

        fun read(path: String, maxBytes: Int): Reading<String> {
            reads += path
            limits += maxBytes
            val value = values[path] ?: return Reading.unavailable(path, "No such file")
            return Reading(value, path)
        }
    }

    private fun baseSample(
        cycle: Reading<Long> = Reading.unavailable("public", "Not sampled"),
    ) = BatterySample(
        levelPercent = Reading(50, "public:level"),
        temperatureC = Reading(25.0, "public:temperature"),
        voltageV = Reading(3.9, "public:voltage"),
        currentMa = Reading(-1_200.0, "public:current"),
        chargeCounterMah = Reading(2_500.0, "public:charge_counter"),
        powerW = Reading(-4.68, "public:power"),
        status = "Charging",
        technology = "Li-ion",
        cycleCount = cycle,
    )

    private fun enrichWith(
        nodes: FakeNodes,
        frame: PrivilegedNodeFrame? = null,
        at: Long = 100_000L,
        base: BatterySample = baseSample(),
    ): BatterySample = AdvancedBatteryCollector.enrich(base, frame, at, nodes::read)

    private fun frame(sampledAt: Long, values: Map<String, String>) = PrivilegedNodeFrame(
        readings = values.mapValues { (path, value) -> Reading(value, path) },
        sampledElapsedRealtimeMs = sampledAt,
        sessionId = 7L,
    )

    @Test
    fun chargePairFromSameObjectProducesMahHealthAndPositiveCycles() {
        val nodes = FakeNodes(
            mapOf(
                node("battery", "type") to "Battery\n",
                node("battery", "charge_full") to "4000000\n",
                node("battery", "charge_full_design") to "5000000\n",
                node("battery", "cycle_count") to "321\n",
                node("battery", "state_of_health") to "77\n",
                node("battery", "health") to "Good\n",
                node("bms", "type") to "BMS\n",
            ),
        )

        val sample = enrichWith(nodes)

        assertEquals(5000.0, sample.designCapacityMah.value!!, 0.0001)
        assertEquals(4000.0, sample.fullChargeCapacityMah.value!!, 0.0001)
        assertEquals(80.0, sample.healthPercent.value!!, 0.0001)
        assertEquals(321L, sample.cycleCount.value)
        assertEquals("GOOD", sample.healthStatus.value)
        assertTrue(sample.healthPercent.source.contains("charge_full"))
        assertTrue(sample.healthPercent.source.contains("charge_full_design"))
        // state_of_health was present but FCC/design must win.
        assertFalse(sample.healthPercent.source.contains("state_of_health"))
    }

    @Test
    fun typeMatchIsCaseInsensitiveAndEveryReadIsBoundedAndAllowlisted() {
        val nodes = FakeNodes(
            mapOf(
                node("battery", "type") to "bAtTeRy",
                node("battery", "charge_full") to "4000000",
                node("battery", "charge_full_design") to "5000000",
                node("battery", "cycle_count") to "12",
            ),
        )

        val sample = enrichWith(nodes)

        assertEquals(4000.0, sample.fullChargeCapacityMah.value!!, 0.0001)
        assertEquals(12L, sample.cycleCount.value)
        assertTrue(nodes.limits.isNotEmpty())
        assertTrue(nodes.limits.all { it == 256 })
        assertTrue(
            nodes.reads.all {
                it.startsWith("/sys/class/power_supply/battery/") ||
                    it.startsWith("/sys/class/power_supply/bms/")
            },
        )
        // Linux charge_counter is a different quantity and is never read or mapped.
        assertFalse(nodes.reads.any { it.endsWith("charge_counter") })
    }

    @Test
    fun doesNotPairChargeFullWithDesignFromAnotherObject() {
        val nodes = FakeNodes(
            mapOf(
                node("battery", "type") to "Battery",
                node("battery", "charge_full") to "4000000",
                node("bms", "type") to "Battery",
                node("bms", "charge_full_design") to "5000000",
            ),
        )

        val sample = enrichWith(nodes)

        assertEquals(4000.0, sample.fullChargeCapacityMah.value!!, 0.0001)
        assertFalse(sample.designCapacityMah.available)
        assertFalse(sample.healthPercent.available)
    }

    @Test
    fun energyPairComputesHealthRatioButNeverMah() {
        val nodes = FakeNodes(
            mapOf(
                node("battery", "type") to "Battery",
                node("battery", "energy_full") to "15000000",
                node("battery", "energy_full_design") to "20000000",
                node("battery", "voltage_now") to "4000000",
                node("battery", "cycle_count") to "100",
            ),
        )

        val sample = enrichWith(nodes)

        assertEquals(75.0, sample.healthPercent.value!!, 0.0001)
        assertTrue(sample.healthPercent.source.contains("µWh"))
        // Energy capacity is not converted to mAh, even with voltage_now available.
        assertFalse(sample.designCapacityMah.available)
        assertFalse(sample.fullChargeCapacityMah.available)
        assertFalse(nodes.reads.any { it.endsWith("voltage_now") })
    }

    @Test
    fun vendorStateOfHealthNeverBecomesCapacityHealth() {
        val nodes = FakeNodes(
            mapOf(
                node("battery", "type") to "Battery",
                node("battery", "state_of_health") to "87",
                node("battery", "health") to "Good",
            ),
        )

        val sample = enrichWith(nodes)

        assertFalse(sample.healthPercent.available)
        assertEquals("GOOD", sample.healthStatus.value)
        val zero = FakeNodes(mapOf(node("battery", "type") to "Battery", node("battery", "state_of_health") to "0"))
        assertFalse(enrichWith(zero).healthPercent.available)
    }

    @Test
    fun vendorStateOfHealthOutsideStandardRangeIsIgnored() {
        val nodes = FakeNodes(
            mapOf(
                node("battery", "type") to "Battery",
                node("battery", "state_of_health") to "120",
            ),
        )

        assertFalse(enrichWith(nodes).healthPercent.available)
    }

    @Test
    fun healthGoodNeverBecomesHealthPercent() {
        val nodes = FakeNodes(
            mapOf(
                node("battery", "type") to "Battery",
                node("battery", "health") to "Good",
            ),
        )

        val sample = enrichWith(nodes)

        assertEquals("GOOD", sample.healthStatus.value)
        assertFalse(sample.healthPercent.available)
    }

    @Test
    fun invalidFccPairIsNotReplacedByVendorPercentage() {
        val nodes = FakeNodes(
            mapOf(
                node("battery", "type") to "Battery",
                node("battery", "charge_full") to "100000",
                node("battery", "charge_full_design") to "50000000",
                node("battery", "state_of_health") to "99",
            ),
        )

        val sample = enrichWith(nodes)

        assertFalse(sample.healthPercent.available)
        assertTrue(sample.healthPercent.source.contains("charge_full"))
    }

    @Test
    fun cycleZeroIsUnknownButPublicZeroIsKeptWithItsSource() {
        val publicZero = Reading(0L, "BatteryManager.EXTRA_CYCLE_COUNT")
        val nodes = FakeNodes(
            mapOf(
                node("battery", "type") to "Battery",
                node("battery", "cycle_count") to "0",
            ),
        )

        val sample = enrichWith(nodes, base = baseSample(cycle = publicZero))

        assertEquals(0L, sample.cycleCount.value)
        assertEquals("BatteryManager.EXTRA_CYCLE_COUNT", sample.cycleCount.source)
    }

    @Test
    fun sysfsCycleZeroAloneStaysUnavailable() {
        val nodes = FakeNodes(
            mapOf(
                node("battery", "type") to "Battery",
                node("battery", "cycle_count") to "0",
            ),
        )

        val sample = enrichWith(nodes)

        assertFalse(sample.cycleCount.available)
        assertTrue(sample.cycleCount.reason.orEmpty().contains("0"))
    }

    @Test
    fun missingCycleCountIsNeverCoercedToZero() {
        val nodes = FakeNodes(mapOf(node("battery", "type") to "Battery"))

        assertFalse(enrichWith(nodes).cycleCount.available)
    }

    @Test
    fun oneItemFailureDoesNotDiscardTheOthers() {
        val nodes = FakeNodes(
            mapOf(
                node("battery", "type") to "Battery",
                node("battery", "charge_full") to "4000000",
                node("battery", "charge_full_design") to "not-a-number",
                node("battery", "cycle_count") to "250",
            ),
        )

        val sample = enrichWith(nodes)

        assertEquals(4000.0, sample.fullChargeCapacityMah.value!!, 0.0001)
        assertFalse(sample.designCapacityMah.available)
        assertFalse(sample.healthPercent.available)
        assertEquals(250L, sample.cycleCount.value)
    }

    @Test
    fun staleFrameIsNotReusedAndFreshOrdinaryReadsWin() {
        val frameValues = mapOf(
            node("battery", "type") to "Battery",
            node("battery", "charge_full") to "4000000",
            node("battery", "charge_full_design") to "5000000",
        )
        val nodes = FakeNodes(
            mapOf(
                node("battery", "type") to "Battery",
                node("battery", "charge_full") to "3500000",
                node("battery", "charge_full_design") to "5000000",
            ),
        )

        val sample = enrichWith(nodes, frame = frame(now - 2_001L, frameValues))

        assertEquals(70.0, sample.healthPercent.value!!, 0.0001)
        assertTrue(nodes.reads.isNotEmpty())
    }

    @Test
    fun frameFreshnessBoundaryIsInclusiveAtTwoSeconds() {
        val frameValues = mapOf(
            node("battery", "type") to "Battery",
            node("battery", "charge_full") to "4000000",
            node("battery", "charge_full_design") to "5000000",
        )
        val empty = FakeNodes(emptyMap())

        val atBoundary = enrichWith(empty, frame = frame(now - 2_000L, frameValues))
        val tooOld = enrichWith(empty, frame = frame(now - 2_001L, frameValues))
        val future = enrichWith(empty, frame = frame(now + 1L, frameValues))

        assertEquals(80.0, atBoundary.healthPercent.value!!, 0.0001)
        assertFalse(tooOld.healthPercent.available)
        assertFalse(future.healthPercent.available)
    }

    @Test
    fun freshFrameIsUsedWithoutFallingBackForMissingNodes() {
        val frameValues = mapOf(
            node("battery", "type") to "Battery",
            node("battery", "charge_full") to "4000000",
        )
        val nodes = FakeNodes(
            mapOf(
                node("battery", "type") to "Battery",
                node("battery", "charge_full_design") to "5000000",
            ),
        )

        val sample = enrichWith(nodes, frame = frame(now, frameValues))

        assertEquals(4000.0, sample.fullChargeCapacityMah.value!!, 0.0001)
        assertFalse(sample.designCapacityMah.available)
        assertTrue(sample.designCapacityMah.reason.orEmpty().contains("未包含"))
        assertTrue(nodes.reads.isEmpty())
    }

    @Test
    fun bmsIsUsedOnlyWhenItIdentifiesItselfAsBattery() {
        val nodes = FakeNodes(
            mapOf(
                node("battery", "type") to "Mains",
                node("bms", "type") to "battery",
                node("bms", "charge_full") to "3900000",
                node("bms", "charge_full_design") to "5000000",
                node("bms", "cycle_count") to "42",
            ),
        )

        val sample = enrichWith(nodes)

        assertEquals(5000.0, sample.designCapacityMah.value!!, 0.0001)
        assertEquals(3900.0, sample.fullChargeCapacityMah.value!!, 0.0001)
        assertEquals(78.0, sample.healthPercent.value!!, 0.0001)
        assertEquals(42L, sample.cycleCount.value)
        assertTrue(sample.designCapacityMah.source.contains("/bms/"))
    }

    @Test
    fun unavailableTypePreventsCapacityReads() {
        val nodes = FakeNodes(mapOf(node("bms", "type") to "BMS"))

        val sample = enrichWith(nodes)

        assertFalse(sample.designCapacityMah.available)
        assertFalse(sample.fullChargeCapacityMah.available)
        assertFalse(sample.healthPercent.available)
        assertFalse(nodes.reads.any { it.endsWith("charge_full") || it.endsWith("energy_full") })
    }

    @Test
    fun existingVoltageCurrentPowerAndChargeSignsAreCopiedUnchanged() {
        val original = baseSample()
        val nodes = FakeNodes(
            mapOf(
                node("battery", "type") to "Battery",
                node("battery", "charge_full") to "4000000",
                node("battery", "charge_full_design") to "5000000",
            ),
        )

        val sample = enrichWith(nodes, base = original)

        assertEquals(3.9, sample.voltageV.value!!, 0.0)
        assertEquals(-1_200.0, sample.currentMa.value!!, 0.0)
        assertEquals(-4.68, sample.powerW.value!!, 0.0)
        assertEquals(2_500.0, sample.chargeCounterMah.value!!, 0.0)
        assertEquals("Charging", sample.status)
        assertFalse(nodes.reads.any { it.endsWith("current_now") })
        assertFalse(nodes.reads.any { it.endsWith("charge_counter") })
    }

    @Test
    fun remainingChargeAndLevelNeverBecomeFccOrHealth() {
        val nodes = FakeNodes(mapOf(node("battery", "type") to "Battery"))

        val sample = enrichWith(nodes)

        assertFalse(sample.fullChargeCapacityMah.available)
        assertFalse(sample.designCapacityMah.available)
        assertFalse(sample.healthPercent.available)
    }
}
