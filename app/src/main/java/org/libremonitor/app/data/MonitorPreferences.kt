/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.libremonitor.core.MonitorSettings
import org.libremonitor.core.OverlayOptions

/**
 * Defaults are available immediately. Loading and all writes run in one IO worker.
 * Setters enqueue updates in order; close drains accepted writes before returning.
 */
class MonitorPreferences(context: Context) {
    private val applicationContext = context.applicationContext ?: context
    private val mutableSettings = MutableStateFlow(MonitorSettings())
    val settings: StateFlow<MonitorSettings> = mutableSettings.asStateFlow()
    private val mutableLoaded = MutableStateFlow(false)
    /** True only after private preferences have been decoded and initially published. */
    val loaded: StateFlow<Boolean> = mutableLoaded.asStateFlow()

    private val lifecycleLock = Any()
    private var closed = false // Guarded by lifecycleLock.
    private val commands = Channel<Command>(Channel.UNLIMITED)
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, error ->
            Log.e(LOG_TAG, "Unable to access private monitor preferences", error)
        },
    )
    private val worker = scope.launch {
        try {
            val preferences = applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            val original = preferences.all
            var current = decode(original)
            // Wrong-typed and out-of-range values are replaced, not allowed into the model.
            if (!matches(original, current)) persist(preferences, current)
            mutableSettings.value = current
            mutableLoaded.value = true

            for (command in commands) {
                val next = when (command) {
                    is Command.SetHistory -> current.copy(historyEnabled = command.enabled)
                    is Command.SetSampleInterval -> current.copy(sampleIntervalMs = command.millis)
                    is Command.SetAutoRestoreRoot -> current.copy(autoRestoreRoot = command.enabled)
                    is Command.SetOverlay -> current.copy(overlay = command.options)
                    is Command.SetCurrentMode -> current.copy(batteryCurrentMode = command.mode)
                }
                if (next != current) {
                    persist(preferences, next)
                    current = next
                    mutableSettings.value = next
                }
            }
        } finally {
            // A failed worker must not leave a queue accepting updates forever.
            commands.close()
        }
    }

    fun setHistoryEnabled(enabled: Boolean) {
        enqueue(Command.SetHistory(enabled))
    }

    fun setSampleIntervalMs(value: Long) {
        enqueue(Command.SetSampleInterval(value.coerceIn(MonitorSettings.SAMPLE_INTERVAL_RANGE_MS)))
    }

    fun setAutoRestoreRoot(enabled: Boolean) {
        enqueue(Command.SetAutoRestoreRoot(enabled))
    }

    fun setOverlayOptions(options: OverlayOptions) { enqueue(Command.SetOverlay(options)) }
    fun setBatteryCurrentMode(mode: org.libremonitor.core.BatteryCurrentMode) { enqueue(Command.SetCurrentMode(mode)) }

    fun close() {
        synchronized(lifecycleLock) {
            if (!closed) {
                closed = true
                commands.close()
            }
        }
        // Disk work remains on Dispatchers.IO, including final accepted commands.
        runBlocking { worker.join() }
        scope.cancel()
    }

    private fun enqueue(command: Command) {
        synchronized(lifecycleLock) {
            check(!closed) { "MonitorPreferences is closed" }
            check(commands.trySend(command).isSuccess) { "MonitorPreferences worker is unavailable" }
        }
    }

    private fun decode(values: Map<String, *>): MonitorSettings {
        val defaults = MonitorSettings()
        return MonitorSettings(
            historyEnabled = values[HISTORY_ENABLED] as? Boolean ?: defaults.historyEnabled,
            sampleIntervalMs = (values[SAMPLE_INTERVAL] as? Long ?: defaults.sampleIntervalMs)
                .coerceIn(MonitorSettings.SAMPLE_INTERVAL_RANGE_MS),
            historyIntervalMs = (values[HISTORY_INTERVAL] as? Long ?: defaults.historyIntervalMs)
                .coerceIn(10_000L, 3_600_000L),
            retentionDays = (values[RETENTION_DAYS] as? Int ?: defaults.retentionDays)
                .coerceIn(1, 30),
            autoRestoreRoot = values[AUTO_RESTORE_ROOT] as? Boolean ?: defaults.autoRestoreRoot,
            overlay = OverlayOptions.decode(values),
            batteryCurrentMode = org.libremonitor.core.BatteryCurrentMode.entries.firstOrNull { it.name == values["battery_current_mode"] }
                ?: org.libremonitor.core.BatteryCurrentMode.AUTO,
        )
    }

    private fun matches(values: Map<String, *>, settings: MonitorSettings): Boolean =
            values["battery_current_mode"] == settings.batteryCurrentMode.name && values[HISTORY_ENABLED] == settings.historyEnabled &&
            values[SAMPLE_INTERVAL] == settings.sampleIntervalMs &&
            values[HISTORY_INTERVAL] == settings.historyIntervalMs &&
            values[RETENTION_DAYS] == settings.retentionDays &&
            values[AUTO_RESTORE_ROOT] == settings.autoRestoreRoot && OverlayOptions.decode(values) == settings.overlay &&
            values["overlay_metrics"] == settings.overlay.metrics.joinToString(",") { it.name } &&
            values["overlay_columns"] == settings.overlay.columns && values["overlay_opacity"] == settings.overlay.opacityPercent &&
            values["overlay_dock_opacity"] == settings.overlay.dockOpacityPercent &&
            values["overlay_text_size"] == settings.overlay.textSizeSp && values["overlay_edge_snap"] == settings.overlay.edgeSnap

    private fun persist(preferences: SharedPreferences, settings: MonitorSettings) {
        // commit is deliberate: the IO worker orders writes, and close can drain them.
        // A failed disk write still updates SharedPreferences' in-memory values; expose
        // the requested live setting, but do not imply durable persistence succeeded.
        val saved = preferences.edit()
            .putString("battery_current_mode", settings.batteryCurrentMode.name)
            .putBoolean(HISTORY_ENABLED, settings.historyEnabled)
            .putLong(SAMPLE_INTERVAL, settings.sampleIntervalMs)
            .putLong(HISTORY_INTERVAL, settings.historyIntervalMs)
            .putInt(RETENTION_DAYS, settings.retentionDays)
            .putBoolean(AUTO_RESTORE_ROOT, settings.autoRestoreRoot)
            .putString("overlay_metrics", settings.overlay.metrics.joinToString(",") { it.name })
            .putInt("overlay_columns", settings.overlay.columns)
            .putFloat("overlay_opacity", settings.overlay.opacityPercent)
            .putFloat("overlay_text_size", settings.overlay.textSizeSp)
            .putFloat("overlay_dock_opacity", settings.overlay.dockOpacityPercent)
            .putBoolean("overlay_edge_snap", settings.overlay.edgeSnap)
            .commit()
        if (!saved) Log.w(LOG_TAG, "Monitor preferences could not be saved to private storage")
    }

    private sealed interface Command {
        data class SetCurrentMode(val mode: org.libremonitor.core.BatteryCurrentMode) : Command
        data class SetHistory(val enabled: Boolean) : Command
        data class SetSampleInterval(val millis: Long) : Command
        data class SetAutoRestoreRoot(val enabled: Boolean) : Command
        data class SetOverlay(val options: OverlayOptions) : Command
    }

    private companion object {
        const val PREFERENCES_NAME = "monitor_preferences"
        const val LOG_TAG = "MonitorPreferences"
        const val HISTORY_ENABLED = "history_enabled"
        const val SAMPLE_INTERVAL = "sample_interval_ms"
        const val HISTORY_INTERVAL = "history_interval_ms"
        const val RETENTION_DAYS = "retention_days"
        const val AUTO_RESTORE_ROOT = "auto_restore_root"
    }
}
