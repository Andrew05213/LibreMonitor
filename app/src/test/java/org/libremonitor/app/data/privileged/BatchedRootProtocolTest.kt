/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.data.privileged

import org.junit.Assert.*
import org.junit.Test
import org.libremonitor.core.ReadingStatus
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.util.Base64
import java.util.concurrent.TimeUnit

/** Host fixtures only: no su, Android service or real phone is used. */
class BatchedRootProtocolTest {
    private val id = "0123456789abcdef0123456789abcdef"
    private val nodes = RootProtocol.overlayNodes()
    private fun raw(values: List<ByteArray>, uid: String = "0") = buildList {
        add("LM:$id:UID:$uid\n".toByteArray())
        values.forEachIndexed { i, value -> add("LM:$id:$i:".toByteArray() + value) }
        add("LM:$id:RAWEND".toByteArray())
    }.fold(byteArrayOf()) { all, record -> all + record + byteArrayOf(0) }
    private fun wire(raw: ByteArray): String = "LM:$id:BATCH:${Base64.getEncoder().encodeToString(raw)}\nLM:$id:BATCHEND\n"
    private fun read(wire: String) = RootProtocol.readBatchedFrame(ByteArrayInputStream(wire.toByteArray()), id, nodes)
    private fun reject(wire: String) {
        try { read(wire); fail("malformed batch accepted") } catch (_: ProtocolException) { }
    }

    @Test fun multilineUtf8AndShellSyntaxRemainData() {
        val values = listOf("cpu  1 2 3 4\ncpu0 1 2 3 4\n", "设备 \u0024(foo) ' ;\n", "35 %\n", "25 100\n")
        val result = read(wire(raw(values.map { it.toByteArray() })))
        nodes.zip(values).forEach { (node, value) -> assertEquals(value.trim(), result[node.key]!!.value) }
    }

    @Test fun emptyOversizedAndInvalidUtf8AreUnavailableInsteadOfPartialOrZero() {
        val values = listOf(byteArrayOf(), ByteArray(257) { 49 }, byteArrayOf(0xc3.toByte(), 0x28), "0\n".toByteArray())
        val result = read(wire(raw(values)))
        assertFalse(result[nodes[0].key]!!.available)
        assertEquals(ReadingStatus.ERROR, result[nodes[1].key]!!.status)
        assertEquals(ReadingStatus.ERROR, result[nodes[2].key]!!.status)
        assertEquals("0", result[nodes[3].key]!!.value)
        reject(wire(raw(values.toMutableList().apply { this[1] = ByteArray(258) { 49 } })))
    }

    @Test fun nonceIdentityCountNulAndEndMarkersAreStrict() {
        val values = List(nodes.size) { "1\n".toByteArray() }
        val packet = raw(values)
        val valid = wire(packet)
        reject(valid.replace(id, "a".repeat(32)))
        reject(wire(raw(values, "2000")))
        reject(wire(raw(values.dropLast(1))))
        reject(wire(raw(values + "2".toByteArray())))
        reject(wire(raw(values.toMutableList().apply { this[1] = "x\u0000y".toByteArray() })))
        reject(wire(packet.copyOf(packet.size - 1)))
        reject(wire(String(packet).replace("LM:$id:1:", "LM:$id:0:").toByteArray()))
        reject(wire(String(packet).replace("RAWEND", "WRONG").toByteArray()))
        reject(valid.substringBefore("LM:$id:BATCHEND"))
        reject(valid + "extra\n")
        reject(valid.replace(":BATCH:", ":BATCH:!"))
        reject("LM:$id:BATCH:" + "a".repeat(RootProtocol.MAX_FRAME))
    }

    @Test fun commandChangesDuplicateNodesAndArbitraryPathsAreRejected() {
        val invalid = listOf(nodes + nodes.first(), listOf(nodes.first().copy(command = "echo injected")),
            listOf(RootProtocol.Node("/tmp/arbitrary", 10, "cat /tmp/arbitrary")), emptyList())
        invalid.forEach { list ->
            try { RootProtocol.batchedFrameScript(id, list); fail("invalid command accepted") }
            catch (_: IllegalArgumentException) { }
        }
        val script = RootProtocol.batchedFrameScript(id, RootProtocol.compactNodes())
        assertEquals(1, Regex("\\| base64").findAll(script).count())
        assertEquals(1, Regex("\\| tr").findAll(script).count())
        assertFalse(script.contains("( head"))
        RootProtocol.batchedFrameScript(id, RootProtocol.diagnosticNodes())
    }

    @Test fun generatedShellAndRealBase64RoundTripBoundedNodeOutput() {
        val dir = Files.createTempDirectory("libremonitor-batch-").toFile()
        try {
            // Only the executable lookup is adapted for the host; constant node commands stay intact.
            val head = dir.resolve("head")
            head.writeText("""#!/bin/sh
                |case "${'$'}3" in
                |/proc/stat) printf 'cpu  1 2 3 4\ncpu0 1 2 3 4\n';;
                |*/gpuclk) printf '500000000\n';;
                |*/gpu_busy_percentage) printf '35 %%\n';;
                |*/gpubusy) printf '25 100\n';;
                |*) exit 1;;
                |esac
                |""".trimMargin())
            assertTrue(head.setExecutable(true))
            val script = RootProtocol.batchedFrameScript(id, nodes)
                .replace("PATH=/system/bin", "PATH='${dir.absolutePath}':/usr/bin:/bin")
                .replace("/system/bin/id -u", "printf '0\\n'")
            val process = ProcessBuilder("sh", "-c", script).start()
            val output = process.inputStream.bufferedReader().readText()
            assertTrue(process.waitFor(5, TimeUnit.SECONDS))
            assertEquals(process.errorStream.bufferedReader().readText(), 0, process.exitValue())
            val result = read(output)
            assertEquals("cpu  1 2 3 4\ncpu0 1 2 3 4", result["/proc/stat"]!!.value)
            assertEquals("35 %", result["/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage"]!!.value)
        } finally { dir.deleteRecursively() }
    }
}
