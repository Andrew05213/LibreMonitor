/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.data.privileged

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/** Executes the generated shell ONLY against a synthetic dumpsys/id, never su or device services. */
class FpsScriptTest {
    private val id = "0123456789abcdef0123456789abcdef"

    private fun runFixture(mode: String, queryOptions: FpsProtocol.Query = FpsProtocol.Query()): Pair<FpsProtocol.Capture, List<String>> {
        val dir = Files.createTempDirectory("libremonitor-fps-fixture").toFile()
        val query = dir.resolve("queries")
        val fake = dir.resolve("dumpsys")
        fake.writeText("""
            #!/bin/sh
            if [ "${'$'}1" = --help ]; then
                [ "${'$'}MODE" != cached ] || exit 91
                [ "${'$'}MODE" = legacy ] || echo ' -T TIMEOUT_MS: timeout in milliseconds'
                exit 0
            fi
            if [ "${'$'}MODE" = legacy ]; then
                [ "${'$'}1 ${'$'}2" = '-t 1' ] || exit 20
            else
                [ "${'$'}1 ${'$'}2" = '-T 250' ] || exit 21
            fi
            shift 2
            case "${'$'}1 ${'$'}2" in
            'window ')
                if [ "${'$'}MODE" = activity ]; then echo 'mCurrentFocus=null';
                elif [ "${'$'}MODE" = other ]; then echo 'mCurrentFocus=Window{123 u0 com.other/.Main}';
                else echo 'mCurrentFocus=Window{123 u0 com.example/.Main}'; fi ;;
            'activity activities') echo 'topResumedActivity=ActivityRecord{123 u0 com.example/.Main t1}' ;;
            'SurfaceFlinger --list')
                if [ "${'$'}MODE" = cached ]; then exit 92
                elif [ "${'$'}MODE" = many ]; then
                    i=1
                    while [ "${'$'}i" -le 4 ]; do printf 'SurfaceView[com.example/.Main]Container#%s\n' "${'$'}i"; i=${'$'}((i+1)); done
                    echo 'SurfaceView[com.example/.Main]Active#5'
                elif [ "${'$'}MODE" = other ]; then echo 'SurfaceView[com.other/.Main]Active#1'
                elif [ "${'$'}MODE" = quoted ]; then
                    printf '%s\n' 'RequestedLayerState{SurfaceView[com.example/.Main] ${'$'}(touch INJECTED) "quoted"#42 parentId=4 z=2}'
                elif [ "${'$'}MODE" = raw ]; then
                    printf '%s\n' 'SurfaceView[com.example/.Main]@0(BLAST)#42'
                else
                    printf '%s\n' 'RequestedLayerState{SurfaceView[evilcom.example/.Main]#9 parentId=4}'
                    printf '%s\n' 'RequestedLayerState{SurfaceView[comXexample/.Main]#9 parentId=4}'
                    printf '%s\n' 'RequestedLayerState{SurfaceView[com.example/.Main]@0(BLAST)#42 parentId=4 relativeParentId=5 z=2}'
                    printf '%s\n' 'RequestedLayerState{com.example/.Main#4 parentId=1}'
                fi ;;
            'SurfaceFlinger --latency')
                printf '%s\n' "${'$'}3" >> "${'$'}QUERY"
                if [ "${'$'}MODE" = many ] && [ "${'$'}3" != 'SurfaceView[com.example/.Main]Active#5' ]; then printf '16666667\n'
                else printf '16666667\n100 100 100\n16666767 16666767 16666767\n33333434 33333434 33333434\n'; fi ;;
            esac
        """.trimIndent() + "\n")
        assertTrue(fake.setExecutable(true))
        dir.resolve("id").apply { writeText("#!/bin/sh\necho 0\n"); assertTrue(setExecutable(true)) }
        val script = FpsProtocol.script(id, queryOptions).replace("PATH=/system/bin:/system/xbin", "PATH=${dir.absolutePath}:/usr/bin:/bin")
        val process = ProcessBuilder("/bin/sh", "-c", script).directory(dir).apply {
            environment()["MODE"] = mode
            environment()["QUERY"] = query.absolutePath
        }.start()
        try {
            assertTrue(process.waitFor(5, TimeUnit.SECONDS))
            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            assertEquals(error, 0, process.exitValue())
            assertFalse("layer text executed as a command", dir.resolve("INJECTED").exists())
            val capture = FpsProtocol.decode(output, id)
            assertNull(capture.failure)
            return capture to query.readLines()
        } finally {
            process.destroyForcibly()
            dir.deleteRecursively()
        }
    }

