/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app

import org.libremonitor.core.BatteryRecord
import org.libremonitor.core.DeviceInfo
import org.libremonitor.core.MonitorSettings
import org.libremonitor.core.MonitorSnapshot
import org.libremonitor.core.RootState
import org.libremonitor.core.PerformanceSession
import org.libremonitor.core.PerformanceReview

data class MonitorUiState(
    val snapshot: MonitorSnapshot? = null,
    val device: DeviceInfo? = null,
    val history: List<BatteryRecord> = emptyList(),
    val settings: MonitorSettings = MonitorSettings(),
    val overlayEnabled: Boolean = false,
    val serviceRunning: Boolean = false,
    val error: String? = null,
    val rootState: RootState = RootState(),
    val permissionGrantRunning: Boolean = false,
    val permissionGrantMessage: String? = null,
    val bluetoothPermissionGranted: Boolean = false,
    val performanceReady: Boolean = false,
    val performanceBusy: Boolean = false,
    val activePerformance: PerformanceSession? = null,
    val performanceSessions: List<PerformanceSession> = emptyList(),
    val performanceReview: PerformanceReview? = null,
    val performanceReviewLoading: Boolean = false,
    val earbudObservations: List<org.libremonitor.core.EarbudObservation> = emptyList(),
    val bluetoothScanning: Boolean = false,
    val bluetoothScanMessage: String = "手动扫描附近耳机广播；不维持后台扫描。",
)

data class MonitorActions(
    val onHistoryEnabledChange: (Boolean) -> Unit,
    val onSampleIntervalChange: (Long) -> Unit,
    val onOverlayToggle: () -> Unit,
    val onExport: () -> Unit,
    val onClearHistory: () -> Unit,
    val onDismissError: () -> Unit,
    val onRootRequest: () -> Unit = {},
    val onRootDisable: () -> Unit = {},
    val onRootGrantPermissions: () -> Unit = {},
    val onBluetoothPermissionRequest: () -> Unit = {},
    val onAutoRestoreRootChange: (Boolean) -> Unit = {},
    val onPerformanceStart: (String) -> Unit = {},
    val onPerformanceStop: () -> Unit = {},
    val onPerformanceReview: (Long) -> Unit = {},
    val onOverlayOptionsChange: (org.libremonitor.core.OverlayOptions) -> Unit = {},
    val onEarbudScan: () -> Unit = {},
    val onEarbudScanStop: () -> Unit = {},
    val onPhonePermissionRequest: () -> Unit = {},
)
