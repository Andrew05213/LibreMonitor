/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.data.privileged

import org.libremonitor.core.Reading
import org.libremonitor.core.ReadingStatus
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Base64

/** All shell fragments are generated here from constants, never from caller input. */
internal object RootProtocol {
    const val MAX_FRAME = 512 * 1024
    const val MAX_STDERR = 16 * 1024
    const val MAX_CORES = 256
    const val MAX_ZONES = 96
    const val PACKAGE = "org.libremonitor.app"
    private const val PROC_LIMIT = 256 * 1024
    private const val HAL_LIMIT = 64 * 1024
    private val powerFields = listOf(
        "type", "scope", "present", "status", "health", "charge_full_design", "charge_full",
        "energy_full_design", "energy_full", "cycle_count", "state_of_health", "voltage_now", "current_now", "model_name",
    )
    // Prefer the direct percentage before gpubusy: some KGSL reads clear stats while powered off.
    private val gpuFields = listOf("gpuclk", "gpu_busy_percentage", "gpubusy")
    private val thermalZonePattern = Regex("/sys/class/thermal/thermal_zone([0-9]+)/(type|temp)")
    private val compactFrameNodes by lazy { buildCompactNodes() }
    private val allowedFrameNodes by lazy { (nodes() + compactNodes()).toSet() }

    data class Node(val key: String, val limit: Int, val command: String)

    fun nodes(): List<Node> {
        val result = ArrayList<Node>()
        fun file(path: String, limit: Int) { result.add(Node(path, limit, "head -c ${limit + 1} '$path'")) }
        file("/proc/stat", PROC_LIMIT)
        for (i in 0 until MAX_CORES) {
            file("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq", 256)
        }
        for (i in 0 until MAX_ZONES) {
            file("/sys/class/thermal/thermal_zone$i/type", 128)
            file("/sys/class/thermal/thermal_zone$i/temp", 128)
        }
        for (supply in listOf("battery", "bms")) for (field in powerFields) {
            file("/sys/class/power_supply/$supply/$field", 256)
        }
        for (field in gpuFields) file("/sys/class/kgsl/kgsl-3d0/$field", 256)
        result.add(Node("thermalservice", HAL_LIMIT, "/system/bin/dumpsys thermalservice | head -c ${HAL_LIMIT + 1}"))
        return result
    }

    /** Base64 confines node bytes to data, never shell syntax or framing. */
    /** Smaller frame for the one-shot Root path: fewer forks and no slow dumpsys. */
    fun compactNodes(): List<Node> = compactFrameNodes

    private fun buildCompactNodes(): List<Node> {
        val result = ArrayList<Node>()
        fun file(path: String, limit: Int) { result.add(Node(path, limit, "head -c ${limit + 1} '$path'")) }
        file("/proc/stat", PROC_LIMIT)
        file("/proc/net/dev", 65_536)
        for (i in 0 until 8) file("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq", 256)
        for (i in 0 until 32) {
            file("/sys/class/thermal/thermal_zone$i/type", 128)
            file("/sys/class/thermal/thermal_zone$i/temp", 128)
        }
        for (supply in listOf("battery", "bms")) for (field in powerFields) {
            file("/sys/class/power_supply/$supply/$field", 256)
        }
        for (field in gpuFields) file("/sys/class/kgsl/kgsl-3d0/$field", 256)
        return result
    }

    /** Four existing fixed nodes: aggregate CPU plus the displayed KGSL GPU readings. */
    fun overlayNodes(): List<Node> = compactNodes().filter {
        it.key == "/proc/stat" || it.key.startsWith("/sys/class/kgsl/")
    }

    /** Select only existing constant nodes. No caller path or shell fragment enters a command. */
    fun samplingNodes(details: Boolean, network: Boolean, metadata: Boolean, zones: Set<Int>): List<Node> {
        require(zones.all { it in 0 until 32 })
        return compactNodes().filter { node ->
            val zone = thermalZonePattern.matchEntire(node.key)
            when {
                node.key == "/proc/stat" || node.key.startsWith("/sys/class/kgsl/") -> true
                node.key == "/proc/net/dev" -> network
                !details -> false
                node.key.startsWith("/sys/class/power_supply/") && node.key.substringAfterLast('/') in setOf("present", "voltage_now", "current_now") -> true
                node.key.startsWith("/sys/devices/system/cpu/") -> true
                zone != null -> metadata || (zone.groupValues[1].toInt() in zones && zone.groupValues[2] == "temp")
                else -> metadata
            }
        }
    }

    fun diagnosticNodes(): List<Node> {
        val dollar = '$'
        // Only three derived fields can leave the filter. Never emit SSIDs, keys or MACs.
        val command = """/system/bin/dumpsys wifi | while IFS= read -r row; do case "${dollar}row" in "Dump of SoftApManager"*) ap=1; printf 'AP_BEGIN\n';; "Dump of "*) ap=0;; esac; if [ "${dollar}ap" = 1 ]; then case "${dollar}row" in "current StateMachine mode: StartedState") printf 'STATE=enabled\n';; "current StateMachine mode: IdleState") printf 'STATE=disabled\n';; "getConnectedClientList().size(): "*) count="${dollar}{row#*: }"; case "${dollar}count" in ''|*[!0-9]*) ;; *) printf 'CLIENTS=%s\n' "${dollar}count";; esac;; esac; fi; done | head -c 4097"""
        return listOf(Node("wifi_status", 4096, command))
    }

