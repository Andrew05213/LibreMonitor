/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.data.privileged

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.libremonitor.core.RootAccess
import java.io.*
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class OneShotFpsTest {
    private fun id(script: String) = Regex("LM:([a-f0-9]{32}):").find(script)!!.groupValues[1]
    private fun probe(script: String): String = id(script).let { "LM:$it:UID:0\nLM:$it:CAP:CapEff:\t0000000000200002\nLM:$it:END\n" }
    private fun fps(script: String, endNanos: Long = System.nanoTime()): String {
        val id = id(script)
        fun b64(text: String) = Base64.getEncoder().encodeToString(text.toByteArray())
        val times = (0..120).map { endNanos - (120 - it) * 16_666_667L }
        val body = times.joinToString("\n") { "$it $it $it" }
        return "LM:$id:FPSBEGIN\nLM:$id:UID:0\nLM:$id:PACKAGE:${b64("com.example")}\n" +
            "LM:$id:LAYER:${b64("SurfaceView[com.example/.Main]#1")}\nLM:$id:DATA:${b64(body)}\nLM:$id:FPSEND\n"
    }
    private class Fake(private val text: String, val gate: CountDownLatch = CountDownLatch(0), private val completionDelayMs: Long = 0) : Process() {
        val waiting = CountDownLatch(1)
        var destroyed = false
        override fun getOutputStream() = ByteArrayOutputStream()
        override fun getInputStream() = ByteArrayInputStream(text.toByteArray())
        override fun getErrorStream() = ByteArrayInputStream(byteArrayOf())
        override fun waitFor(): Int { gate.await(); return 0 }
        override fun waitFor(timeout: Long, unit: TimeUnit): Boolean {
            waiting.countDown()
            val budget = unit.toMillis(timeout)
            if (completionDelayMs > 0) Thread.sleep(minOf(completionDelayMs, budget))
            return completionDelayMs <= budget && gate.await((budget - completionDelayMs).coerceAtLeast(0L), TimeUnit.MILLISECONDS)
        }
        override fun exitValue() = 0
        override fun destroy() { destroyed = true; gate.countDown() }
        override fun destroyForcibly(): Process { destroy(); return this }
    }

    @Test fun disabledRootDoesNotLaunchAProcess() = runBlocking {
        val starts = AtomicInteger()
        val reader = OneShotRootReader({ starts.incrementAndGet(); Fake("") })
        assertFalse(reader.readFps()!!.fps.available)
        assertEquals(0, starts.get())
    }

    @Test fun parsesFreshExchangeThroughActualBackend() = runBlocking {
        val reader = OneShotRootReader({ script -> Fake(if (script.contains("FPSBEGIN")) fps(script) else probe(script)) })
        assertTrue(reader.requestAuthorization())
        val sample = reader.readFps()!!
        assertTrue(sample.fps.available)
        assertEquals(reader.state.value.sessionId, sample.rootSessionId)
    }

    @Test fun acceptsCompleteSlowCandidateExchangeInsteadOfLosingItAtTwoSeconds() = runBlocking {
        val now = 30_000_000_000L
        val reader = OneShotRootReader({ script ->
            if (script.contains("FPSBEGIN")) Fake(fps(script, now), completionDelayMs = 2_200L) else Fake(probe(script))
        }, monotonicNanos = { now })
        assertTrue(reader.requestAuthorization())
        assertTrue(reader.readFps()!!.fps.available)
    }

    @Test fun rootCloseRejectsInFlightFpsResult() = runBlocking {
        lateinit var process: Fake
        val ready = CountDownLatch(1)
        val reader = OneShotRootReader({ script ->
            if (script.contains("FPSBEGIN")) Fake(fps(script), CountDownLatch(1)).also { process = it; ready.countDown() }
            else Fake(probe(script))
        }, 1000, 1000)
        assertTrue(reader.requestAuthorization())
        val task = async(Dispatchers.Default) { reader.readFps() }
        withContext(Dispatchers.IO) { assertTrue(ready.await(2, TimeUnit.SECONDS)); assertTrue(process.waiting.await(2, TimeUnit.SECONDS)) }
        reader.close()
        process.gate.countDown()
        assertFalse(task.await()!!.fps.available)
        assertEquals(RootAccess.DISABLED, reader.state.value.access)
    }

    @Test fun fpsTimeoutReturnsAReasonInsteadOfAnOldNumber() = runBlocking {
        lateinit var process: Fake
        val reader = OneShotRootReader({ script ->
            if (script.contains("FPSBEGIN")) Fake("", CountDownLatch(1)).also { process = it } else Fake(probe(script))
        }, 1000, 50)
        assertTrue(reader.requestAuthorization())
        val sample = reader.readFps()!!
        assertFalse(sample.fps.available)
        assertTrue(sample.fps.reason!!.contains("超时"))
        assertTrue(process.destroyed)
    }

    @Test fun cancellingSamplingDestroysTheInFlightRootProcessPromptly() = runBlocking {
        lateinit var process: Fake
        val ready = CountDownLatch(1)
        val reader = OneShotRootReader({ script ->
            if (script.contains("FPSBEGIN")) Fake("", CountDownLatch(1)).also { process = it; ready.countDown() }
            else Fake(probe(script))
        }, readTimeoutMs = 15_000)
        assertTrue(reader.requestAuthorization())
        val task = launch(Dispatchers.Default) { reader.readFps() }
        withContext(Dispatchers.IO) {
            assertTrue(ready.await(2, TimeUnit.SECONDS))
            assertTrue(process.waiting.await(2, TimeUnit.SECONDS))
        }
        withTimeout(2_000) { task.cancelAndJoin() }
        assertTrue(process.destroyed)
        // The operation mutex is released after cancellation, so resuming does not get stuck.
        assertTrue(reader.requestAuthorization())
    }
}
