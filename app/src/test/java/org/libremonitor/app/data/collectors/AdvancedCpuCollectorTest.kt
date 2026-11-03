/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.data.collectors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.libremonitor.app.data.privileged.RootProtocol
import org.libremonitor.core.PrivilegedNodeFrame
import org.libremonitor.core.Reading
import org.libremonitor.core.SensorKind
import java.io.ByteArrayInputStream
import java.util.Base64

class AdvancedCpuCollectorTest {
    private val cpu = "Temperature{mValue=42.5, mType=0, mName=cpu0, mStatus=0}"
    private val skin = "Temperature{mValue=90.0, mType=3, mName=skin, mStatus=0}"
    private fun dump(body: String) = "HAL Ready: true\nCurrent temperatures from HAL:\n$body\nCached temperatures:\n"

    @Test fun halOnlyAcceptsCurrentCpu() {
        val sensors = AdvancedCpuCollector.parseHal(dump("$cpu\n$skin"))!!
        assertEquals(1, sensors.size)
        assertEquals(42.5, sensors.single().temperatureC.value!!, 0.0)
    }

    @Test fun refusesMissingReadinessOrIncompleteSection() {
        assertNull(AdvancedCpuCollector.parseHal(dump(cpu).replace("HAL Ready: true", "HAL Ready: false")))
        assertNull(AdvancedCpuCollector.parseHal("HAL Ready: true\nCurrent temperatures from HAL:\n$cpu\n"))
        assertNull(AdvancedCpuCollector.parseHal(dump(cpu).substringBefore("Cached temperatures:")))
        assertNull(AdvancedCpuCollector.parseHal("Current temperatures from HAL:\n$cpu\nHAL Ready: true\nCached temperatures:\n"))
    }

    @Test fun protocolFrameFeedsHalAndIncompleteHalFallsBack() {
        val id = "0123456789abcdef0123456789abcdef"
        val node = RootProtocol.Node("thermalservice", 65536, "")
        fun result(text: String): AdvancedCpuCollector.Result {
            val encoded = Base64.getEncoder().encodeToString(text.toByteArray())
            val wire = "LM:$id:BEGIN\nLM:$id:0:$encoded\nLM:$id:END\n"
            val readings = RootProtocol.readFrame(ByteArrayInputStream(wire.toByteArray()), id, listOf(node))
            val frame = PrivilegedNodeFrame(readings, 100, 1)
            return AdvancedCpuCollector.sample(frame) { path, _ ->
                when (path) {
                    "/sys/class/thermal/thermal_zone0/type" -> Reading("cpu-thermal", path)
                    "/sys/class/thermal/thermal_zone0/temp" -> Reading("38000", path)
                    else -> Reading.unavailable(path, "synthetic missing")
                }
            }
        }
        val complete = result(dump("$cpu\n$skin"))
        assertEquals(42.5, complete.temperature.value!!, 0.0)
        assertTrue(complete.temperature.source.contains("HAL"))
        assertEquals(1, complete.sensors.count { it.id.startsWith("hal:") && it.kind == SensorKind.CPU })
        val truncated = result(dump(cpu).substringBefore("Cached temperatures:"))
        assertEquals(38.0, truncated.temperature.value!!, 0.0)
        assertTrue(truncated.temperature.source.contains("sysfs"))
        assertTrue(truncated.sensors.none { it.id.startsWith("hal:") })
    }

    @Test fun cachedAndInvalidValuesNeverBecomeCpu() {
        assertNull(AdvancedCpuCollector.parseHal(dump(skin) + "$cpu\n"))
        assertNull(AdvancedCpuCollector.parseHal(dump(cpu.replace("42.5", "NaN"))))
        assertNull(AdvancedCpuCollector.parseHal(dump(cpu.replace("42.5", "181.0"))))
        assertEquals(0.0, AdvancedCpuCollector.parseHal(dump(cpu.replace("42.5", "0")))!!.single().temperatureC.value!!, 0.0)
        assertEquals(-10.0, AdvancedCpuCollector.parseHal(dump(cpu.replace("42.5", "-10")))!!.single().temperatureC.value!!, 0.0)
    }
}