    fun frameScript(id: String, nodes: List<Node>): String {
        validateNodes(id, nodes)
        val script = StringBuilder()
        // The shell must consume the whole compound command before it can emit stdout;
        // otherwise writing a large script and then reading can deadlock on small pipes.
        script.append("{\nPATH=/system/bin; export PATH\n")
        script.append("printf 'LM:$id:BEGIN\\n'\n")
        nodes.forEachIndexed { index, node ->
            script.append("printf 'LM:$id:$index:'\n")
            if (node.key == "thermalservice") {
                script.append("( ").append(node.command).append(" ) 2>/dev/null | base64 | tr -d '\\n'\n")
            } else if (node.key.startsWith("/sys/devices/system/cpu/cpu")) {
                val cpuDirectory = node.key.substringBefore("/cpufreq/")
                script.append("if [ -d '$cpuDirectory' ]; then ")
                script.append("( ").append(node.command).append(" ) 2>/dev/null | base64 | tr -d '\\n'; fi\n")
            } else {
                script.append("( ").append(node.command).append(" ) 2>/dev/null | base64 | tr -d '\\n'\n")
            }
            script.append("printf '\\n'\n")
        }
        script.append("printf 'LM:$id:END\\n'\n}\n")
        return script.toString()
    }

    private fun validateNodes(id: String, nodes: List<Node>) {
        require(id.matches(Regex("[a-f0-9]{32}")))
        require(nodes == diagnosticNodes() || (nodes.isNotEmpty() && nodes.distinct() == nodes && nodes.all { it in allowedFrameNodes }))
    }

    /** One bounded head per node; encode the entire NUL-delimited packet once, not each node. */
    fun batchedFrameScript(id: String, nodes: List<Node>): String {
        validateNodes(id, nodes)
        return buildString {
            append("{\nPATH=/system/bin; export PATH\n")
            append("printf 'LM:$id:BATCH:'\n{\nprintf 'LM:$id:UID:'\n/system/bin/id -u\nprintf '\\000'\n")
            nodes.forEachIndexed { index, node ->
                append("printf 'LM:$id:$index:'\n")
                if (node.key.startsWith("/sys/devices/system/cpu/cpu")) {
                    val cpuDirectory = node.key.substringBefore("/cpufreq/")
                    append("if [ -d '$cpuDirectory' ]; then ")
                    append("{ ").append(node.command).append("; } 2>/dev/null; fi\n")
                } else {
                    append("{ ").append(node.command).append("; } 2>/dev/null\n")
                }
                append("printf '\\000'\n")
            }
            append("printf 'LM:$id:RAWEND\\000'\n} 2>/dev/null | base64 | tr -d '\\n'\n")
            append("printf '\\nLM:$id:BATCHEND\\n'\n}\n")
        }
    }

    /** A missing/extra NUL, item, nonce, identity or end marker invalidates the entire frame. */
    fun readBatchedFrame(input: InputStream, id: String, nodes: List<Node>): Map<String, Reading<String>> {
        validateNodes(id, nodes)
        val budget = Budget(MAX_FRAME)
        val line = readLine(input, budget)
        val prefix = "LM:$id:BATCH:"
        if (!line.startsWith(prefix)) throw ProtocolException("missing batch start marker")
        val encoded = line.substring(prefix.length)
        if (encoded.length % 4 != 0) throw ProtocolException("invalid batch encoding")
        val bytes = try { Base64.getDecoder().decode(encoded) }
            catch (_: IllegalArgumentException) { throw ProtocolException("invalid batch encoding") }
        if (readLine(input, budget) != "LM:$id:BATCHEND" || input.read() != -1)
            throw ProtocolException("missing batch end marker or trailing data")
        var offset = 0
        fun record(): ByteArray {
            val start = offset
            while (offset < bytes.size && bytes[offset] != 0.toByte()) offset++
            if (offset == bytes.size) throw ProtocolException("missing record delimiter")
            return bytes.copyOfRange(start, offset++)
        }
        if (!record().contentEquals("LM:$id:UID:0\n".toByteArray(Charsets.US_ASCII)))
            throw ProtocolException("invalid batch identity")
        val readings = LinkedHashMap<String, Reading<String>>()
        nodes.forEachIndexed { index, node ->
            val item = record()
            val marker = "LM:$id:$index:".toByteArray(Charsets.US_ASCII)
            if (item.size < marker.size || !item.copyOfRange(0, marker.size).contentEquals(marker))
                throw ProtocolException("invalid batch item marker")
            val value = item.copyOfRange(marker.size, item.size)
            if (value.size > node.limit + 1) throw ProtocolException("invalid batch item length")
            readings[node.key] = nodeReading(node, value)
        }
        if (!record().contentEquals("LM:$id:RAWEND".toByteArray(Charsets.US_ASCII)) || offset != bytes.size)
            throw ProtocolException("invalid batch record count or end marker")
        return readings
    }