    @Test fun stripsAndroid16DebugWrapperAndProbesMultipleMatchingLayers() {
        val (capture, queried) = runFixture("wrapped")
        assertEquals(listOf("SurfaceView[com.example/.Main]@0(BLAST)#42", "com.example/.Main#4"), queried)
        assertEquals(2, capture.layers.size)
        assertTrue(capture.layers.all { it.timestamps.size == 3 })
    }

    @Test fun discoveryCanReachAnActiveFifthLayerAfterFourEmptyCandidates() {
        val first = runFixture("many").first
        assertEquals(5, first.totalCandidates)
        assertTrue(first.layers.all { it.failureCode == "HEADER_ONLY" })
        val (next, queried) = runFixture("many", FpsProtocol.Query(offset = 4, discoveryPackage = "com.example"))
        assertEquals(listOf("SurfaceView[com.example/.Main]Active#5"), queried)
        assertEquals(4, next.offset)
        assertEquals(3, next.layers.single().timestamps.size)
    }

    @Test fun cachedTargetSkipsCapabilityAndLayerListQueriesButStillFollowsFocus() {
        val query = FpsProtocol.Query(millisecondsTimeout = true, cachedPackage = "com.example",
            cachedLayer = "SurfaceView[com.example/.Main]Active#5")
        val (cached, queried) = runFixture("cached", query)
        assertTrue(cached.cached)
        assertEquals(listOf(query.cachedLayer), queried)
        val (changed, changedQueries) = runFixture("other", query)
        assertFalse(changed.cached)
        assertEquals("com.other", changed.packageName)
        assertEquals(listOf("SurfaceView[com.other/.Main]Active#1"), changedQueries)
    }

    @Test fun cachedLayerShellCharactersStayLiteral() {
        val name = "SurfaceView[com.example/.Main] ${'$'}(touch INJECTED) 'quoted'#42"
        assertEquals(listOf(name), runFixture("cached", FpsProtocol.Query(millisecondsTimeout = true,
            cachedPackage = "com.example", cachedLayer = name)).second)
    }

    @Test fun supportsLegacyRawLayerNames() {
        assertEquals("SurfaceView[com.example/.Main]@0(BLAST)#42", runFixture("raw").second.single())
    }

    @Test fun fallsBackToResumedActivityWhenFocusIsNull() {
        assertEquals("com.example", runFixture("activity").first.packageName)
    }

    @Test fun usesLegacyTimeoutWhenMillisecondFlagIsNotAdvertised() {
        assertEquals(2, runFixture("legacy").first.layers.size)
    }

    @Test fun keepsShellCharactersInLayerNamesAsLiteralData() {
        val name = runFixture("quoted").second.single()
        assertTrue(name.contains("${'$'}(touch INJECTED)"))
        assertTrue(name.contains("\"quoted\""))
    }

    @Test fun scriptDoesNotEnableOrClearGlobalStats() {
        val script = FpsProtocol.script(id)
        assertFalse(script.contains("--latency-clear"))
        assertFalse(script.contains("--timestats"))
        assertFalse(script.contains("eval"))
        // Android 8/8.1 shell has no awk; the script must run with minSdk 26.
        assertFalse(script.contains("awk"))
        assertTrue(script.contains("head -${FpsProtocol.MAX_CANDIDATES}"))
    }
}
