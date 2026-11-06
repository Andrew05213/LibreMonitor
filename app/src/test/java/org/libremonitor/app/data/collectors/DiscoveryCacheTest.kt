/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.data.collectors

import org.junit.Assert.*
import org.junit.Test
import org.libremonitor.core.*

class DiscoveryCacheTest {
    @Test fun sensorDiscoveryIsBoundedButLiveTemperaturesAreNeverReused() {
        val discovery = AdvancedCpuCollector.Discovery()
        var typesRead = 0
        var temperature = "42000"
        val read: (String, Int) -> Reading<String> = { path, _ ->
            if (path.endsWith("/type")) typesRead++
            when (path) {
                "/sys/class/thermal/thermal_zone2/type" -> Reading("cpu-thermal", path)
                "/sys/class/thermal/thermal_zone2/temp" -> Reading(temperature, path)
                else -> Reading.unavailable(path, "synthetic missing")
            }
        }
        fun sample(now: Long, identity: String) = AdvancedCpuCollector.sample(null, read, discovery.types(null, now, identity, read))
        assertEquals(42.0, sample(100, "root-1").temperature.value!!, 0.0)
        assertEquals(96, typesRead)
        temperature = "47000"
        assertEquals(47.0, sample(200, "root-1").temperature.value!!, 0.0)
        assertEquals(96, typesRead)
        sample(300, "disabled")
        assertEquals(192, typesRead)
        sample(30_300, "disabled")
        assertEquals(288, typesRead)
    }

    @Test fun batteryCacheKeepsMetadataAndUsesFreshElectricalReadingsAfterEverySample() {
        val cache = BatteryDetailsCache()
        var calls = 0
        val base = BatterySample(Reading(50, "public"), Reading(25.0, "public"), Reading(4.0, "public"),
            Reading(100.0, "public"), Reading(2_500.0, "public"), Reading(0.4, "public"), "Charging", "Li-ion")
        fun sample(now: Long, identity: String, current: Double) = cache.enrich(
            base.copy(currentMa = Reading(current, "fresh")), now, identity) {
            calls++; base.copy(designCapacityMah = Reading(5_000.0, "synthetic"))
        }
        sample(100, "root-1", 100.0)
        val next = sample(200, "root-1", 900.0)
        assertEquals(1, calls)
        assertEquals(900.0, next.currentMa.value!!, 0.0)
        assertEquals(5_000.0, next.designCapacityMah.value!!, 0.0)
        sample(300, "disabled", 200.0)
        assertEquals(2, calls)
        sample(30_300, "disabled", 300.0)
        assertEquals(3, calls)
    }
}
