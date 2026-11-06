/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.data.privileged

import org.junit.Assert.*
import org.junit.Test
import org.libremonitor.core.*

class FpsQueryPlannerTest {
    private val name = "SurfaceView[com.example/.Main]#5"
    private val valid = FpsSample(fps = Reading(60.0, "fixture"), target = Reading(name, "fixture"))

    @Test fun emptyPagesRotateThenCacheAWorkingTargetAndInvalidateOnExpiryOrFailure() {
        val planner = FpsQueryPlanner()
        val first = FpsProtocol.Capture("com.example", emptyList(), millisecondsTimeout = true, totalCandidates = 5)
        planner.accept(first, FpsSample(), 100)
        assertEquals(4, planner.query(200).offset)
        val found = first.copy(layers = listOf(FpsProtocol.Layer(name, listOf(1, 2, 3))), offset = 4)
        planner.accept(found, valid, 200)
        assertEquals(name, planner.query(300).cachedLayer)
        assertEquals(true, planner.query(300).millisecondsTimeout)
        planner.accept(found.copy(cached = true, totalCandidates = 0), FpsSample(), 400)
        assertNull(planner.query(500).cachedLayer)
        planner.accept(found, valid, 1_000)
        assertNull(planner.query(5_000_001_000).cachedLayer)
        planner.reset()
        assertNull(planner.query(0).millisecondsTimeout)
    }

    @Test fun cursorWrapsAtTheBoundedEndAndDoesNotGrowWithoutLimit() {
        val planner = FpsQueryPlanner()
        planner.accept(FpsProtocol.Capture("com.example", emptyList(), totalCandidates = 64, offset = 60), FpsSample(), 0)
        assertEquals(0, planner.query(1).offset)
        try { FpsProtocol.script("a".repeat(32), FpsProtocol.Query(offset = 64)); fail("unbounded offset") }
        catch (_: IllegalArgumentException) { }
        try { FpsProtocol.script("a".repeat(32), FpsProtocol.Query(cachedPackage = "com.example", cachedLayer = "com.other/.Main")); fail("foreign cached layer") }
        catch (_: IllegalArgumentException) { }
    }
}
