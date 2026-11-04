/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app

import org.libremonitor.core.BatteryRecord
import org.libremonitor.core.DeviceInfo
import org.libremonitor.core.MonitorSettings
import org.libremonitor.core.MonitorSnapshot
import org.libremonitor.core.RootState

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
)
