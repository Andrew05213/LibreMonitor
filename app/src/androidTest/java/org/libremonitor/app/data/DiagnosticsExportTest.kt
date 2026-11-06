/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.libremonitor.app.data.privileged.*
import org.libremonitor.core.*
import java.io.ByteArrayOutputStream

/** Android's real JsonWriter/JSONObject, not a host mock. */
@RunWith(AndroidJUnit4::class)
class DiagnosticsExportTest {
    @Test fun exportEscapesLayerDataAndPreservesUnavailableFieldsAsJsonNull() {
        val layer = "SurfaceView[com.example/.Main] \"quoted\"#1"
        val response = "8333333\n0 0 0\n"
        val fps = FpsSample(fps = Reading.unavailable("fixture", "没有帧"), target = Reading(layer, "fixture"))
        val capture = FpsProtocol.Capture("com.example", listOf(FpsProtocol.Layer(layer, emptyList(),
            "没有帧", "NO_PRESENT_TIMES", response.length, response)))
        val diagnostics = SamplingDiagnostics()
        diagnostics.record(SamplingTrace(1_000, 100, 150, "PERFORMANCE", true, fps, SamplerTimings(), FpsAttempt(50, fps, capture)), 1)
        diagnostics.record(SamplingTrace(1_200, 100, 100, "PERFORMANCE", false, FpsSample(), SamplerTimings(), null), 1)
        val output = ByteArrayOutputStream()
        DiagnosticsExport.write(output, diagnostics.snapshot(), mapOf("model" to "fixture"), null)
        val json = JSONObject(output.toString("UTF-8"))
        assertEquals(2, json.getLong("cycles"))
        assertEquals(1, json.getLong("fps_queries"))
        val trace = json.getJSONArray("recent").getJSONObject(0)
        assertTrue(trace.isNull("published_fps"))
        assertTrue(trace.isNull("milliseconds_timeout"))
        assertEquals(layer, trace.getJSONArray("layers").getJSONObject(0).getString("name"))
        assertEquals(response, trace.getJSONArray("layers").getJSONObject(0).getString("latency_response_preview"))
    }
}
