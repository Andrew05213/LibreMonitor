/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.data.privileged

import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class FpsParsingTest {
    private val id = "0123456789abcdef0123456789abcdef"
    private val layer = "SurfaceView[com.example/com.example.Main]@0(BLAST)#42"
    private fun encoded(text: String) = Base64.getEncoder().encodeToString(text.toByteArray())
    private fun body(times: List<Long>) = "16666667\n" + times.joinToString("\n") { "$it $it $it" }
    private fun capture(vararg layers: Pair<String, String>, pkg: String = "com.example") =
        "LM:$id:FPSBEGIN\nLM:$id:UID:0\nLM:$id:PACKAGE:${encoded(pkg)}\n" +
            layers.joinToString("") { (name, data) -> "LM:$id:LAYER:${encoded(name)}\nLM:$id:DATA:${encoded(data)}\n" } + "LM:$id:FPSEND\n"
    private fun sixty(count: Int = 120, start: Long = 20_000_000_000L) = (0..count).map { start + it * 16_666_667L }

    @Test fun computesFpsAndLowFromActualPresentColumn() {
        val times = sixty(100).toMutableList()
        times += times.last() + 50_000_000L
        val sample = FpsTracker().sample(FpsProtocol.decode(capture(layer to body(times)), id), times.last())
        assertTrue(sample.fps.available)
        assertTrue(sample.onePercentLow.available)
        assertTrue(sample.fps.value!! > sample.onePercentLow.value!!)
        assertEquals(layer, sample.target.value)
    }

    @Test fun rejectsDuplicateHistoryUntilNewFrameArrives() {
        val times = sixty()
        val tracker = FpsTracker()
        val data = FpsProtocol.decode(capture(layer to body(times)), id)
        assertTrue(tracker.sample(data, times.last()).fps.available)
        assertFalse(tracker.sample(data, times.last() + 100_000_000L).fps.available)
        val next = times + (times.last() + 16_666_667L)
        assertTrue(tracker.sample(FpsProtocol.decode(capture(layer to body(next)), id), next.last()).fps.available)
    }

    @Test fun oldAndFutureTimestampsStayUnavailable() {
        val times = sixty()
        val frame = FpsProtocol.decode(capture(layer to body(times)), id)
        assertFalse(FpsTracker().sample(frame, times.last() + 3_000_000_001L).fps.available)
        assertFalse(FpsTracker().sample(frame, times.first() - 1L).fps.available)
    }

    @Test fun preservesOnePointFiveSecondStall() {
        val times = sixty(50).toMutableList()
        times += times.last() + 1_500_000_000L
        repeat(50) { times += times.last() + 16_666_667L }
        val sample = FpsTracker().sample(FpsProtocol.decode(capture(layer to body(times)), id), times.last())
        assertTrue(sample.fps.value!! < 40.0)
        assertTrue(sample.onePercentLow.value!! < 2.0)
    }

    @Test fun insufficientSamplesDoNotInventOnePercentLow() {
        val times = sixty(20)
        val result = FpsTracker().sample(FpsProtocol.decode(capture(layer to body(times)), id), times.last())
        assertTrue(result.fps.available)
        assertFalse(result.onePercentLow.available)
        assertTrue(result.onePercentLow.reason!!.contains("100"))
    }

    @Test fun choosesFreshSurfaceInsteadOfContainerOrStaleSurface() {
        val times = sixty()
        val empty = "com.example/com.example.Main#10"
        val old = "SurfaceView[com.example/com.example.Old]#1"
        val parsed = FpsProtocol.decode(capture(empty to "16666667\n", old to body(sixty(start = 1_000_000_000L)), layer to body(times)), id)
        assertEquals(layer, FpsTracker().sample(parsed, times.last()).target.value)
    }

    @Test fun followsTargetChangeAndResetDoesNotReuseLastTarget() {
        val tracker = FpsTracker()
        val times = sixty()
        val first = FpsProtocol.decode(capture(layer to body(times)), id)
        assertTrue(tracker.sample(first, times.last()).fps.available)
        tracker.reset()
        assertTrue(tracker.sample(first, times.last()).fps.available)
        val other = "SurfaceView[com.other/com.other.Main]#3"
        val second = FpsProtocol.decode(capture(other to body(times), pkg = "com.other"), id)
        assertEquals(other, tracker.sample(second, times.last()).target.value)
    }

    @Test fun removesZeroPendingAndDuplicateRecords() {
        val times = sixty()
        val data = "0 0 0\n1 ${Long.MAX_VALUE} 2\n" + body(times) + "\n${times.last()} ${times.last()} ${times.last()}\n"
        val parsed = FpsProtocol.decode(capture(layer to data), id)
        assertEquals(times, parsed.layers.single().timestamps)
    }

    @Test fun validatesNonceRootIdentityAndCompleteExchange() {
        val raw = capture(layer to body(sixty()))
        assertNotNull(FpsProtocol.decode(raw, "f".repeat(32)).failure)
        assertNotNull(FpsProtocol.decode(raw.replace("UID:0", "UID:2000"), id).failure)
        assertNotNull(FpsProtocol.decode(raw.replace("LM:$id:FPSEND", ""), id).failure)
        assertNotNull(FpsProtocol.decode(raw.replace("DATA:", "OTHER:"), id).failure)
    }

    @Test fun rejectsForeignPackagesMalformedEncodingAndTooManyCandidates() {
        assertNotNull(FpsProtocol.decode(capture("evilcom.example/Main" to body(sixty())), id).failure)
        assertNotNull(FpsProtocol.decode(capture("com.example2/Main" to body(sixty())), id).failure)
        assertNotNull(FpsProtocol.decode(capture(layer to "x".repeat(FpsProtocol.MAX_LATENCY_BYTES + 1)), id).failure)
        assertNotNull(FpsProtocol.decode(capture(*Array(5) { "$layer$it" to "" }), id).failure)
        assertNotNull(FpsProtocol.decode(capture(layer to "").replace("DATA:\n", "DATA:!!!!\n"), id).failure)
    }

    @Test fun missingLayerAndPermissionFailureExplainUnavailability() {
        assertNotNull(FpsProtocol.decode(capture(), id).failure)
        val parsed = FpsProtocol.decode(capture(layer to "Permission Denial: can't dump"), id)
        val result = FpsTracker().sample(parsed, 30_000_000_000L)
        assertFalse(result.fps.available)
        assertTrue(result.fps.reason!!.contains("拒绝"))
    }

    @Test fun headerOnlyIsNotInterpretedAsRefreshRateFps() {
        val parsed = FpsProtocol.decode(capture(layer to "8333333\n"), id)
        assertFalse(FpsTracker().sample(parsed, 30_000_000_000L).fps.available)
    }
}
