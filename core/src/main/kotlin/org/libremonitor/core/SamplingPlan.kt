/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.core

/** Only visible consumers need the full hardware/Root snapshot. */
data class SamplingPlan(
    val mode: Mode,
    val sampleIntervalMs: Long,
    val historyIntervalMs: Long,
    val recordHistory: Boolean,
) {
    enum class Mode { IDLE, LIVE, OVERLAY, HISTORY }

    val needsSnapshot: Boolean get() = mode == Mode.LIVE || mode == Mode.OVERLAY

    companion object {
        fun forDemand(
            uiVisible: Boolean,
            serviceRunning: Boolean,
            screenInteractive: Boolean,
            overlayEnabled: Boolean,
            settings: MonitorSettings,
        ): SamplingPlan {
            val recording = serviceRunning && settings.historyEnabled
            return SamplingPlan(
                when {
                    screenInteractive && uiVisible -> Mode.LIVE
                    screenInteractive && serviceRunning && overlayEnabled -> Mode.OVERLAY
                    recording -> Mode.HISTORY
                    else -> Mode.IDLE
                },
                settings.sampleIntervalMs, settings.historyIntervalMs, recording,
            )
        }
    }
}

/** Monotonic ledger survives consumer/interval changes and advances only after a successful insert. */
class HistoryCadence {
    private var lastRecordedElapsed: Long? = null

    fun remainingMillis(now: Long, intervalMs: Long): Long {
        require(intervalMs > 0L)
        val last = lastRecordedElapsed ?: return 0L
        if (now < last) return 0L
        return (intervalMs - (now - last)).coerceAtLeast(0L)
    }

    fun recorded(elapsedMillis: Long) { lastRecordedElapsed = elapsedMillis }
    fun reset() { lastRecordedElapsed = null }
}
