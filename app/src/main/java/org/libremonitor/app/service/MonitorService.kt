/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.service

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.libremonitor.app.MainActivity
import org.libremonitor.app.MonitorApplication
import org.libremonitor.app.R

/** Explicit opt-in continuous monitor with a persistent, actionable notification. */
class MonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val runtime get() = (application as MonitorApplication).runtime
    private val windows by lazy { getSystemService(Context.WINDOW_SERVICE) as WindowManager }
    private val panel by lazy { FloatingPanel(this, windows, ::openMainActivity,
        { runtime.setOverlayEnabled(false) }, runtime::reportError) }
    private var recordingNotification = false
    private var foregroundReady = false

    override fun onCreate() {
        super.onCreate()
        MonitorNotifications.ensureChannel(this)
        if (!MonitorNotifications.allowed(this)) {
            runtime.stopAll()
            runtime.reportError("通知未开启，持续监控已停止。")
            stopSelf()
            return
        }
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(),
                if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
            foregroundReady = true
            runtime.setServiceRunning(true)
        } catch (e: Exception) {
            runtime.reportError("无法启动可见监控服务：${e.javaClass.simpleName}")
            runtime.stopAll()
            stopSelf()
            return
        }
        scope.launch {
            combine(runtime.state, runtime.screenInteractive) { state, interactive -> state to interactive }
                .collect { (state, interactive) ->
                    val recording = state.activePerformance != null
                    if (recordingNotification != recording && MonitorNotifications.allowed(this@MonitorService)) {
                        recordingNotification = recording
                        if (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this@MonitorService,
                                Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification())
                        }
                    }
                    if (!MonitorNotifications.allowed(this@MonitorService)) {
                        runtime.stopAll()
                        runtime.reportError("通知或监控通知渠道已关闭，持续监控已停止。")
                        stopSelf()
                    } else if (!state.settings.historyEnabled && !state.overlayEnabled && state.activePerformance == null && !state.performanceBusy) {
                        stopSelf()
                    } else if (state.overlayEnabled) {
                        if (!Settings.canDrawOverlays(this@MonitorService)) {
                            removeOverlay()
                            runtime.reportError("悬浮窗权限尚未授予或已被撤销。")
                            runtime.setOverlayEnabled(false)
                        } else if (interactive) {
                            panel.render(state)
                        } else removeOverlay()
                    } else {
                        removeOverlay()
                    }
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            runtime.stopAll()
            stopSelf()
        }
        // Do not silently resurrect recording after a process kill or device reboot.
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        removeOverlay()
        runtime.setServiceRunning(false)
        if (foregroundReady) ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, MonitorService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_monitor)
            .setContentTitle(if (runtime.state.value.activePerformance != null) "LibreMonitor · 性能记录中" else "LibreMonitor 正在监控")
            .setContentText("点此返回应用查看；停止全部将结束并保存性能记录")
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, "停止全部", stop)
            .build()
    }

    private fun removeOverlay() { panel.hide() }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        panel.configurationChanged()
        if (runtime.state.value.overlayEnabled && runtime.screenInteractive.value) panel.render(runtime.state.value)
    }

    private fun openMainActivity() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    }

    companion object {
        private const val CHANNEL = "monitor_continuous"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP = "org.libremonitor.app.STOP_MONITORING"
    }
}
