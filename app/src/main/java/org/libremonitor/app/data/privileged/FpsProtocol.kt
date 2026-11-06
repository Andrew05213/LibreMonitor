/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.data.privileged

import java.util.Base64

/** Read-only SurfaceFlinger exchange. All commands are constants; layer names remain quoted data. */
internal object FpsProtocol {
    const val MAX_CANDIDATES = 4
    const val MAX_LAYERS = 64
    const val MAX_LATENCY_BYTES = 24 * 1024
    // At worst: window + activity fallback + layer list + four latency calls,
    // each taking the legacy one-second timeout, plus process/protocol overhead.
    const val PROCESS_TIMEOUT_MS = (MAX_CANDIDATES + 3) * 1_000L + 1_000L
    private val packageName = Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+")

    data class Layer(val name: String, val timestamps: List<Long>, val failure: String? = null,
        val failureCode: String? = null, val responseBytes: Int = 0, val responsePreview: String = "")
    data class Capture(val packageName: String?, val layers: List<Layer>, val failure: String? = null,
        val millisecondsTimeout: Boolean? = null, val totalCandidates: Int = layers.size,
        val offset: Int = 0, val cached: Boolean = false)
    data class Query(val offset: Int = 0, val millisecondsTimeout: Boolean? = null,
        val cachedPackage: String? = null, val cachedLayer: String? = null, val discoveryPackage: String? = null)


