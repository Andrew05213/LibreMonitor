/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.data

import android.util.JsonWriter
import org.libremonitor.core.PerformanceMetric
import org.libremonitor.core.PerformanceReview
import java.io.OutputStream
import java.io.OutputStreamWriter

/** No network, extra privileged reads, Bluetooth records or general service dumps. */
internal object DiagnosticsExport {
    private fun JsonWriter.optionalBoolean(value: Boolean?): JsonWriter = if (value == null) nullValue() else value(value)
    fun write(output: OutputStream, data: DiagnosticsSnapshot, device: Map<String, String>, review: PerformanceReview?) {
        JsonWriter(OutputStreamWriter(output, Charsets.UTF_8)).use { out ->
            out.setIndent("  ")
            out.beginObject().name("format").value(1).name("exported_epoch_ms").value(System.currentTimeMillis())
            out.name("scope").value("Run counters and last 120 sample cycles; in-memory data resets when the process exits. Frame responses are bounded previews, never a display-refresh FPS substitute.")
            out.name("device").beginObject(); device.forEach { (key, value) -> out.name(key).value(value) }; out.endObject()
            out.name("cycles").value(data.cycles).name("collection_errors").value(data.collectionErrors)
            out.name("fps_queries").value(data.fpsQueries).name("fps_valid").value(data.fpsValid)
            out.name("fps_failures").beginObject(); data.failures.forEach { (reason, count) -> out.name(reason).value(count) }; out.endObject()
            out.name("sampling_intervals_excluding_pauses_and_plan_changes").beginObject()
                .name("count").value(data.intervalCount).name("mean_ms").value(data.intervalMeanMs)
                .name("minimum_ms").value(data.intervalMinimumMs).name("maximum_ms").value(data.intervalMaximumMs).endObject()
            out.name("recording_batch_writes_including_compression_and_db_wait").beginObject()
                .name("batches").value(data.writes.batches).name("points").value(data.writes.points)
                .name("failures").value(data.writes.failures).name("mean_ms").value(data.writes.meanMs)
                .name("maximum_ms").value(data.writes.maximumMs).endObject()
            if (review != null) {
                out.name("selected_saved_session").beginObject().name("id").value(review.session.id)
                    .name("name").value(review.session.name).name("samples").value(review.session.sampleCount)
                    .name("fps_valid").value(review.stats[PerformanceMetric.FPS]?.validCount)
                    .name("fps_attempts").value(review.fpsAttemptCount)
                    .name("fps_failures").beginObject()
                review.fpsFailures.forEach { (reason, count) -> out.name(reason).value(count) }; out.endObject()
                out.name("observed_targets_including_failed_queries").beginObject()
                review.fpsTargets.forEach { (target, count) -> out.name(target).value(count) }; out.endObject()
                out.name("valid_fps_targets").beginObject()
                review.fpsValidTargets.forEach { (target, count) -> out.name(target).value(count) }; out.endObject().endObject()
            }
            out.name("recent").beginArray()
            data.recent.forEach { trace ->
                out.beginObject().name("started_elapsed_ms").value(trace.startedElapsedMs).name("mode").value(trace.mode)
                    .name("target_interval_ms").value(trace.targetIntervalMs).name("cycle_ms").value(trace.cycleMs)
                    .name("environment_ms").value(trace.timings.environmentMs).name("fps_ms").value(trace.timings.fpsMs)
                    .name("root_frame_ms").value(trace.timings.frameMs).name("other_collectors_ms").value(trace.timings.otherCollectorsMs)
                    .name("root_node_count").value(trace.timings.rootNodeCount).name("root_transport").value(trace.timings.rootTransport)
                    .name("record_enqueue_wait_ms").value(trace.enqueueWaitMs)
                    .name("fps_requested").value(trace.fpsRequested).name("published_fps_available").value(trace.fps.fps.available)
                    .name("published_fps").value(trace.fps.fps.value).name("published_reason").value(trace.fps.fps.reason)
                    .name("target").value(trace.fps.target.value)
                    .name("fps_read_completed_elapsed_ms").value(trace.fps.sampledElapsedRealtimeMillis)
                trace.battery?.let { battery ->
                    out.name("battery").beginObject().name("voltage_v").value(battery.voltageV.value)
                        .name("current_ma").value(battery.currentMa.value).name("power_w").value(battery.powerW.value)
                        .name("current_source").value(battery.currentMa.source).name("current_reason").value(battery.currentMa.reason)
                    battery.electricalDiagnostics?.let { electrical ->
                        out.name("raw_android_current").value(electrical.rawApiCurrent.value)
                            .name("selected_supply").value(electrical.selectedSupply).name("current_unit").value(electrical.currentUnit)
                            .name("nodes").beginObject()
                        electrical.nodes.forEach { (path, reading) ->
                            out.name(path).beginObject().name("value").value(reading.value).name("status").value(reading.status.name)
                                .name("reason").value(reading.reason).endObject()
                        }
                        out.endObject()
                    }
                    out.endObject()
                }
                trace.attempt?.let { attempt ->
                    out.name("query_ms").value(attempt.durationMillis).name("query_fps_available").value(attempt.sample.fps.available)
                        .name("sampled_monotonic_ns").value(attempt.sampledMonotonicNanos)
                    val capture = attempt.capture
                    out.name("package").value(capture?.packageName).name("capture_failure").value(capture?.failure)
                        .name("candidate_count").value(capture?.totalCandidates).name("candidate_offset").value(capture?.offset)
                        .name("cached_layer_query").optionalBoolean(capture?.cached).name("milliseconds_timeout").optionalBoolean(capture?.millisecondsTimeout)
                        .name("layers").beginArray()
                    capture?.layers?.forEachIndexed { index, layer ->
                        out.beginObject().name("name").value(layer.name).name("failure_code").value(layer.failureCode)
                            .name("reason").value(layer.failure).name("response_bytes").value(layer.responseBytes)
                            .name("valid_timestamp_count").value(attempt.timestampCounts.getOrNull(index) ?: layer.timestamps.size).name("newest_present_ns").value(layer.timestamps.lastOrNull())
                            .name("latency_response_preview").value(layer.responsePreview).endObject()
                    }
                    out.endArray()
                }
                out.endObject()
            }
            out.endArray().endObject()
        }
    }
}
