/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.data.collectors

import org.junit.Assert.*
import org.junit.Test
import org.libremonitor.core.*

class BatteryElectricalCollectorTest {
    private val base = BatterySample(Reading(25, "public"), Reading(35.0, "public"), Reading(3.93, "public"),
        Reading(-1.721, "public"), Reading(1_030.0, "public"), Reading(-0.00676353, "public"), "充电", "Li-ion",
        electricalDiagnostics = BatteryElectricalDiagnostics(Reading(-1721L, "BatteryManager")))
    private fun nodes(model: String = "oplus battery", current: String = "-1721", supply: String = "battery") = mapOf(
        "type" to "Battery", "scope" to "System", "present" to "1", "model_name" to model,
        "voltage_now" to "3935000", "current_now" to current).mapKeys { "/sys/class/power_supply/$supply/${it.key}" }
    private fun sample(values: Map<String, String>, mode: BatteryCurrentMode = BatteryCurrentMode.AUTO,
        frame: PrivilegedNodeFrame? = null, now: Long = 1_000) = BatteryElectricalCollector.enrich(base, frame, mode, now) { path, _ ->
        values[path]?.let { Reading(it, path) } ?: Reading.unavailable(path, "fixture missing")
    }

    @Test fun identifiedOplusMilliampDriverProducesSixPointSevenWattsWithoutChangingTheSign() {
        val sample = sample(nodes())
        assertEquals(-1721.0, sample.currentMa.value!!, 0.0)
        assertEquals(3.935, sample.voltageV.value!!, 0.0)
        assertEquals(-6.772135, sample.powerW.value!!, 0.000001)
        assertEquals("mA", sample.electricalDiagnostics!!.currentUnit)
        assertEquals(-1721L, sample.electricalDiagnostics!!.rawApiCurrent.value!!)
    }

    @Test fun standardDriverAlwaysUsesMicroampsEvenForVerySmallCurrents() {
        assertEquals(-1721.0, sample(nodes("standard battery", "-1721000")).currentMa.value!!, 0.0)
        assertEquals(-1.721, sample(nodes("standard battery", "-1721")).currentMa.value!!, 0.0)
        assertEquals(0.0, sample(nodes("standard battery", "0")).powerW.value!!, 0.0)
    }

    @Test fun explicitUnitOverrideIsPersistableAndDoesNotDependOnReadingMagnitude() {
        assertEquals(-1.721, sample(nodes(), BatteryCurrentMode.MICROAMPS).currentMa.value!!, 0.0)
        assertEquals(-1721.0, sample(emptyMap(), BatteryCurrentMode.MILLIAMPS).currentMa.value!!, 0.0)
    }

    @Test fun noCrossSupplyVoltageCurrentPairAndNoAccessoryBattery() {
        val incomplete = nodes().filterKeys { !it.endsWith("current_now") } + nodes("standard", "2000000", "bms").filterKeys { !it.endsWith("voltage_now") }
        assertEquals(base.voltageV.value!!, sample(incomplete).voltageV.value!!, 0.0)
        assertNull(sample(incomplete).electricalDiagnostics!!.selectedSupply)
        val accessory = nodes().toMutableMap().apply { put("/sys/class/power_supply/battery/scope", "Device") }
        assertNull(sample(accessory).electricalDiagnostics!!.selectedSupply)
        val absent = nodes().toMutableMap().apply { put("/sys/class/power_supply/battery/present", "0") }
        assertNull(sample(absent).electricalDiagnostics!!.selectedSupply)
    }

    @Test fun staleFutureDeniedAndMalformedNodesDoNotBecomeFreshOrZero() {
        val frame = PrivilegedNodeFrame(nodes().mapValues { Reading(it.value, "Root ${it.key}") }, 1_000, 1)
        assertEquals(-1721.0, sample(emptyMap(), frame = frame, now = 3_000).currentMa.value!!, 0.0)
        assertEquals(-1.721, sample(emptyMap(), frame = frame, now = 3_001).currentMa.value!!, 0.0)
        assertEquals(-1.721, sample(emptyMap(), frame = frame, now = 999).currentMa.value!!, 0.0)
        assertNull(sample(nodes(current = "garbage")).electricalDiagnostics!!.selectedSupply)
    }

    @Test fun liveElectricalValuesChangeEachCycleRatherThanUsingMetadataCache() {
        assertEquals(-6.772135, sample(nodes(current = "-1721")).powerW.value!!, 0.000001)
        assertEquals(-3.935, sample(nodes(current = "-1000")).powerW.value!!, 0.000001)
        assertEquals(3.935, sample(nodes(current = "1000")).powerW.value!!, 0.000001)
    }
}
