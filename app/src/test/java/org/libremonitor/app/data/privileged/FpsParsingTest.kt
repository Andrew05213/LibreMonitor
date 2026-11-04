/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.data.privileged

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FpsParsingTest {
    @Test fun parsesFrameTimesAndComputesOnePercentLow() {
        val times = mutableListOf(1_000_000_000L)
        repeat(96) { times += times.last() + 16_666_667L }
        repeat(4) { times += times.last() + 50_000_000L }
        val body = times.mapIndexed { index, time ->
            "$time ${time + 1_000_000L} ${time + 2_000_000L}"
        }.joinToString("\n")
        val sample = RootProtocol.parseFps("FPSBEGIN\nFPSLAYER:SurfaceView[example]#0\n$body\nFPSEND\n")
        assertTrue(sample.fps.available)
        assertTrue(sample.onePercentLow.available)
        assertTrue(sample.fps.value!! > sample.onePercentLow.value!!)
        assertTrue(sample.frameTimeMs.available)
        assertTrue(sample.target.value!!.contains("SurfaceView"))
    }

    @Test fun missingOrTooFewFramesRemainUnavailable() {
        val empty = RootProtocol.parseFps("FPSBEGIN\nFPSEND\n")
        assertFalse(empty.fps.available)
        val one = RootProtocol.parseFps("FPSBEGIN\nFPSLAYER:x\n1 2 3\nFPSEND\n")
        assertFalse(one.fps.available)
        assertFalse(one.onePercentLow.available)
    }
}
