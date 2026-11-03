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
        enqueue(Command.SetSampleInterval(value.coerceIn(1_000L, 10_000L)))
    }

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
                .coerceIn(1_000L, 10_000L),
            historyIntervalMs = (values[HISTORY_INTERVAL] as? Long ?: defaults.historyIntervalMs)
                .coerceIn(10_000L, 3_600_000L),
            retentionDays = (values[RETENTION_DAYS] as? Int ?: defaults.retentionDays)
                .coerceIn(1, 30),
        )
    }

    private fun matches(values: Map<String, *>, settings: MonitorSettings): Boolean =
        values[HISTORY_ENABLED] == settings.historyEnabled &&
            values[SAMPLE_INTERVAL] == settings.sampleIntervalMs &&
            values[HISTORY_INTERVAL] == settings.historyIntervalMs &&
            values[RETENTION_DAYS] == settings.retentionDays

    private fun persist(preferences: SharedPreferences, settings: MonitorSettings) {
        // commit is deliberate: the IO worker orders writes, and close can drain them.
        // A failed disk write still updates SharedPreferences' in-memory values; expose
        // the requested live setting, but do not imply durable persistence succeeded.
        val saved = preferences.edit()
            .putBoolean(HISTORY_ENABLED, settings.historyEnabled)
            .putLong(SAMPLE_INTERVAL, settings.sampleIntervalMs)
            .putLong(HISTORY_INTERVAL, settings.historyIntervalMs)
            .putInt(RETENTION_DAYS, settings.retentionDays)
            .commit()
        if (!saved) Log.w(LOG_TAG, "Monitor preferences could not be saved to private storage")
    }

    private sealed interface Command {
        data class SetHistory(val enabled: Boolean) : Command
        data class SetSampleInterval(val millis: Long) : Command
    }

    private companion object {
        const val PREFERENCES_NAME = "monitor_preferences"
        const val LOG_TAG = "MonitorPreferences"
        const val HISTORY_ENABLED = "history_enabled"
        const val SAMPLE_INTERVAL = "sample_interval_ms"
        const val HISTORY_INTERVAL = "history_interval_ms"
        const val RETENTION_DAYS = "retention_days"
    }
}
