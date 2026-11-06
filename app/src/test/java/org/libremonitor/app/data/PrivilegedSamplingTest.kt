/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.libremonitor.app.data.collectors.GpuCollector
import org.libremonitor.app.data.privileged.RootSession
import org.libremonitor.core.*

class PrivilegedSamplingTest {
    @Test fun unselectedFpsNeverLaunchesACompositorQuery() = runBlocking {
        val reader = FakeReader()
        val result = readPrivilegedSample(reader, false, includeFps = false) { reader.now }
        assertEquals(listOf("overlay"), reader.calls)
        assertNotNull(result.frame)
        assertFalse(result.fps.fps.available)
    }
    private class FakeReader : RootSession {
        var now = 1_000L
        var fpsCost = 2_200L
        var frameCost = 20L
        var fpsFailure: Exception? = null
        val calls = mutableListOf<String>()
        override val state = MutableStateFlow(RootState(RootAccess.GRANTED, "synthetic", 1))
        override suspend fun requestAuthorization() = true
        override suspend fun grantPermissions() = PermissionGrantResult()
        override fun close() { state.value = RootState(RootAccess.DISABLED, "synthetic closed", 1) }
        override suspend fun readFps(): FpsSample {
            calls.add("fps")
            fpsFailure?.let { throw it }
            now += fpsCost
            return FpsSample(fps = Reading(60.0, "synthetic"), rootSessionId = 1)
        }
        override suspend fun readFrame(): PrivilegedNodeFrame {
            calls.add("frame")
            now += frameCost
            return PrivilegedNodeFrame(mapOf(
                "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage" to Reading("35 %", "synthetic Root"),
                "/sys/class/kgsl/kgsl-3d0/gpuclk" to Reading("500000000", "synthetic Root")), now, 1)
        }
        override suspend fun readOverlayFrame(): PrivilegedNodeFrame {
            val frame = readFrame()
            calls[calls.lastIndex] = "overlay"
            return frame
        }
    }

    @Test fun slowFpsDiscoveryDoesNotAgeTheGpuFrameBeforePublication() = runBlocking {
        val reader = FakeReader()
        val result = readPrivilegedSample(reader, true) { reader.now }
        assertEquals(listOf("fps", "frame"), reader.calls)
        val gpu = GpuCollector.sample(result.frame, reader.now) { _, _ -> error("fresh Root GPU should not fall back") }
        assertEquals(35.0, gpu.usagePercent.value!!, 0.0)
        assertTrue(result.fpsAt(reader.state.value, reader.now).fps.available)
    }

    @Test fun slowNodeReadsExpireTheOlderFpsResultInsteadOfPublishingItAsLive() = runBlocking {
        val reader = FakeReader().apply { frameCost = 2_001 }
        val result = readPrivilegedSample(reader, true) { reader.now }
        val fps = result.fpsAt(reader.state.value, reader.now)
        assertFalse(fps.fps.available)
        assertTrue(fps.fps.reason!!.contains("耗时过长"))
        assertEquals(reader.now, result.frame!!.sampledElapsedRealtimeMs)
    }

    @Test fun overlayUsesOnlyItsReducedFrame() = runBlocking {
        val reader = FakeReader()
        readPrivilegedSample(reader, false) { reader.now }
        assertEquals(listOf("fps", "overlay"), reader.calls)
    }

    @Test fun revokedRootCannotPublishTheFpsCapturedBeforeClose() = runBlocking {
        val reader = FakeReader()
        val result = readPrivilegedSample(reader, true) { reader.now }
        reader.close()
        assertFalse(result.fpsAt(reader.state.value, reader.now).fps.available)
    }

    @Test fun fpsFailureDoesNotPreventFreshGpuCollection() = runBlocking {
        val reader = FakeReader().apply { fpsFailure = IllegalStateException("synthetic FPS failure") }
        val result = readPrivilegedSample(reader, true) { reader.now }
        assertNotNull(result.frame)
        assertFalse(result.fpsAt(reader.state.value, reader.now).fps.available)
    }

    @Test fun cancellationDoesNotLaunchAnotherPrivilegedOperation() = runBlocking {
        val reader = FakeReader().apply { fpsFailure = CancellationException("synthetic cancellation") }
        try {
            readPrivilegedSample(reader, true) { reader.now }
            fail("Cancellation was swallowed")
        } catch (_: CancellationException) { }
        assertEquals(listOf("fps"), reader.calls)
    }
}
