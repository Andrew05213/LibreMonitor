/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.core

import org.junit.Assert.*
import org.junit.Test

class SensorMathTest {
    @Test fun temperatureDefaultsToMilliCelsius() {
        assertEquals(42.5, SensorMath.temperatureC("42500")!!, 0.0)
    }
    @Test fun temperatureAcceptsExplicitCelsius() {
        assertEquals(42.5, SensorMath.temperatureC("42.5", milliCelsius = false)!!, 0.0)
    }
    @Test fun temperatureDoesNotGuessUnitsFromMagnitude() {
        assertEquals(0.042, SensorMath.temperatureC("42")!!, 0.0)
        assertNull(SensorMath.temperatureC("42000", milliCelsius = false))
    }
    @Test fun temperatureTrimsSurroundingWhitespace() {
        assertEquals(25.0, SensorMath.temperatureC(" \t25000\r\n")!!, 0.0)
    }
    @Test fun temperatureKeepsRealZeroAndNegativeValues() {
        assertEquals(0.0, SensorMath.temperatureC("0")!!, 0.0)
        assertEquals(-10.0, SensorMath.temperatureC("-10000")!!, 0.0)
    }
    @Test fun temperatureAcceptsInclusivePhysicalBounds() {
        assertEquals(-273.15, SensorMath.temperatureC("-273150")!!, 0.0)
        assertEquals(180.0, SensorMath.temperatureC("180000")!!, 0.0)
        assertEquals(-273.15, SensorMath.temperatureC("-273.15", false)!!, 0.0)
        assertEquals(180.0, SensorMath.temperatureC("180", false)!!, 0.0)
    }
    @Test fun temperatureRejectsOutsidePhysicalBounds() {
        assertNull(SensorMath.temperatureC("-273151"))
        assertNull(SensorMath.temperatureC("180001"))
        assertNull(SensorMath.temperatureC("-273.1501", false))
        assertNull(SensorMath.temperatureC("180.0001", false))
    }
    @Test fun temperatureRejectsAllNonFiniteInputs() {
        listOf("NaN", "Infinity", "+Infinity", "-Infinity", "Inf", "1e309", "-1e309").forEach { raw ->
            assertNull(raw, SensorMath.temperatureC(raw))
            assertNull(raw, SensorMath.temperatureC(raw, false))
        }
    }
    @Test fun temperatureRejectsMalformedAndUnitSuffixedValues() {
        listOf("", "  ", "abc", "25 C", "25000mC", "1 2", "1,5", "0x1p4", "25f").forEach { raw ->
            assertNull(raw, SensorMath.temperatureC(raw))
        }
    }
    @Test fun temperatureAcceptsFiniteDecimalExponentWithoutChangingUnits() {
        assertEquals(42.5, SensorMath.temperatureC("4.25e4")!!, 0.0)
        assertEquals(42.5, SensorMath.temperatureC("4.25e1", false)!!, 0.0)
    }
    @Test fun capacityConvertsExplicitMicroAmpHours() {
        assertEquals(4_500.0, SensorMath.microAmpHoursToMah("4500000")!!, 0.0)
        assertEquals(4_500.001, SensorMath.microAmpHoursToMah("4500001")!!, 0.0)
    }
    @Test fun capacityDoesNotMistakeMahForMicroAmpHours() {
        assertNull(SensorMath.microAmpHoursToMah("4500"))
    }
    @Test fun capacityAcceptsInclusiveCredibleBounds() {
        assertEquals(100.0, SensorMath.microAmpHoursToMah("100000")!!, 0.0)
        assertEquals(50_000.0, SensorMath.microAmpHoursToMah("50000000")!!, 0.0)
    }
    @Test fun capacityRejectsOutsideCredibleBounds() {
        assertNull(SensorMath.microAmpHoursToMah("99999"))
        assertNull(SensorMath.microAmpHoursToMah("50000001"))
        assertNull(SensorMath.microAmpHoursToMah("0"))
        assertNull(SensorMath.microAmpHoursToMah("-4500000"))
    }
    @Test fun capacityTrimsAndAcceptsIntegerPlusPrefix() {
        assertEquals(4_000.0, SensorMath.microAmpHoursToMah(" \t+4000000\n")!!, 0.0)
    }
    @Test fun capacityRejectsMalformedNonIntegerAndNonFiniteValues() {
        listOf("", "NaN", "Infinity", "4000000.5", "4e6", "4000000 uAh", "4000000µAh", "bad").forEach { raw ->
            assertNull(raw, SensorMath.microAmpHoursToMah(raw))
        }
    }
    @Test fun capacityRejectsOverflowAndHugeButParsableCounter() {
        assertNull(SensorMath.microAmpHoursToMah("9223372036854775808"))
        assertNull(SensorMath.microAmpHoursToMah(Long.MAX_VALUE.toString()))
    }
    @Test fun healthCalculatesOrdinaryWear() {
        assertEquals(80.0, SensorMath.healthPercent(4_000.0, 5_000.0)!!, 0.0)
    }
    @Test fun healthPreservesLegitimateOverOneHundredPercent() {
        assertEquals(101.0, SensorMath.healthPercent(5_050.0, 5_000.0)!!, 0.0)
    }
    @Test fun healthAcceptsInclusiveRatioBounds() {
        assertEquals(1.0, SensorMath.healthPercent(100.0, 10_000.0)!!, 0.0)
        assertEquals(150.0, SensorMath.healthPercent(7_500.0, 5_000.0)!!, 0.0)
    }
    @Test fun healthRejectsOutsideRatioBounds() {
        assertNull(SensorMath.healthPercent(100.0, 10_001.0))
        assertNull(SensorMath.healthPercent(7_500.1, 5_000.0))
    }
    @Test fun healthRejectsZeroOrNegativeCapacityOnEitherSide() {
        listOf(0.0, -0.0, -1.0, -5_000.0).forEach { capacity ->
            assertNull(SensorMath.healthPercent(capacity, 5_000.0))
            assertNull(SensorMath.healthPercent(5_000.0, capacity))
        }
    }
    @Test fun healthRejectsNonFiniteCapacityOnEitherSide() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach { capacity ->
            assertNull(SensorMath.healthPercent(capacity, 5_000.0))
            assertNull(SensorMath.healthPercent(5_000.0, capacity))
        }
    }
    @Test fun healthAcceptsInclusiveCapacityBounds() {
        assertEquals(100.0, SensorMath.healthPercent(100.0, 100.0)!!, 0.0)
        assertEquals(100.0, SensorMath.healthPercent(50_000.0, 50_000.0)!!, 0.0)
    }
    @Test fun healthRejectsOutOfRangeCapacitiesEvenWhenRatioLooksValid() {
        assertNull(SensorMath.healthPercent(99.0, 99.0))
        assertNull(SensorMath.healthPercent(50_001.0, 50_001.0))
        assertNull(SensorMath.healthPercent(99.0, 100.0))
        assertNull(SensorMath.healthPercent(100.0, 99.0))
        assertNull(SensorMath.healthPercent(50_001.0, 50_000.0))
        assertNull(SensorMath.healthPercent(50_000.0, 50_001.0))
    }
    @Test fun healthRejectsDualCellTwoHundredPercentWithoutRescaling() {
        assertNull(SensorMath.healthPercent(10_000.0, 5_000.0))
    }
    @Test fun arithmeticCannotValidateBatteryIdentityOrReverseDualCellMismatch() {
        // A mismatched pack/cell pair can pass numerical bounds: the caller must reject that source.
        assertEquals(50.0, SensorMath.healthPercent(5_000.0, 10_000.0)!!, 0.0)
    }
    @Test fun counterKeepsZero() {
        assertEquals(0L, SensorMath.nonNegativeCounter("0")!!.toLong())
    }
    @Test fun counterAcceptsWhitespaceLeadingZerosAndPlusPrefix() {
        assertEquals(42L, SensorMath.nonNegativeCounter(" \t+00042\r\n")!!.toLong())
    }
    @Test fun counterAcceptsLargestLong() {
        assertEquals(Long.MAX_VALUE, SensorMath.nonNegativeCounter("9223372036854775807")!!.toLong())
    }
    @Test fun counterRejectsOverflow() {
        assertNull(SensorMath.nonNegativeCounter("9223372036854775808"))
        assertNull(SensorMath.nonNegativeCounter("18446744073709551615"))
        assertNull(SensorMath.nonNegativeCounter("9999999999999999999999999999999999999999"))
    }
    @Test fun counterRejectsNegativeValuesAndNegativeSigns() {
        assertNull(SensorMath.nonNegativeCounter("-1"))
        assertNull(SensorMath.nonNegativeCounter(Long.MIN_VALUE.toString()))
        assertNull(SensorMath.nonNegativeCounter("-0"))
    }
    @Test fun counterRejectsMalformedFractionalExponentAndUnitText() {
        listOf("", "  ", "+", "1.0", "1e3", "NaN", "Infinity", "1 cycle", "1 2", "0x10", "--1", "++1").forEach { raw ->
            assertNull(raw, SensorMath.nonNegativeCounter(raw))
        }
    }
    @Test fun counterRejectsNonAsciiDigits() {
        assertNull(SensorMath.nonNegativeCounter("１２"))
        assertNull(SensorMath.nonNegativeCounter("١٢"))
    }
    @Test fun cpuWhitelistAcceptsExplicitCpuAndCpussNames() {
        listOf("cpu", "cpu0", "cpu-1", "cpu_2", "cpuss", "cpuss0", "cpuss-1").forEach { type ->
            assertTrue(type, SensorMath.isCpuSensorType(type))
        }
    }
    @Test fun cpuWhitelistAcceptsExplicitThermalVariants() {
        listOf("cpu-therm", "cpu_therm", "cpu0-therm", "cpu-thermal", "cpuss-therm", "cpu-therm0").forEach { type ->
            assertTrue(type, SensorMath.isCpuSensorType(type))
        }
    }
    @Test fun cpuWhitelistAcceptsExplicitClusterVariants() {
        listOf("cluster", "cluster0", "cluster-1", "cluster_2", "cluster0-therm", "cluster1-thermal", "cpu-cluster0", "cpu_cluster_1").forEach { type ->
            assertTrue(type, SensorMath.isCpuSensorType(type))
        }
    }
    @Test fun cpuWhitelistIsCaseInsensitiveAndTrims() {
        listOf("CPU", "CpUsS0", "CPU-THERM", "Cluster0", " \tCPU-THERM\n").forEach { type ->
            assertTrue(type, SensorMath.isCpuSensorType(type))
        }
    }
    @Test fun cpuWhitelistRejectsOtherComponents() {
        listOf("battery", "bms", "skin", "xo", "pmic", "gpu", "modem", "BATTERY", "GPU-THERM", "pmic-therm").forEach { type ->
            assertFalse(type, SensorMath.isCpuSensorType(type))
        }
    }
    @Test fun cpuWhitelistRejectsAnonymousOrUnvalidatedSensors() {
        listOf("tsens_tz_sensor0", "tsens_tz_sensor12", "TSENS_TZ_SENSOR3", "soc", "soc-therm", "thermal_zone0", "ap", "quiet-therm").forEach { type ->
            assertFalse(type, SensorMath.isCpuSensorType(type))
        }
    }
    @Test fun cpuWhitelistDoesNotTrustSubstringOrMislabelledCombinations() {
        listOf("battery-cpu", "cpu-battery", "bms-cpuss", "cpuss-bms", "cpu-skin", "cpu-xo", "cpu-pmic", "cpu-gpu-therm", "cpu-modem", "gpu-cluster0", "notcpu", "cpuish", "cluster-battery", "tsens_tz_sensor0-cpu").forEach { type ->
            assertFalse(type, SensorMath.isCpuSensorType(type))
        }
    }
    @Test fun cpuWhitelistRejectsEmptyAndMalformedNames() {
        listOf("", " \t\n", "cpu 0", "cpu\n0", "cpu/therm", "cpu-therm-extra", "cluster0-unknown", "cpu--0").forEach { type ->
            assertFalse(type, SensorMath.isCpuSensorType(type))
        }
    }
}
