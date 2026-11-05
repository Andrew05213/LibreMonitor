/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.data.privileged

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.libremonitor.core.RootAccess
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class RootProtocolTest {
    private val id = "0123456789abcdef0123456789abcdef"
    private fun frame(body: String) = ByteArrayInputStream(body.toByteArray(Charsets.US_ASCII))
    private fun encode(body: String) = Base64.getEncoder().encodeToString(body.toByteArray())

    @Test fun fixedAllowlistAndLimits() {
        val nodes = RootProtocol.nodes()
        assertEquals(1 + 256 + 192 + 26 + 3 + 1, nodes.size)
        assertEquals(1, nodes.count { it.key == "/proc/stat" && it.limit == 256 * 1024 })
        assertEquals(1, nodes.count { it.key == "thermalservice" && it.limit == 64 * 1024 })
        assertEquals(setOf("gpuclk", "gpubusy", "gpu_busy_percentage"), nodes.filter { it.key.startsWith("/sys/class/kgsl/") }.map { it.key.substringAfterLast('/') }.toSet())
        assertTrue(nodes.filter { it.key.startsWith("/sys/class/thermal/") }.all { it.limit == 128 })
        assertTrue(nodes.filter { it.key.startsWith("/sys/class/power_supply/") || it.key.contains("/cpufreq/") || it.key.startsWith("/sys/class/kgsl/") }.all { it.limit == 256 })
        assertEquals(nodes.size, nodes.map { it.key }.toSet().size)
        assertEquals(256, nodes.count { it.key.contains("/cpufreq/") })
        assertEquals(192, nodes.count { it.key.startsWith("/sys/class/thermal/") })
        assertEquals(26, nodes.count { it.key.startsWith("/sys/class/power_supply/") })
        assertTrue(nodes.any { it.key.endsWith("cpu255/cpufreq/scaling_cur_freq") })
        assertFalse(nodes.any { it.key.contains("cpu256/") || it.key.contains("thermal_zone96/") })
        val script = RootProtocol.frameScript(id, nodes)
        assertFalse(script.contains("eval"))
        assertFalse(script.contains("*"))
        try {
            RootProtocol.frameScript(id, nodes + RootProtocol.Node("/tmp/injected", 4, "echo bad"))
            fail("arbitrary node accepted")
        } catch (_: IllegalArgumentException) { }
        val compact = RootProtocol.compactNodes()
        assertEquals(2 + 8 + 64 + 26 + 3, compact.size)
        assertTrue(RootProtocol.frameScript(id, compact).contains("LM:$id:BEGIN"))
    }

    @Test fun compoundScriptCanBeSentBeforeOutputOnSmallPipes() {
        val script = RootProtocol.frameScript(id, RootProtocol.nodes())
        assertTrue(script.startsWith("{\n"))
        assertTrue(script.endsWith("\n}\n"))
        // POSIX shell receives the exact compound wrapper and a body with output larger than
        // the bounded pipe, followed by a large script tail. It has no privileged commands.
        val prefix = script.substringBefore("PATH=/system/bin")
        val suffix = script.substringAfterLast("printf 'LM:$id:END\\n'\n")
        val synthetic = prefix + "printf '" + "x".repeat(8192) + "'\n" +
            ": #" + "padding".repeat(16000) + "\nprintf 'END\\n'\n" + suffix
        val process = ProcessBuilder("/bin/sh").start()
        val pipeIn = PipedInputStream(256)
        val pipeOut = PipedOutputStream(pipeIn)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val transfer = pool.submit { process.inputStream.use { input -> pipeOut.use { input.copyTo(it) } } }
            val writer = pool.submit { process.outputStream.use { it.write(synthetic.toByteArray()); it.flush() } }
            writer.get(5, TimeUnit.SECONDS)
            val bytes = pipeIn.readBytes()
            assertEquals(8192 + 4, bytes.size)
            assertEquals("END\n", String(bytes.takeLast(4).toByteArray()))
            transfer.get(5, TimeUnit.SECONDS)
            assertTrue(process.waitFor(5, TimeUnit.SECONDS))
            assertEquals(0, process.exitValue())
        } finally {
            process.destroyForcibly()
            pipeIn.close()
            pool.shutdownNow()
        }
    }

    @Test fun permissionScriptIsFixedToThisAppAndReadsMarkers() {
        val script = RootProtocol.permissionScript(id)
        assertTrue(script.contains("pm grant org.libremonitor.app android.permission.POST_NOTIFICATIONS"))
        assertTrue(script.contains("appops set org.libremonitor.app SYSTEM_ALERT_WINDOW allow"))
        assertTrue(script.contains("dumpsys deviceidle whitelist +org.libremonitor.app"))
        assertTrue(script.contains("android.permission.BLUETOOTH_CONNECT"))
        assertFalse(script.contains("eval"))
        assertFalse(script.contains("sh -c"))
        val input = frame(
            "LM:$id:NOTIF:OK\nLM:$id:OVERLAY:FAIL\nLM:$id:BATTERY:OK\nLM:$id:BLUETOOTH:OK\nLM:$id:END\n",
        )
        assertEquals(
            listOf("LM:$id:NOTIF:OK", "LM:$id:OVERLAY:FAIL", "LM:$id:BATTERY:OK", "LM:$id:BLUETOOTH:OK", "LM:$id:END"),
            RootProtocol.readPermissionResults(input, id),
        )
        try {
            RootProtocol.readPermissionResults(frame("LM:$id:NOTIF:OK\n"), id)
            fail("missing permission end marker accepted")
        } catch (_: ProtocolException) { }
    }

    @Test fun identityProbeRejectsFakeRoot() {
        val valid = listOf("LM:$id:UID:0", "LM:$id:CAP:CapEff:\t0000000000200002", "LM:$id:END")
        assertTrue(RootProtocol.verifyProbe(valid, id))
        assertFalse(RootProtocol.verifyProbe(valid.toMutableList().apply { this[0] = "LM:$id:UID:2000" }, id))
        assertFalse(RootProtocol.verifyProbe(valid.toMutableList().apply { this[1] = "LM:$id:CAP:CapEff:\t0" }, id))
        assertFalse(RootProtocol.verifyProbe(valid.toMutableList().apply { this[2] = "LM:$id:ENDX" }, id))
        assertFalse(RootProtocol.verifyProbe(valid.dropLast(1), id))
    }

    @Test fun markerAndTruncationAreEnforced() {
        val nodes = listOf(RootProtocol.Node("x", 4, ""), RootProtocol.Node("y", 4, ""))
        val good = "LM:$id:BEGIN\nLM:$id:0:${encode("ok")}\nLM:$id:1:${encode("12345")}\nLM:$id:END\n"
        val result = RootProtocol.readFrame(frame(good), id, nodes)
        assertEquals("ok", result["x"]?.value)
        assertNull(result["y"]?.value)
        for (bad in listOf(good.replace("LM:$id:END\n", ""), good.replace("LM:$id:1:", "LM:$id:0:"), good.replace("BEGIN", "WRONG"))) {
            try { RootProtocol.readFrame(frame(bad), id, nodes); fail("incomplete frame accepted") }
            catch (_: ProtocolException) { }
        }
    }

    @Test fun frameOverLimitFails() {
        val oversized = "LM:$id:BEGIN\n" + "a".repeat(RootProtocol.MAX_FRAME + 1)
        try {
            RootProtocol.readFrame(frame(oversized), id, listOf(RootProtocol.Node("x", RootProtocol.MAX_FRAME, "")))
            fail("oversize accepted")
        } catch (_: ProtocolException) { }
    }

    @Test fun timeoutClosesOnlyOwnedFakeAndDoesNotRetry() = runBlocking {
        val fake = BlockingProcess()
        val starts = AtomicInteger()
        val reader = RootReader({ starts.incrementAndGet(); fake }, { System.nanoTime() / 1_000_000 }, authTimeoutMs = 100, readTimeoutMs = 100)
        assertFalse(reader.requestAuthorization())
        assertTrue(fake.destroyedSignal.await(1, TimeUnit.SECONDS))
        assertTrue(fake.destroyed.get())
        assertNull(reader.readFrame())
        assertEquals(1, starts.get())
        assertNotEquals(RootAccess.GRANTED, reader.state.value.access)
        reader.close()
        reader.close()
    }

    private class BlockingProcess : Process() {
        val destroyed = AtomicBoolean()
        val destroyedSignal = CountDownLatch(1)
        private val closed = CountDownLatch(1)
        private val sink = ByteArrayOutputStream()
        private fun blocking(): InputStream = object : InputStream() {
            override fun read(): Int {
                try { closed.await(3, TimeUnit.SECONDS) } catch (_: InterruptedException) { }
                return -1
            }
            override fun close() { closed.countDown() }
        }
        private val stdout = blocking()
        private val stderr = blocking()
        override fun getOutputStream(): OutputStream = sink
        override fun getInputStream(): InputStream = stdout
        override fun getErrorStream(): InputStream = stderr
        override fun waitFor(): Int { closed.await(); return 0 }
        override fun exitValue(): Int = if (destroyed.get()) 0 else throw IllegalThreadStateException()
        override fun destroy() { destroyed.set(true); closed.countDown(); destroyedSignal.countDown() }
        override fun destroyForcibly(): Process { destroy(); return this }
    }
}