    fun script(id: String, query: Query = Query()): String {
        require(id.matches(Regex("[a-f0-9]{32}")))
        require(query.offset in 0 until MAX_LAYERS)
        require((query.cachedPackage == null) == (query.cachedLayer == null))
        query.cachedPackage?.let { require(packageName.matches(it)) }
        query.discoveryPackage?.let { require(packageName.matches(it)) }
        query.cachedLayer?.let { name ->
            require(name.length <= 1024 && name.none { it.code < 32 || it.code == 127 })
            require(Regex("(?:^|[^A-Za-z0-9_.])${Regex.escape(query.cachedPackage!!)}(?:/|\\]|#|$)").containsMatchIn(name))
        }
        val cached = query.cachedLayer?.let { Base64.getEncoder().encodeToString(it.toByteArray()) }.orEmpty()
        val timeout = query.millisecondsTimeout?.let { if (it) "1" else "0" }.orEmpty()
        // Android 16+ --list may print RequestedLayerState{name parentId=...}, while
        // --latency still compares against the EXACT name. Never pass that wrapper as a name.
        // AOSP references and the separately approved device checklist are in docs/FPS.md.
        return """
            {
            PATH=/system/bin:/system/xbin; export PATH
            milliseconds='$timeout'
            if [ -z "${'$'}milliseconds" ]; then
                if dumpsys --help 2>&1 | grep -q -- '-T TIMEOUT_MS'; then milliseconds=1; else milliseconds=0; fi
            fi
            if [ "${'$'}milliseconds" = 1 ]; then
                dump() { dumpsys -T 250 "${'$'}@"; }
            else
                dump() { dumpsys -t 1 "${'$'}@"; }
            fi
            printf 'LM:$id:FPSBEGIN\n'
            printf 'LM:$id:UID:'; id -u
            focus=${'$'}(dump window 2>/dev/null | grep -E 'mCurrentFocus=|mFocusedApp=' | head -8)
            pkg=${'$'}(printf '%s\n' "${'$'}focus" | grep 'mCurrentFocus=' | grep -oE '[A-Za-z][A-Za-z0-9_.]+/[A-Za-z0-9_.]+' | head -1 | cut -d/ -f1)
            if [ -z "${'$'}pkg" ]; then
                pkg=${'$'}(printf '%s\n' "${'$'}focus" | grep -oE '[A-Za-z][A-Za-z0-9_.]+/[A-Za-z0-9_.]+' | head -1 | cut -d/ -f1)
            fi
            if [ -z "${'$'}pkg" ]; then
                pkg=${'$'}(dump activity activities 2>/dev/null | grep -E 'topResumedActivity=|mResumedActivity:|ResumedActivity:' | grep -oE '[A-Za-z][A-Za-z0-9_.]+/[A-Za-z0-9_.]+' | head -1 | cut -d/ -f1)
            fi
            printf 'LM:$id:PACKAGE:'; printf '%s' "${'$'}pkg" | base64 | tr -d '\n'; printf '\n'
            start=${query.offset}; total=0; cached=0
            if [ -n "${'$'}pkg" ] && [ "${'$'}pkg" = '${query.cachedPackage.orEmpty()}' ] && [ -n '$cached' ]; then
                candidates=${'$'}(printf '%s' '$cached' | base64 -d)
                cached=1; start=0
            elif [ -n "${'$'}pkg" ]; then
                start=0
                [ "${'$'}pkg" != '${query.discoveryPackage.orEmpty()}' ] || start=${query.offset}
                escaped_pkg=${'$'}(printf '%s' "${'$'}pkg" | sed 's/[.]/\\./g')
                layers=${'$'}(dump SurfaceFlinger --list 2>/dev/null | head -c 131073 | sed \
                    -e 's/^RequestedLayerState{//' -e 's/^\[[0-9][0-9]*\]//' \
                    -e 's/ parentId=.*${'$'}//' -e 's/ relativeParentId=.*${'$'}//' -e 's/ mirrorId=.*${'$'}//' \
                    -e 's/ !handle.*${'$'}//' -e 's/ z=.*${'$'}//' -e 's/ layerStack=.*${'$'}//' -e 's/}${'$'}//' \
                    | grep -E -e "(^|[^A-Za-z0-9_.])${'$'}escaped_pkg/" -e "\\[${'$'}escaped_pkg\\]" -e "^${'$'}escaped_pkg#" -e "^${'$'}escaped_pkg${'$'}" \
                    | grep -v -E '(^|[ /])(Background|Dim|StatusBar|NavigationBar|Wallpaper|Splash Screen|Starting Window)|^ActivityRecord|^Task=')
                # Keep SurfaceView priority without requiring text tools absent on Android 8.
                ordered=${'$'}({ printf '%s\n' "${'$'}layers" | grep 'SurfaceView'; \
                    printf '%s\n' "${'$'}layers" | grep 'BLAST' | grep -v 'SurfaceView'; \
                    printf '%s\n' "${'$'}layers" | grep -v -E 'SurfaceView|BLAST'; } | grep -v '^${'$'}' | head -$MAX_LAYERS)
                total=${'$'}(printf '%s\n' "${'$'}ordered" | grep -v '^${'$'}' | wc -l | tr -d ' ')
                [ "${'$'}start" -lt "${'$'}total" ] || start=0
                candidates=${'$'}(printf '%s\n' "${'$'}ordered" | sed -n "${'$'}((start + 1)),${'$'}((start + $MAX_CANDIDATES))p" | head -$MAX_CANDIDATES)
            else
                candidates=''
            fi
            printf 'LM:$id:INFO:MS:%s\n' "${'$'}milliseconds"
            printf 'LM:$id:INFO:TOTAL:%s\n' "${'$'}total"
            printf 'LM:$id:INFO:OFFSET:%s\n' "${'$'}start"
            printf 'LM:$id:INFO:CACHED:%s\n' "${'$'}cached"
            if [ -n "${'$'}pkg" ]; then
                printf '%s\n' "${'$'}candidates" | while IFS= read -r layer; do
                    [ -n "${'$'}layer" ] || continue
                    [ "${'$'}{#layer}" -le 1024 ] || continue
                    printf 'LM:$id:LAYER:'; printf '%s' "${'$'}layer" | base64 | tr -d '\n'; printf '\n'
                    printf 'LM:$id:DATA:'
                    dump SurfaceFlinger --latency "${'$'}layer" 2>&1 | head -c ${MAX_LATENCY_BYTES + 1} | base64 | tr -d '\n'
                    printf '\n'
                done
            fi
            printf 'LM:$id:FPSEND\n'
            }
        """.trimIndent() + "\n"
    }

