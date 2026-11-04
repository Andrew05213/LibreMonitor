/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.libremonitor.app.MainActivity
import org.libremonitor.app.MonitorApplication
import org.libremonitor.app.MonitorUiState
import org.libremonitor.app.R
import java.util.Locale
import kotlin.math.roundToInt

/** Explicit opt-in continuous monitor with a persistent, actionable notification. */
class MonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val runtime get() = (application as MonitorApplication).runtime
    private val windows by lazy { getSystemService(Context.WINDOW_SERVICE) as WindowManager }
    private var overlay: LinearLayout? = null
    private var overlayText: TextView? = null
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
            runtime.state.collect { state ->
                if (!MonitorNotifications.allowed(this@MonitorService)) {
                    runtime.stopAll()
                    runtime.reportError("通知或监控通知渠道已关闭，持续监控已停止。")
                    stopSelf()
                } else if (!state.settings.historyEnabled && !state.overlayEnabled) {
                    stopSelf()
                } else if (state.overlayEnabled) {
                    if (!Settings.canDrawOverlays(this@MonitorService)) {
                        removeOverlay()
                        runtime.reportError("悬浮窗权限尚未授予或已被撤销。")
                        runtime.setOverlayEnabled(false)
                    } else {
                        ensureOverlay()
                        overlayText?.text = formatOverlay(state)
                    }
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
            .setContentTitle("LibreMonitor 正在监控")
            .setContentText("本地电池记录或悬浮窗已开启；可随时停止全部监控")
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
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = GradientDrawable().apply { setColor(0xff17212b.toInt()); cornerRadius = dp(14).toFloat() }
        }
        val title = DragHandle(this).apply {
            setText(R.string.overlay_drag_handle)
            textSize = 12f
            setTextColor(0xff74e5c2.toInt())
            setPadding(0, dp(8), 0, dp(10))
            contentDescription = getString(R.string.overlay_handle_description)
            setOnClickListener { openMainActivity() }
        }
        val body = TextView(this).apply {
            textSize = 12f
            setTextColor(0xfff2f6fa.toInt())
            typeface = Typeface.MONOSPACE
        }
        val controls = LinearLayout(this)
        controls.addView(Button(this).apply {
            setText(R.string.overlay_open)
            textSize = 12f
            setOnClickListener { openMainActivity() }
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        controls.addView(Button(this).apply {
            setText(R.string.overlay_stop)
            textSize = 12f
            contentDescription = "停止电池记录并关闭悬浮窗"
            setOnClickListener { runtime.stopAll(); stopSelf() }
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        panel.addView(title)
        panel.addView(body)
        panel.addView(controls)
        val width = minOf(dp(245), resources.displayMetrics.widthPixels - dp(24)).coerceAtLeast(dp(160))
        val parameters = WindowManager.LayoutParams(width, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.TOP or Gravity.START
                x = dp(12)
                y = dp(120)
                alpha = 0.8f
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
            overlayText = body
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
        overlayText = null
    }

    private fun formatOverlay(state: MonitorUiState): String {
        val snapshot = state.snapshot ?: return "正在读取真实设备数据…"
        fun number(value: Double?, suffix: String) = value?.takeIf { it.isFinite() }?.let {
            String.format(Locale.ROOT, "%.1f%s", it, suffix)
        } ?: "不可用"
        val memory = snapshot.memory.let { m ->
            val total = m.totalBytes.value
            val available = m.availableBytes.value
            if (total != null && available != null && total > 0) number((total - available).toDouble() / total * 100, "%") else "不可用"
        }
        val fpsText = if (snapshot.fps.fps.available) {
            val fps = snapshot.fps.fps.value?.let { String.format(Locale.ROOT, "%.1f", it) } ?: "—"
            val low = snapshot.fps.onePercentLow.value?.let { String.format(Locale.ROOT, "%.1f", it) } ?: "—"
            "\nFPS $fps  1% low $low"
        } else {
            "\nFPS — ${snapshot.fps.fps.reason.orEmpty().take(48)}"
        }
        return "CPU  ${number(snapshot.cpu.usagePercent.value, "%")}\n" +
            "内存 $memory\n" +
            "电量 ${snapshot.battery.levelPercent.value?.let { "$it%" } ?: "不可用"}  电池 ${number(snapshot.battery.temperatureC.value, "°C")}\n" +
            "电池端 ${number(snapshot.battery.powerW.value, " W")}（估算）" + fpsText
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
