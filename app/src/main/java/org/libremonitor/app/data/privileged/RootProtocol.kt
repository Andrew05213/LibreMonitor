/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.data.privileged

import org.libremonitor.core.FpsSample
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
        "energy_full_design", "energy_full", "cycle_count", "state_of_health", "voltage_now", "current_now",
    )
    private val gpuFields = listOf("gpuclk", "gpubusy", "gpu_busy_percentage")

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
    fun compactNodes(): List<Node> {
        val result = ArrayList<Node>()
        fun file(path: String, limit: Int) { result.add(Node(path, limit, "head -c ${limit + 1} '$path'")) }
        file("/proc/stat", PROC_LIMIT)
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

    fun frameScript(id: String, nodes: List<Node>): String {
        require(id.matches(Regex("[a-f0-9]{32}")))
        require(nodes == nodes() || nodes == compactNodes())
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

    /**
     * Fixed script: find the focused package, choose its top visible layer, then ask
     * SurfaceFlinger for that layer's frame timestamps. Layer names are allowlisted
     * before being passed to the shell.
     */
    fun fpsScript(id: String): String {
        require(id.matches(Regex("[a-f0-9]{32}")))
        return "{\nPATH=/system/bin:/system/xbin; export PATH\n" +
            "printf 'FPSBEGIN\\n'\n" +
            "focus=\u0024(dumpsys window 2>/dev/null | grep -m1 'mCurrentFocus=')\n" +
            "pkg=\u0024(printf '%s\\n' \"\u0024focus\" | grep -oE '[A-Za-z0-9_.]+/[A-Za-z0-9_.]+' | head -1 | cut -d/ -f1)\n" +
            "layer=\u0024(if [ -n \"\u0024pkg\" ]; then dumpsys SurfaceFlinger --list 2>/dev/null | grep -F \"\u0024pkg\" | grep -v -E 'Background|Dim|StatusBar|NavigationBar|Wallpaper' | tail -1; fi)\n" +
            // Layer names commonly contain [] and spaces; reject shell-active characters only.
            "if printf '%s' \"\u0024layer\" | grep -q '[\"\u0024`]'; then layer=; fi\n" +
            "if [ -n \"\u0024layer\" ]; then printf 'FPSLAYER:%s\\n' \"\u0024layer\"; dumpsys SurfaceFlinger --latency \"\u0024layer\" 2>/dev/null; fi\n" +
            "printf 'FPSEND\\n'\n}\n"
    }

    fun parseFps(output: String): FpsSample {
        val lines = output.lines()
        val begin = lines.indexOf("FPSBEGIN")
        val end = lines.indexOf("FPSEND")
        if (begin < 0 || end <= begin) return unavailableFps("SurfaceFlinger 未返回 FPS 数据")
        val body = lines.subList(begin + 1, end)
        val layer = body.firstOrNull { it.startsWith("FPSLAYER:") }
            ?.removePrefix("FPSLAYER:")?.takeIf { it.isNotBlank() }
        val times = body.mapNotNull { line ->
            val fields = line.trim().split(Regex("\\s+"))
            fields.getOrNull(1)?.toLongOrNull()?.takeIf { it > 0L }
        }.sorted()
        if (times.size < 2) return unavailableFps("前台图层没有可用的帧时间", layer)
        val intervals = times.zipWithNext { a, b -> b - a }.filter { it in 1_000_000L..1_000_000_000L }
        if (intervals.size < 2) return unavailableFps("帧时间样本不足", layer)
        val mean = intervals.average()
        val slowest = intervals.sortedDescending()
        val lowCount = maxOf(1, kotlin.math.ceil(slowest.size * 0.01).toInt())
        val lowMean = slowest.take(lowCount).average()
        val source = "SurfaceFlinger --latency${layer?.let { " ($it)" } ?: ""}"
        return FpsSample(
            fps = Reading(1_000_000_000.0 / mean, source, reason = "按实际上屏帧时间计算"),
            onePercentLow = Reading(1_000_000_000.0 / lowMean, source, reason = "最慢 1% 帧的平均帧率"),
            frameTimeMs = Reading(mean / 1_000_000.0, source),
            target = layer?.let { Reading(it, source) }
                ?: Reading.unavailable(source, "未定位到前台图层"),
        )
    }

    private fun unavailableFps(reason: String, layer: String? = null) = FpsSample(
        fps = Reading.unavailable("SurfaceFlinger", reason),
        onePercentLow = Reading.unavailable("SurfaceFlinger", reason),
        frameTimeMs = Reading.unavailable("SurfaceFlinger", reason),
        target = layer?.let { Reading(it, "SurfaceFlinger") }
            ?: Reading.unavailable("SurfaceFlinger", "未定位到前台图层"),
    )

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
            readings[node.key] = when {
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
        if (readLine(input, budget) != "LM:$id:END") throw ProtocolException("missing end marker")
        return readings
    }
}

internal class ProtocolException(message: String) : Exception(message)