    fun decode(output: String, id: String): Capture {
        val prefix = "LM:$id:"
        val lines = output.lineSequence().filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) }.toList()
        if (output.toByteArray().size > RootProtocol.MAX_FRAME || lines.firstOrNull() != "FPSBEGIN" ||
            lines.lastOrNull() != "FPSEND" || lines.size < 4 || lines[1] != "UID:0") {
            return Capture(null, emptyList(), "Root 帧率响应不完整、超限或身份验证失败")
        }
        if (!lines[2].startsWith("PACKAGE:")) return Capture(null, emptyList(), "帧率响应缺少前台应用")
        val pkg = decodeText(lines[2].removePrefix("PACKAGE:"), 256)
        if (pkg.isNullOrBlank() || !packageName.matches(pkg)) return Capture(null, emptyList(), "未识别到前台应用；请在目标应用中打开悬浮窗")
        val info = linkedMapOf<String, Int>()
        var first = 3
        while (first < lines.lastIndex && lines[first].startsWith("INFO:")) {
            val fields = lines[first++].split(':')
            val key = fields.getOrNull(1)
            val value = fields.getOrNull(2)?.toIntOrNull()
            if (fields.size != 3 || key !in setOf("MS", "TOTAL", "OFFSET", "CACHED") || value == null ||
                value !in 0..MAX_LAYERS || info.put(key!!, value) != null) return Capture(pkg, emptyList(), "帧率查询元数据无效")
        }
        if (info.isNotEmpty() && (info.size != 4 || info["MS"] !in 0..1 || info["CACHED"] !in 0..1 || info["OFFSET"] !in 0 until MAX_LAYERS))
            return Capture(pkg, emptyList(), "帧率查询元数据不完整")
        val entries = lines.subList(first, lines.lastIndex)
        if (entries.size % 2 != 0 || entries.size > MAX_CANDIDATES * 2) return Capture(pkg, emptyList(), "图层响应不完整或超限")
        val layers = entries.chunked(2).map { pair ->
            if (!pair[0].startsWith("LAYER:") || !pair[1].startsWith("DATA:")) return Capture(pkg, emptyList(), "图层响应标记无效")
            val name = decodeText(pair[0].removePrefix("LAYER:"), 1024)
                ?: return Capture(pkg, emptyList(), "图层名称编码无效")
            // Match a package boundary, not com.example2 when the target is com.example.
            if (!Regex("(?:^|[^A-Za-z0-9_.])${Regex.escape(pkg)}(?:/|\\]|#|$)").containsMatchIn(name) ||
                name.any { it.code < 32 || it.code == 127 }) return Capture(pkg, emptyList(), "图层不属于前台应用或名称无效")
            val text = decodeText(pair[1].removePrefix("DATA:"), MAX_LATENCY_BYTES)
                ?: return Capture(pkg, emptyList(), "帧时间响应超限或编码无效")
            val denied = text.contains("Permission Denial", true) || text.contains("Permission denied", true)
            val timeout = text.contains("timeout", true) || text.contains("timed out", true)
            val times = text.lineSequence().mapNotNull { line ->
                val fields = line.trim().split(Regex("\\s+"))
                if (fields.size != 3 || fields.any { it.toLongOrNull() == null }) return@mapNotNull null
                fields[1].toLongOrNull()?.takeIf { it > 0L && it < Long.MAX_VALUE }
            }.distinct().sorted().toList()
            val code = when {
                denied -> "PERMISSION_DENIED"
                timeout -> "TIMEOUT"
                times.isNotEmpty() -> null
                text.isBlank() -> "EMPTY_RESPONSE"
                text.trim().toLongOrNull() != null -> "HEADER_ONLY"
                text.lineSequence().any { row -> row.trim().split(Regex("\\s+")).let { it.size == 3 && it.all { part -> part.toLongOrNull() != null } } } -> "NO_PRESENT_TIMES"
                else -> "UNRECOGNIZED_FORMAT"
            }
            val reason = when (code) {
                "PERMISSION_DENIED" -> "系统拒绝读取 SurfaceFlinger 帧时间"
                "TIMEOUT" -> "SurfaceFlinger 查询超时"
                "EMPTY_RESPONSE" -> "图层帧时间响应为空，等待重新选择图层"
                "HEADER_ONLY" -> "图层只返回刷新周期，没有帧时间；将继续查找其他图层"
                "NO_PRESENT_TIMES" -> "图层帧时间为零或尚未提交，暂时没有有效帧"
                "UNRECOGNIZED_FORMAT" -> "帧时间格式未识别，可导出采集诊断检查兼容性"
                else -> null
            }
            Layer(name, times, reason, code, text.toByteArray().size, text.take(1536))
        }
        return Capture(pkg, layers, if (layers.isEmpty()) "没有找到前台应用的可测图层" else null,
            info["MS"]?.let { it == 1 }, info["TOTAL"] ?: layers.size, info["OFFSET"] ?: 0, info["CACHED"] == 1)
    }

    private fun decodeText(encoded: String, limit: Int): String? {
        if (encoded.length > ((limit + 2) / 3) * 4) return null
        val bytes = try { Base64.getDecoder().decode(encoded) } catch (_: IllegalArgumentException) { return null }
        if (bytes.size > limit) return null
        val text = bytes.toString(Charsets.UTF_8)
        return text.takeUnless { it.any { c -> c == '\u0000' || c == '\uFFFD' } }
    }
}
