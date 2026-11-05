/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.libremonitor.app.ui.LibreMonitorScreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val runtime get() = (application as MonitorApplication).runtime
    private enum class PendingAction { NONE, HISTORY, OVERLAY, PERFORMANCE }
    private var pendingNotificationAction = PendingAction.NONE
    private var pendingOverlay = false
    private var pendingPerformanceName = ""

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = pendingNotificationAction
        pendingNotificationAction = PendingAction.NONE
        if (granted) perform(action)
        else runtime.reportError("未授予通知权限，持续记录或悬浮窗未开启。实时概览仍可使用。")
    }
    private val overlayPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (pendingOverlay) {
            pendingOverlay = false
            if (Settings.canDrawOverlays(this)) withNotificationPermission(PendingAction.OVERLAY)
            else runtime.reportError("未授予悬浮窗权限；不会开启悬浮窗。")
        }
    }
    private val createCsv = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) runtime.exportCsvTo(uri)
    }
    private val bluetoothPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        runtime.refreshBluetoothPermission()
        if (!granted) runtime.reportError("未授予蓝牙权限，无法读取适配器和已配对设备。")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingNotificationAction = PendingAction.entries.firstOrNull {
            it.name == savedInstanceState?.getString("pending_notification_action")
        } ?: PendingAction.NONE
        pendingOverlay = savedInstanceState?.getBoolean("pending_overlay", false) ?: false
        pendingPerformanceName = savedInstanceState?.getString("pending_performance_name").orEmpty()
        enableEdgeToEdge()
        setContent {
            val state by runtime.state.collectAsStateWithLifecycle()
            LibreMonitorScreen(state, MonitorActions(
                onHistoryEnabledChange = { enabled ->
                    if (enabled) withNotificationPermission(PendingAction.HISTORY)
                    else runtime.setHistoryEnabled(false)
                },
                onSampleIntervalChange = runtime::setSampleInterval,
                onOverlayToggle = ::toggleOverlay,
                onExport = {
                    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
                    createCsv.launch("LibreMonitor-battery-$stamp.csv")
                },
                onClearHistory = runtime::clearHistory,
                onDismissError = runtime::dismissError,
                onRootRequest = runtime::requestRoot,
                onRootDisable = runtime::disableRoot,
                onRootGrantPermissions = runtime::grantRootPermissions,
                onBluetoothPermissionRequest = {
                    if (Build.VERSION.SDK_INT >= 31 &&
                        ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                    ) {
                        bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
                    } else {
                        runtime.refreshBluetoothPermission()
                    }
                },
                onAutoRestoreRootChange = runtime::setAutoRestoreRoot,
                onPerformanceStart = { name ->
                    pendingPerformanceName = name
                    withNotificationPermission(PendingAction.PERFORMANCE)
                },
                onPerformanceStop = { runtime.stopPerformanceRecording() },
                onPerformanceReview = runtime::reviewPerformance,
            ))
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("pending_notification_action", pendingNotificationAction.name)
        outState.putBoolean("pending_overlay", pendingOverlay)
        outState.putString("pending_performance_name", pendingPerformanceName)
        super.onSaveInstanceState(outState)
    }
    override fun onStart() {
        super.onStart()
        runtime.setUiVisible(this, true)
        runtime.refreshBluetoothPermission()
    }
    override fun onStop() { runtime.setUiVisible(this, false); super.onStop() }
    override fun onDestroy() { runtime.setUiVisible(this, false); super.onDestroy() }

    private fun perform(action: PendingAction) {
        when (action) {
            PendingAction.HISTORY -> runtime.setHistoryEnabled(true)
            PendingAction.OVERLAY -> runtime.setOverlayEnabled(true)
            PendingAction.PERFORMANCE -> runtime.startPerformanceRecording(pendingPerformanceName)
            PendingAction.NONE -> Unit
        }
    }

    private fun withNotificationPermission(action: PendingAction) {
        if (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            perform(action)
        } else {
            pendingNotificationAction = action
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun toggleOverlay() {
        if (runtime.state.value.overlayEnabled) {
            runtime.setOverlayEnabled(false)
            return
        }
        if (Settings.canDrawOverlays(this)) {
            withNotificationPermission(PendingAction.OVERLAY)
        } else {
            pendingOverlay = true
            try {
                overlayPermission.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } catch (e: Exception) {
                pendingOverlay = false
                runtime.reportError("无法打开悬浮窗授权页：${e.javaClass.simpleName}")
            }
        }
    }
}
