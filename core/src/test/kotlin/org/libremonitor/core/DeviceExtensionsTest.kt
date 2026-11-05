/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.core

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class DeviceExtensionsTest {
    private val catalogText = "SM8550,SM8550-AB\t骁龙 8 Gen 2\tQualcomm\t1\t4\t3\thttps://example.org/reference"
    @Test fun catalogUsesExactChipIdentifiersAndNeverPhoneNameGuessing() {
        val entries = CpuCatalog.parse(catalogText)
        assertEquals("骁龙 8 Gen 2", CpuCatalog.identify(entries, listOf("sm8550"), "", emptyMap()).name.value)
        assertEquals("SM85500", CpuCatalog.identify(entries, listOf("SM85500"), "", emptyMap()).name.value)
        assertNull(CpuCatalog.identify(entries, listOf("unknown"), "", emptyMap()).catalog)
    }
    @Test fun shippedCatalogIsWellFormedAndContainsTheUsersChip() {
        val entries = CpuCatalog.parse(File("../app/src/main/assets/soc_catalog.tsv").readText())
        assertTrue(entries.size >= 7)
        assertEquals(8, entries.single { "SM8550" in it.aliases }.let { it.prime + it.performance + it.efficiency })
        assertEquals("骁龙 8 Gen 2", entries.single { "SM8550" in it.aliases }.name)
    }
    @Test(expected = IllegalArgumentException::class) fun duplicateCatalogAliasFailsInsteadOfAmbiguousMatching() {
        CpuCatalog.parse("$catalogText\n$catalogText")
    }
    @Test fun armPartsDetermineCoreKindsWithoutGuessingCoreIds() {
        val info = "processor : 7\nCPU implementer : 0x41\nCPU part : 0xd4e\nprocessor : 1\nCPU implementer : 0x41\nCPU part : 0xd46\nprocessor : 3\nCPU implementer : 0x41\nCPU part : 0xd4d"
        val identity = CpuCatalog.identify(CpuCatalog.parse(catalogText), listOf("SM8550"), info, emptyMap())
        assertEquals(listOf(1, 3, 7), identity.cores.map { it.id })
        assertEquals(listOf(CoreTier.EFFICIENCY, CoreTier.PERFORMANCE, CoreTier.PRIME), identity.cores.map { it.tier })
        assertEquals("Cortex-X3", identity.cores.last().architecture)
    }
    @Test fun customImplementerIsNotMisidentifiedAsArmAndUniformCapacityStaysUnknown() {
        val info = "processor : 0\nCPU implementer : 0x51\nCPU part : 0xd4e"
        assertEquals(CoreTier.UNKNOWN, CpuCatalog.identify(emptyList(), emptyList(), info, mapOf(0 to 512L)).cores.single().tier)
        val guessed = CpuCatalog.identify(emptyList(), emptyList(), "", mapOf(0 to 256L, 1 to 512L, 2 to 1024L))
        assertEquals(CoreTier.PRIME, guessed.cores.last().tier)
        assertTrue(guessed.cores.last().source.contains("推断"))
    }
    @Test fun overlayPreferenceDecodeKeepsOrderDropsUnknownsAndClampsCorruption() {
        val decoded = OverlayOptions.decode(mapOf("overlay_metrics" to "FPS,INVALID,CPU,FPS", "overlay_opacity" to 200,
            "overlay_columns" to 9, "overlay_text_size" to 0, "overlay_edge_snap" to false))
        assertEquals(listOf(OverlayMetric.FPS, OverlayMetric.CPU), decoded.metrics)
        assertEquals(100, decoded.opacityPercent); assertEquals(2, decoded.columns); assertEquals(12, decoded.textSizeSp)
        assertFalse(decoded.edgeSnap)
        assertEquals(OverlayOptions().metrics, OverlayOptions.decode(mapOf("overlay_metrics" to "INVALID")).metrics)
    }
    @Test(expected = IllegalArgumentException::class) fun overlayCannotHaveNoMetrics() { OverlayOptions(metrics = emptyList()) }
    @Test fun edgeThresholdAndVerticalClampWorkForSmallAndRotatedScreens() {
        assertEquals(DockEdge.LEFT, OverlayDocking.edge(24, 184, 600, 24))
        assertEquals(DockEdge.RIGHT, OverlayDocking.edge(392, 184, 600, 24))
        assertEquals(DockEdge.NONE, OverlayDocking.edge(200, 184, 600, 24))
        assertEquals(0, OverlayDocking.clampY(50, 200, 100))
        assertEquals(744, OverlayDocking.clampY(1000, 56, 800))
    }

    private fun apple(flags: Int = 0x20, batteries: Int = 0x98, case: Int = 0x47) = ByteArray(27).apply {
        this[0] = 7; this[1] = 25; this[2] = 1; this[3] = 0x0e; this[4] = 0x20
        this[5] = flags.toByte(); this[6] = batteries.toByte(); this[7] = case.toByte()
    }
    @Test fun airpodsCoarseBatteryFlipAndUnknownNibblesArePreserved() {
        val regular = EarbudProtocol.appleManufacturer(apple())!!
        assertEquals("AirPods Pro", regular.model)
        assertEquals(80, regular.left); assertEquals(90, regular.right); assertEquals(70, regular.case); assertTrue(regular.caseCharging)
        val flipped = EarbudProtocol.appleManufacturer(apple(flags = 0))!!
        assertEquals(90, flipped.left); assertEquals(80, flipped.right)
        val missing = EarbudProtocol.appleManufacturer(apple(batteries = 0xf0, case = 0xff))!!
        assertEquals(0, missing.left); assertNull(missing.right); assertNull(missing.case)
    }
    @Test fun advertisementWalkerFindsProximityDataAmongMultipleAppleEntries() {
        fun ad(payload: ByteArray) = byteArrayOf((payload.size + 3).toByte(), 0xff.toByte(), 0x4c, 0) + payload
        val bytes = ad(byteArrayOf(0x12, 1, 0)) + ad(apple()) + byteArrayOf(0)
        assertEquals("AirPods Pro", EarbudProtocol.advertisement(bytes)?.model)
        assertNull(EarbudProtocol.advertisement(byteArrayOf(50, 0xff.toByte(), 0x4c, 0, 7)))
        assertNull(EarbudProtocol.appleManufacturer(apple().copyOf(26)))
        assertNull(EarbudProtocol.appleManufacturer(apple().apply { this[3] = 0x7f }))
    }
    private fun counters(rx: Long, tx: Long, name: String = "bnep0") =
        Reading("$name: $rx 0 0 0 0 0 0 0 $tx 0 0 0 0 0 0 0\nwlan0: 999999 0 0 0 0 0 0 0 999999 0 0 0 0 0 0 0", "fixture")
    @Test fun panRatesUseOnlyPanCountersAndRejectResetAndMissingBaseline() {
        val rates = BluetoothPanRates()
        assertFalse(rates.sample(counters(100, 200), 1000).first.available)
        val valid = rates.sample(counters(300, 300), 2000)
        assertEquals(200.0, valid.first.value!!, 0.0); assertEquals(100.0, valid.second.value!!, 0.0)
        assertEquals(0.0, rates.sample(counters(300, 300), 3000).first.value!!, 0.0)
        assertFalse(rates.sample(counters(50, 400), 4000).first.available)
        assertFalse(rates.sample(counters(100, 500, "wlan1"), 5000).first.available)
        val denied = rates.sample(Reading.unavailable("proc", "denied", ReadingStatus.PERMISSION_DENIED), 6000)
        assertEquals(ReadingStatus.PERMISSION_DENIED, denied.first.status)
        assertFalse(rates.sample(counters(200, 600), 7000).first.available)
    }
    @Test fun hotspotDoesNotTreatMissingDiagnosticsAsDisabledOrZeroClients() {
        assertFalse(HotspotDiagnostics.parse("").getValue("SoftAP 状态").available)
        assertFalse(HotspotDiagnostics.parse("CLIENTS=0").getValue("SoftAP 客户端数").available)
        val active = HotspotDiagnostics.parse("AP_BEGIN\nSTATE=enabled\nCLIENTS=2\n")
        assertEquals("已开启", active.getValue("SoftAP 状态").value)
        assertEquals("2", active.getValue("SoftAP 客户端数").value)
        assertEquals("0", HotspotDiagnostics.parse("AP_BEGIN\nSTATE=disabled\nCLIENTS=0\n").getValue("SoftAP 客户端数").value)
    }
}
