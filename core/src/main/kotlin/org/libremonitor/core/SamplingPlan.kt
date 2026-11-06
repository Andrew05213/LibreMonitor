/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.core

/** One demand plan serves visible consumers, opt-in performance recording and battery history. */
data class SamplingPlan(
    val mode: Mode,
    val sampleIntervalMs: Long,
    val historyIntervalMs: Long,
    val recordHistory: Boolean,
    val recordPerformance: Boolean = false,
    val dashboard: Boolean = false,
    val hardwareDetails: Boolean = false,
    val collectFps: Boolean = false,
    val collectNetwork: Boolean = false,
) {
    enum class Mode { IDLE, LIVE, OVERLAY, PERFORMANCE, HISTORY }
    enum class Page { OVERVIEW, BATTERY, PERFORMANCE, SETTINGS }

    val needsSnapshot: Boolean get() = mode == Mode.LIVE || mode == Mode.OVERLAY || mode == Mode.PERFORMANCE

    /** Target start-to-start cadence; slow samples run serially without catching up missed ticks. */
    fun delayAfterSampleMillis(collectionElapsedMillis: Long): Long =
        if (needsSnapshot) (sampleIntervalMs - collectionElapsedMillis.coerceAtLeast(0L)).coerceAtLeast(0L)
        else historyIntervalMs

    companion object {
        fun forDemand(
            uiVisible: Boolean,
            serviceRunning: Boolean,
            screenInteractive: Boolean,
            overlayEnabled: Boolean,
            settings: MonitorSettings,
            performanceRecording: Boolean = false,
            page: Page = Page.OVERVIEW,
            overlayExpanded: Boolean = true,
        ): SamplingPlan {
            val recording = serviceRunning && settings.historyEnabled
            val dashboard = uiVisible && page == Page.OVERVIEW
            val battery = uiVisible && page == Page.BATTERY
            val overlay = serviceRunning && overlayEnabled && overlayExpanded
            val performance = serviceRunning && screenInteractive && performanceRecording
            return SamplingPlan(
                when {
                    screenInteractive && (dashboard || battery) -> Mode.LIVE
                    screenInteractive && serviceRunning && performanceRecording -> Mode.PERFORMANCE
                    screenInteractive && overlay -> Mode.OVERLAY
                    recording -> Mode.HISTORY
                    else -> Mode.IDLE
                },
                settings.sampleIntervalMs, settings.historyIntervalMs, recording,
                performance,
                dashboard = dashboard,
                hardwareDetails = dashboard || battery || performance || (overlay && settings.overlay.needsHardwareDetails),
                collectFps = dashboard || performance || (overlay && OverlayMetric.FPS in settings.overlay.metrics),
                collectNetwork = dashboard || (overlay && settings.overlay.needsNetwork),
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