    fun probeScript(id: String): String {
        require(id.matches(Regex("[a-f0-9]{32}")))
        return "PATH=/system/bin; export PATH; printf 'LM:$id:UID:'; /system/bin/id -u; " +
            "printf 'LM:$id:CAP:'; while IFS= read -r l; do case \"\u0024l\" in CapEff:*) printf '%s\\n' \"\u0024l\";; esac; done < /proc/self/status; " +
            "printf 'LM:$id:END\\n'\n"
    }

    /**
     * Fixed, app-scoped permission commands. There is deliberately no caller-provided
     * package, permission, path, or shell fragment in this script.
     */
    fun permissionScript(id: String): String {
        require(id.matches(Regex("[a-f0-9]{32}")))
        return "{\nPATH=/system/bin:/system/xbin; export PATH\n" +
            "printf 'LM:$id:NOTIF:'; if /system/bin/pm grant $PACKAGE android.permission.POST_NOTIFICATIONS >/dev/null 2>&1; then echo OK; else echo FAIL; fi\n" +
            "printf 'LM:$id:OVERLAY:'; if /system/bin/appops set $PACKAGE SYSTEM_ALERT_WINDOW allow >/dev/null 2>&1; then echo OK; else echo FAIL; fi\n" +
            "printf 'LM:$id:BATTERY:'; if /system/bin/dumpsys deviceidle whitelist +$PACKAGE >/dev/null 2>&1; then echo OK; else echo FAIL; fi\n" +
            "printf 'LM:$id:BLUETOOTH:'; if /system/bin/pm grant $PACKAGE android.permission.BLUETOOTH_CONNECT >/dev/null 2>&1; then echo OK; else echo FAIL; fi\n" +
            "printf 'LM:$id:END\\n'\n}\n"
    }

    fun fpsScript(id: String): String = FpsProtocol.script(id)

    fun readPermissionResults(input: InputStream, id: String): List<String> {
        val budget = Budget(4096)
        return List(5) { readLine(input, budget) }.also {
            if (it[4] != "LM:$id:END") throw ProtocolException("missing permission end marker")
        }
    }

    fun verifyProbe(lines: List<String>, id: String): Boolean {
        if (lines.size != 3 || lines[0] != "LM:$id:UID:0" || lines[2] != "LM:$id:END") return false
        val cap = lines[1].removePrefix("LM:$id:CAP:")
        if (cap == lines[1] || !cap.matches(Regex("CapEff:\\s*[0-9a-fA-F]+"))) return false
        val value = cap.substringAfter(':').trim().toULongOrNull(16) ?: return false
        return (value and (1uL shl 1)) != 0uL && (value and (1uL shl 21)) != 0uL
    }

    fun readLine(input: InputStream, budget: Budget): String {
        val out = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0) throw ProtocolException("unexpected EOF")
            budget.take()
            if (b == 10) break
            if (b > 127 || b == 13) throw ProtocolException("invalid framing")
            out.write(b)
        }
        return out.toString("US-ASCII")
    }

    class Budget(private var remaining: Int) {
        fun take() { if (--remaining < 0) throw ProtocolException("frame exceeds limit") }
    }

    fun readFrame(input: InputStream, id: String, nodes: List<Node>): Map<String, Reading<String>> {
        val budget = Budget(MAX_FRAME)
        if (readLine(input, budget) != "LM:$id:BEGIN") throw ProtocolException("missing start marker")
        val readings = LinkedHashMap<String, Reading<String>>()
        nodes.forEachIndexed { index, node ->
            val line = readLine(input, budget)
            val prefix = "LM:$id:$index:"
            if (!line.startsWith(prefix)) throw ProtocolException("invalid item marker")
            val encoded = line.substring(prefix.length)
            // Permit the extra sentinel byte so overlong sources become unavailable, not partial values.
            if (encoded.length > ((node.limit + 3) / 3) * 4 || encoded.length % 4 != 0) {
                throw ProtocolException("invalid item encoding")
            }
            val bytes = try { Base64.getDecoder().decode(encoded) }
                catch (_: IllegalArgumentException) { throw ProtocolException("invalid item encoding") }
            if (bytes.size > node.limit + 1) throw ProtocolException("invalid item length")
            readings[node.key] = nodeReading(node, bytes)
        }
        if (readLine(input, budget) != "LM:$id:END") throw ProtocolException("missing end marker")
        return readings
    }

    private fun nodeReading(node: Node, bytes: ByteArray): Reading<String> = when {
                bytes.size > node.limit -> Reading.unavailable(node.key, "Source output truncated", ReadingStatus.ERROR)
                bytes.isEmpty() -> Reading.unavailable(node.key, "Missing, denied or empty")
                else -> {
                    val text = try {
                        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString().trim()
                    } catch (_: Exception) { null }
                    if (text.isNullOrEmpty() || text.any { it == '\u0000' }) {
                        Reading.unavailable(node.key, "Invalid or empty source", ReadingStatus.ERROR)
                    } else Reading(text, node.key)
                }
    }
}

internal class ProtocolException(message: String) : Exception(message)
