/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.service

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.SystemClock
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.libremonitor.app.MonitorUiState
import org.libremonitor.app.ui.ReadingFormat
import org.libremonitor.core.*
import java.util.Locale
import kotlin.math.roundToInt

/** Window-only presentation. Sampling and recording remain owned by MonitorRuntime. */
internal class FloatingPanel(private val context: Context, private val windows: WindowManager,
    private val open: () -> Unit, private val close: () -> Unit, private val error: (String) -> Unit) {
    private var window: View? = null
    private var parameters: WindowManager.LayoutParams? = null
    private var options = OverlayOptions()
    private var lastOptions: OverlayOptions? = null
    private val values = linkedMapOf<OverlayMetric, TextView>()
    private var grip: TextView? = null
    private var edge = DockEdge.NONE
    private var collapsed = false
    private var x = dp(12)
    private var y = dp(120)

    private data class Area(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width get() = right - left
        val height get() = bottom - top
    }
    private fun area(): Area {
        if (Build.VERSION.SDK_INT >= 30) {
            val metrics = windows.currentWindowMetrics
            val inset = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            return Area(inset.left, inset.top, metrics.bounds.width() - inset.right, metrics.bounds.height() - inset.bottom)
        }
        val display = context.resources.displayMetrics
        return Area(0, dp(24), display.widthPixels, display.heightPixels - dp(48))
    }

    fun render(state: MonitorUiState) {
        options = state.settings.overlay
        if (!options.edgeSnap && collapsed) { collapsed = false; edge = DockEdge.NONE; hide() }
        if (window != null && lastOptions != options) hide()
        if (window == null) create()
        val recording = state.activePerformance != null
        if (!collapsed) {
            grip?.text = if (recording) "●" else "•••"
            grip?.setTextColor(if (recording) 0xffff8078.toInt() else 0xff74e5c2.toInt())
            grip?.contentDescription = if (recording) "性能记录中；拖动移动，点击返回应用，长按收起" else "拖动移动，点击返回应用，长按收起"
        } else grip?.contentDescription = "${if (recording) "性能记录中。" else ""}边缘悬浮窗，点击展开，拖动改变位置"
        val now = SystemClock.elapsedRealtime()
        val snapshot = state.snapshot?.takeIf { now - it.elapsedRealtimeMillis in 0..maxOf(15_000L, state.settings.sampleIntervalMs * 3) }
        val point = snapshot?.let { PerformancePoint.from(it, 0) }
        values.forEach { (metric, view) ->
            val reading = when (metric) {
                OverlayMetric.DOWNLOAD -> snapshot?.network?.downloadBytesPerSecond
                OverlayMetric.UPLOAD -> snapshot?.network?.uploadBytesPerSecond
                else -> point?.readings?.get(metric.performance)
            }
            val number = reading?.value?.takeIf { reading.available && it.isFinite() &&
                (metric.performance?.accepts(it) ?: (it >= 0)) }
            val parts = if (number == null) "—" to "" else when (metric) {
                OverlayMetric.DOWNLOAD, OverlayMetric.UPLOAD -> ReadingFormat.byteParts(number).let { it.first to "${it.second}/s" }
                else -> String.format(Locale.ROOT, if (metric == OverlayMetric.POWER) "%.1f" else "%.0f", number) to metric.performance!!.unit
            }
            val text = parts.first + if (parts.second.isEmpty()) "" else " ${parts.second}"
            if (view.text.toString() != text) view.text = SpannableString(text).apply {
                if (parts.second.isNotEmpty()) setSpan(RelativeSizeSpan(0.6f), parts.first.length, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            view.contentDescription = "${metric.label}：$text。${reading?.reason ?: reading?.source ?: "等待有效样本"}"
        }
    }

    fun hide() {
        window?.let { runCatching { windows.removeViewImmediate(it) } }
        window = null; parameters = null; values.clear(); grip = null
    }
    fun configurationChanged() { hide() }

    private fun create() {
        val screen = area()
        val panel = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val title = Grip(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(0xff74e5c2.toInt())
            setOnClickListener { if (collapsed) expand() else open() }
            setOnLongClickListener {
                if (options.edgeSnap && !collapsed) { dock(nearestEdge()); true } else false
            }
        }
        grip = title
        val width: Int
        val height: Int
        if (collapsed) {
            width = dp(30); height = dp(56)
            title.text = if (edge == DockEdge.LEFT) "›" else "‹"
            title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 26f); title.gravity = Gravity.CENTER
            panel.background = background(true)
            panel.addView(title, LinearLayout.LayoutParams(-1, -1))
        } else {
            width = minOf(dp((options.columns * 92 * options.textSizeSp / 18) + 12), screen.width)
            val rows = (options.metrics.size + options.columns - 1) / options.columns
            val rowHeight = dp((options.textSizeSp * context.resources.configuration.fontScale * 1.4f + 22).roundToInt())
            height = minOf(dp(40) + rows * rowHeight, screen.height)
            panel.setPadding(dp(10), dp(2), dp(6), dp(8)); panel.background = background(false)
            title.text = "•••"; title.textSize = 12f
            val toolbar = LinearLayout(context)
            toolbar.addView(title, LinearLayout.LayoutParams(0, dp(32), 1f))
            toolbar.addView(TextView(context).apply {
                text = "×"; textSize = 20f; gravity = Gravity.CENTER; setTextColor(0xffa1afbb.toInt())
                contentDescription = "关闭悬浮窗；不结束正在进行的性能记录"
                setOnClickListener { close() }
            }, LinearLayout.LayoutParams(dp(32), dp(32)))
            panel.addView(toolbar)
            val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            options.metrics.chunked(options.columns).forEach { metrics ->
                val row = LinearLayout(context)
                metrics.forEach { metric ->
                    val cell = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(2), dp(4), dp(4)) }
                    cell.addView(TextView(context).apply { text = metric.label; textSize = 10f; setTextColor(0xffa1afbb.toInt()) })
                    val value = TextView(context).apply {
                        text = "—"; textSize = options.textSizeSp.toFloat(); maxLines = 1
                        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                        setTextColor(if (metric == OverlayMetric.FPS) 0xff74e5c2.toInt() else 0xfff2f6fa.toInt())
                        setOnClickListener { open() }
                    }
                    values[metric] = value; cell.addView(value)
                    row.addView(cell, LinearLayout.LayoutParams(0, rowHeight, 1f))
                }
                repeat(options.columns - metrics.size) { row.addView(View(context), LinearLayout.LayoutParams(0, rowHeight, 1f)) }
                body.addView(row)
            }
            val scroll = ScrollView(context).apply { isFillViewport = false; addView(body) }
            panel.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        x = when (edge) { DockEdge.LEFT -> screen.left; DockEdge.RIGHT -> screen.right - width; else -> x.coerceIn(screen.left, (screen.right - width).coerceAtLeast(screen.left)) }
        y = screen.top + OverlayDocking.clampY(y - screen.top, height, screen.height)
        val p = WindowManager.LayoutParams(width, height, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT).apply {
                // Drag coordinates and edge names are physical screen coordinates, including in RTL locales.
                gravity = Gravity.TOP or Gravity.getAbsoluteGravity(Gravity.START, View.LAYOUT_DIRECTION_LTR)
                this.x = this@FloatingPanel.x; this.y = this@FloatingPanel.y
            }
        parameters = p
        drag(title, panel, p)
        try { windows.addView(panel, p); window = panel; lastOptions = options }
        catch (e: Exception) { hide(); error("无法显示悬浮窗：${e.javaClass.simpleName}"); close() }
    }

    private fun background(small: Boolean) = GradientDrawable().apply {
        val alpha = (if (small) options.opacityPercent.coerceAtMost(60) else options.opacityPercent) * 255 / 100
        setColor((alpha shl 24) or 0x192028)
        if (!small) { cornerRadius = dp(14).toFloat(); setStroke(dp(1), 0x334f6a7d) }
        else {
            val r = dp(28).toFloat()
            cornerRadii = if (edge == DockEdge.LEFT) floatArrayOf(0f, 0f, r, r, r, r, 0f, 0f)
                else floatArrayOf(r, r, 0f, 0f, 0f, 0f, r, r)
        }
    }
    private fun nearestEdge(): DockEdge = if (x < area().left + area().width / 2) DockEdge.LEFT else DockEdge.RIGHT
    private fun dock(target: DockEdge) { edge = target; collapsed = true; hide(); create() }
    private fun expand() { collapsed = false; hide(); create() }

    private fun drag(handle: Grip, panel: View, p: WindowManager.LayoutParams) {
        handle.setOnTouchListener(object : View.OnTouchListener {
            var startX = 0; var startY = 0; var rawX = 0f; var rawY = 0f
            var moved = false; var longClicked = false
            val longPress = Runnable { if (!moved) longClicked = handle.performLongClick() }
            override fun onTouch(view: View, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = p.x; startY = p.y; rawX = event.rawX; rawY = event.rawY
                        moved = false; longClicked = false
                        handle.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (kotlin.math.abs(event.rawX - rawX) > dp(6) || kotlin.math.abs(event.rawY - rawY) > dp(6)) {
                            moved = true; handle.removeCallbacks(longPress)
                        }
                        if (moved && !longClicked) {
                            val a = area()
                            x = (startX + event.rawX - rawX).roundToInt().coerceIn(a.left, (a.right - p.width).coerceAtLeast(a.left))
                            y = a.top + OverlayDocking.clampY((startY + event.rawY - rawY).roundToInt() - a.top, p.height, a.height)
                            p.x = x; p.y = y
                            runCatching { windows.updateViewLayout(panel, p) }
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        handle.removeCallbacks(longPress)
                        if (!moved && !longClicked) view.performClick()
                        else if (moved && !longClicked) {
                            if (collapsed) dock(nearestEdge())
                            else {
                                val a = area()
                                edge = if (options.edgeSnap) OverlayDocking.edge(x - a.left, p.width, a.width, dp(24)) else DockEdge.NONE
                                if (edge != DockEdge.NONE) dock(edge)
                            }
                        }
                    }
                    MotionEvent.ACTION_CANCEL -> handle.removeCallbacks(longPress)
                }
                return true
            }
        })
    }
    private class Grip(context: Context) : TextView(context) { override fun performClick(): Boolean = super.performClick() }
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).roundToInt()
}
