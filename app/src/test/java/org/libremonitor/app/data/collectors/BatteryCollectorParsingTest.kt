/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.data.collectors

import android.os.BatteryManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for the broadcast-value mapping. Intent plumbing itself needs an Android
 * instrumentation or device test; these cases cover the value/status boundaries only.
 */
class BatteryCollectorParsingTest {
    @Test
    fun healthMapsGoodAndEveryPublicFaultCode() {
        assertEquals(
            "GOOD",
            batteryHealthReading(BatteryManager.BATTERY_HEALTH_GOOD, "health").value,
        )
        assertEquals(
            "OVERHEAT",
            batteryHealthReading(BatteryManager.BATTERY_HEALTH_OVERHEAT, "health").value,
        )
        assertEquals(
            "DEAD",
            batteryHealthReading(BatteryManager.BATTERY_HEALTH_DEAD, "health").value,
        )
        assertEquals(
            "OVER_VOLTAGE",
            batteryHealthReading(BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE, "health").value,
        )
        assertEquals(
            "UNSPECIFIED_FAILURE",
            batteryHealthReading(
                BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE, "health",
            ).value,
        )
        assertEquals(
            "COLD",
            batteryHealthReading(BatteryManager.BATTERY_HEALTH_COLD, "health").value,
        )
    }

    @Test
    fun unknownOrVendorHealthCodeStaysUnavailable() {
        assertFalse(
            batteryHealthReading(BatteryManager.BATTERY_HEALTH_UNKNOWN, "health").available,
        )
        assertFalse(batteryHealthReading(0, "health").available)
        assertFalse(batteryHealthReading(999, "health").available)
    }

    @Test
    fun pluggedBitmaskMapsExternalSourceAndZeroIsRealData() {
        assertEquals(
            "未连接外部电源",
            batteryChargingSourceReading(0, "plugged").value,
        )
        assertEquals(
            "AC",
            batteryChargingSourceReading(BatteryManager.BATTERY_PLUGGED_AC, "plugged").value,
        )
        assertEquals(
            "USB",
            batteryChargingSourceReading(BatteryManager.BATTERY_PLUGGED_USB, "plugged").value,
        )
        assertEquals(
            "无线",
            batteryChargingSourceReading(
                BatteryManager.BATTERY_PLUGGED_WIRELESS, "plugged",
            ).value,
        )
        assertEquals(
            "AC+USB",
            batteryChargingSourceReading(
                BatteryManager.BATTERY_PLUGGED_AC or BatteryManager.BATTERY_PLUGGED_USB,
                "plugged",
            ).value,
        )
        assertFalse(batteryChargingSourceReading(64, "plugged").available)
    }

    @Test
    fun cycleCountRequiresApi34AndAnExplicitExtra() {
        assertFalse(batteryCycleCountReading(33, true, 0, "cycle").available)
        assertFalse(batteryCycleCountReading(34, false, 0, "cycle").available)
        assertFalse(batteryCycleCountReading(34, true, -1, "cycle").available)
        assertFalse(
            batteryCycleCountReading(34, true, Int.MAX_VALUE, "cycle").available,
        )
    }

    @Test
    fun reportedZeroIsAvailableButMissingOrInvalidIsNotZero() {
        val zero = batteryCycleCountReading(34, true, 0, "cycle")
        assertTrue(zero.available)
        assertEquals(0L, zero.value)
        assertTrue(zero.reason.orEmpty().contains("0"))

        val positive = batteryCycleCountReading(34, true, 250, "cycle")
        assertTrue(positive.available)
        assertEquals(250L, positive.value)

        assertFalse(batteryCycleCountReading(34, false, 0, "cycle").available)
    }
}
