/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
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
import org.libremonitor.app.MonitorUiState
import org.libremonitor.app.R
import org.libremonitor.core.PerformanceMetric
import org.libremonitor.core.PerformancePoint
import java.util.Locale
import kotlin.math.roundToInt

/** Explicit opt-in continuous monitor with a persistent, actionable notification. */
class MonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val runtime get() = (application as MonitorApplication).runtime
    private val windows by lazy { getSystemService(Context.WINDOW_SERVICE) as WindowManager }
    private var overlay: LinearLayout? = null
    private val overlayValues = linkedMapOf<PerformanceMetric, TextView>()
    private var overlayHandle: TextView? = null
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
                            ensureOverlay()
                            updateOverlay(state)
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

    private fun ensureOverlay() {
        if (overlay != null) return
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(2), dp(6), dp(10))
            background = GradientDrawable().apply {
                setColor(0xe6192028.toInt()); cornerRadius = dp(14).toFloat()
                setStroke(dp(1), 0x334f6a7d)
            }
        }
        val title = DragHandle(this).apply {
            text = "•••"
            textSize = 12f
            setTextColor(0xff74e5c2.toInt())
            gravity = Gravity.CENTER_VERTICAL
            contentDescription = "拖动悬浮窗，轻点返回应用"
            setOnClickListener { openMainActivity() }
        }
        val toolbar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        toolbar.addView(title, LinearLayout.LayoutParams(0, dp(32), 1f))
        toolbar.addView(TextView(this).apply {
            text = "×"; textSize = 20f; gravity = Gravity.CENTER
            setTextColor(0xffa1afbb.toInt())
            contentDescription = "关闭悬浮窗，性能记录继续"
            setOnClickListener { runtime.setOverlayEnabled(false) }
        }, LinearLayout.LayoutParams(dp(32), dp(32)))
        panel.addView(toolbar)
        val metrics = listOf(PerformanceMetric.CPU to "CPU", PerformanceMetric.GPU to "GPU",
            PerformanceMetric.MEMORY to "内存", PerformanceMetric.FPS to "FPS")
        metrics.chunked(2).forEach { pair ->
            val row = LinearLayout(this)
            pair.forEach { (metric, label) ->
                val cell = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, dp(2), dp(4), dp(3))
                }
                cell.addView(TextView(this).apply {
                    text = label; textSize = 10f; setTextColor(0xffa1afbb.toInt())
                })
                val value = TextView(this).apply {
                    text = "—"; textSize = 18f; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                    setTextColor(if (metric == PerformanceMetric.FPS) 0xff74e5c2.toInt() else 0xfff2f6fa.toInt())
                    setOnClickListener { openMainActivity() }
                }
                overlayValues[metric] = value
                cell.addView(value)
                row.addView(cell, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }
            panel.addView(row)
        }
        val width = minOf(dp(184), resources.displayMetrics.widthPixels - dp(24)).coerceAtLeast(dp(144))
        val parameters = WindowManager.LayoutParams(width, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.TOP or Gravity.START
                x = dp(12)
                y = dp(120)
                alpha = 1f
            }
        title.setOnTouchListener(object : View.OnTouchListener {
            var originalX = 0
            var originalY = 0
            var touchX = 0f
            var touchY = 0f
            var moved = false
            override fun onTouch(view: View, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        originalX = parameters.x; originalY = parameters.y
                        touchX = event.rawX; touchY = event.rawY
                        moved = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (kotlin.math.abs(event.rawX - touchX) > dp(6) || kotlin.math.abs(event.rawY - touchY) > dp(6)) moved = true
                        parameters.x = (originalX + event.rawX - touchX).roundToInt()
                            .coerceIn(0, (resources.displayMetrics.widthPixels - panel.width).coerceAtLeast(0))
                        parameters.y = (originalY + event.rawY - touchY).roundToInt()
                            .coerceIn(0, (resources.displayMetrics.heightPixels - panel.height).coerceAtLeast(0))
                        try { windows.updateViewLayout(panel, parameters) }
                        catch (e: Exception) { runtime.reportError("无法移动悬浮窗：${e.javaClass.simpleName}") }
                        return true
                    }
                    MotionEvent.ACTION_UP -> { if (!moved) view.performClick(); return true }
                    MotionEvent.ACTION_CANCEL -> return true
                }
                return false
            }
        })
        try {
            windows.addView(panel, parameters)
            overlay = panel
            overlayHandle = title
        } catch (e: Exception) {
            runtime.reportError("无法显示悬浮窗：${e.javaClass.simpleName}")
            runtime.setOverlayEnabled(false)
        }
    }

    private fun removeOverlay() {
        overlay?.let {
            try { windows.removeViewImmediate(it) } catch (_: IllegalArgumentException) { /* Already removed by system. */ }
        }
        overlay = null
        overlayValues.clear()
        overlayHandle = null
    }

    private fun updateOverlay(state: MonitorUiState) {
        val snapshot = state.snapshot
        val now = SystemClock.elapsedRealtime()
        val fresh = snapshot?.takeIf { it.elapsedRealtimeMillis <= now &&
            now - it.elapsedRealtimeMillis <= maxOf(15_000L, state.settings.sampleIntervalMs * 3) }
        val point = fresh?.let { PerformancePoint.from(it, 0) }
        overlayValues.forEach { (metric, view) ->
            val reading = point?.readings?.get(metric)
            val value = reading?.value?.takeIf { reading.available && metric.accepts(it) }
            val text = if (value == null) "—" else String.format(Locale.ROOT,
                if (metric == PerformanceMetric.FPS) "%.0f" else "%.0f%%", value)
            if (view.text.toString() != text) view.text = text
            view.contentDescription = "${metric.label}：$text" +
                (if (value == null) "，${reading?.reason ?: "等待有效样本"}" else " ${reading?.source.orEmpty()}")
        }
        val recording = state.activePerformance != null
        overlayHandle?.text = if (recording) "●" else "•••"
        overlayHandle?.setTextColor(if (recording) 0xffff8078.toInt() else 0xff74e5c2.toInt())
        overlayHandle?.contentDescription = if (recording) "性能记录中。拖动移动，轻点返回应用结束记录" else "拖动移动，轻点返回应用"
    }

    private fun openMainActivity() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    }

    private class DragHandle(context: Context) : TextView(context) {
        override fun performClick(): Boolean = super.performClick()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val CHANNEL = "monitor_continuous"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP = "org.libremonitor.app.STOP_MONITORING"
    }
}
