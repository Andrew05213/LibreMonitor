/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.data.privileged

import java.util.Base64

/** Read-only SurfaceFlinger exchange. All commands are constants; layer names remain quoted data. */
internal object FpsProtocol {
    const val MAX_CANDIDATES = 4
    const val MAX_LATENCY_BYTES = 24 * 1024
    private val packageName = Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+")

    data class Layer(val name: String, val timestamps: List<Long>, val failure: String? = null)
    data class Capture(val packageName: String?, val layers: List<Layer>, val failure: String? = null)

    fun script(id: String): String {
        require(id.matches(Regex("[a-f0-9]{32}")))
        // Android 16+ --list may print RequestedLayerState{name parentId=...}, while
        // --latency still compares against the EXACT name. Never pass that wrapper as a name.
        // AOSP references and the separately approved device checklist are in docs/FPS.md.
        return """
            {
            PATH=/system/bin:/system/xbin; export PATH
            printf 'LM:$id:FPSBEGIN\n'
            printf 'LM:$id:UID:'; id -u
            focus=${'$'}(dumpsys -t 1 window 2>/dev/null | grep -E 'mCurrentFocus=|mFocusedApp=' | head -8)
            pkg=${'$'}(printf '%s\n' "${'$'}focus" | grep 'mCurrentFocus=' | grep -oE '[A-Za-z][A-Za-z0-9_.]+/[A-Za-z0-9_.]+' | head -1 | cut -d/ -f1)
            if [ -z "${'$'}pkg" ]; then
                pkg=${'$'}(printf '%s\n' "${'$'}focus" | grep -oE '[A-Za-z][A-Za-z0-9_.]+/[A-Za-z0-9_.]+' | head -1 | cut -d/ -f1)
            fi
            if [ -z "${'$'}pkg" ]; then
                pkg=${'$'}(dumpsys -t 1 activity activities 2>/dev/null | grep -E 'topResumedActivity=|mResumedActivity:|ResumedActivity:' | grep -oE '[A-Za-z][A-Za-z0-9_.]+/[A-Za-z0-9_.]+' | head -1 | cut -d/ -f1)
            fi
            printf 'LM:$id:PACKAGE:'; printf '%s' "${'$'}pkg" | base64 | tr -d '\n'; printf '\n'
            if [ -n "${'$'}pkg" ]; then
                layers=${'$'}(dumpsys -t 1 SurfaceFlinger --list 2>/dev/null | head -c 131073 | sed \
                    -e 's/^RequestedLayerState{//' -e 's/^\[[0-9][0-9]*\]//' \
                    -e 's/ parentId=.*${'$'}//' -e 's/ relativeParentId=.*${'$'}//' -e 's/ mirrorId=.*${'$'}//' \
                    -e 's/ !handle.*${'$'}//' -e 's/ z=.*${'$'}//' -e 's/ layerStack=.*${'$'}//' -e 's/}${'$'}//' \
                    | awk -v pkg="${'$'}pkg" 'function belongs(s,n, p) { p=index(s,n); return p>0 && (p==1 || substr(s,p-1,1) !~ /[A-Za-z0-9_.]/) } belongs(${'$'}0,pkg "/") || index(${'$'}0,"[" pkg "]") || ${'$'}0==pkg || index(${'$'}0,pkg "#")==1' \
                    | grep -v -E '(^|[ /])(Background|Dim|StatusBar|NavigationBar|Wallpaper|Splash Screen|Starting Window)|^ActivityRecord|^Task=')
                candidates=${'$'}({ printf '%s\n' "${'$'}layers" | grep -E 'SurfaceView|BLAST'; printf '%s\n' "${'$'}layers"; } | awk 'NF && !seen[${'$'}0]++' | head -$MAX_CANDIDATES)
                printf '%s\n' "${'$'}candidates" | while IFS= read -r layer; do
                    [ -n "${'$'}layer" ] || continue
                    [ "${'$'}{#layer}" -le 1024 ] || continue
                    printf 'LM:$id:LAYER:'; printf '%s' "${'$'}layer" | base64 | tr -d '\n'; printf '\n'
                    printf 'LM:$id:DATA:'
                    dumpsys -t 1 SurfaceFlinger --latency "${'$'}layer" 2>&1 | head -c ${MAX_LATENCY_BYTES + 1} | base64 | tr -d '\n'
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
        val entries = lines.subList(3, lines.lastIndex)
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
            Layer(name, times, when {
                denied -> "系统拒绝读取 SurfaceFlinger 帧时间"
                timeout -> "SurfaceFlinger 查询超时"
                times.isEmpty() -> "此图层没有可用帧时间；当前 ROM 可能不支持该接口"
                else -> null
            })
        }
        return Capture(pkg, layers, if (layers.isEmpty()) "没有找到前台应用的可测图层" else null)
    }

    private fun decodeText(encoded: String, limit: Int): String? {
        if (encoded.length > ((limit + 2) / 3) * 4) return null
        val bytes = try { Base64.getDecoder().decode(encoded) } catch (_: IllegalArgumentException) { return null }
        if (bytes.size > limit) return null
        val text = bytes.toString(Charsets.UTF_8)
        return text.takeUnless { it.any { c -> c == '\u0000' || c == '\uFFFD' } }
    }
}
