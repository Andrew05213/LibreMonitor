/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.libremonitor.app.data.AndroidSampler
import org.libremonitor.app.data.BatteryHistoryStore
import org.libremonitor.app.data.MonitorPreferences
import org.libremonitor.app.data.privileged.OneShotRootReader
import org.libremonitor.core.RootAccess
import org.libremonitor.app.service.MonitorNotifications
import org.libremonitor.app.service.MonitorService
import org.libremonitor.core.BatteryRecord
import java.io.OutputStream

/** Process-owned single sampling engine. No boot receiver or background auto-start. */
class MonitorRuntime(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val rootReader = OneShotRootReader()
    private val sampler = AndroidSampler(context, rootReader)
    private var rootRequestGeneration = 0L
    private var rootRequestInFlight = false
    private val history = BatteryHistoryStore(context)
    private val preferences = MonitorPreferences(context)
    private val visible = MutableStateFlow(false)
    private val serviceActive = MutableStateFlow(false)
    private val visibleOwners = mutableSetOf<Any>() // Accessed only on Main.
    private val exportMutex = Mutex()
    private val mutableState = MutableStateFlow(MonitorUiState())
    val state: StateFlow<MonitorUiState> = mutableState.asStateFlow()
    private var lastHistoryElapsed: Long? = null
    private var stopRequested = false
    private var pendingHistoryIntent: Boolean? = null
    private var historyGeneration = 0L

    init {
        scope.launch {
            rootReader.state.collect { root -> mutableState.update { it.copy(rootState = root) } }
        }
        scope.launch {
            try { mutableState.update { it.copy(device = sampler.deviceInfo()) } }
            catch (e: Exception) { reportError("设备信息暂不可用：${e.javaClass.simpleName}") }
            refreshHistory()
        }
        scope.launch {
            combine(preferences.settings, preferences.loaded) { settings, loaded -> settings to loaded }
                .collect { (settings, loaded) ->
                    if (loaded) {
                        val pending = pendingHistoryIntent
                        val effective = if (pending == null) settings else settings.copy(historyEnabled = pending)
                        // Keep this process's explicit user intent; equal values are not queue acknowledgements.
                        mutableState.update { it.copy(settings = effective) }
                        reconcileService()
                    }
                }
        }
        scope.launch {
            // Interval changes do not cancel a committed record or reset the cadence ledger.
            combine(visible, serviceActive) { shown, service -> shown || service }
                .distinctUntilChanged().collectLatest { active ->
                    if (!active) return@collectLatest
                    while (currentCoroutineContext().isActive) {
                        try {
                            val snapshot = sampler.sample()
                            mutableState.update { it.copy(snapshot = snapshot) }
                            val settings = state.value.settings
                            val elapsed = snapshot.elapsedRealtimeMillis
                            val due = lastHistoryElapsed?.let { elapsed < it || elapsed - it >= settings.historyIntervalMs } ?: true
                            if (settings.historyEnabled && serviceActive.value && !stopRequested && due) {
                                // A record already accepted may finish after Stop; no subsequent record is accepted.
                                // Keep commit + cadence bookkeeping together even when UI demand disappears.
                                withContext(NonCancellable) {
                                    history.insert(BatteryRecord.from(snapshot))
                                    lastHistoryElapsed = elapsed
                                    refreshHistory()
                                }
                            }
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) {
                            reportError("采集或记录失败：${e.javaClass.simpleName}；请检查可用空间和系统限制。")
                        }
                        delay(state.value.settings.sampleIntervalMs)
                    }
                }
        }
    }

    fun setUiVisible(owner: Any, value: Boolean) {
        if (value) visibleOwners.add(owner) else visibleOwners.remove(owner)
        visible.value = visibleOwners.isNotEmpty()
        if (value) reconcileService()
    }

    fun setHistoryEnabled(value: Boolean) {
        pendingHistoryIntent = value
        if (value) { stopRequested = false; lastHistoryElapsed = null }
        mutableState.update { it.copy(settings = it.settings.copy(historyEnabled = value)) }
        try { preferences.setHistoryEnabled(value) }
        catch (e: Exception) { reportError("无法保存记录设置：${e.javaClass.simpleName}") }
        reconcileService()
    }

    fun setSampleInterval(value: Long) {
        try { preferences.setSampleIntervalMs(value) }
        catch (e: Exception) { reportError("无法保存刷新间隔：${e.javaClass.simpleName}") }
    }

    fun setOverlayEnabled(value: Boolean) {
        if (value) stopRequested = false
        mutableState.update { it.copy(overlayEnabled = value) }
        reconcileService()
    }

    fun setServiceRunning(value: Boolean) {
        serviceActive.value = value
        mutableState.update { it.copy(serviceRunning = value) }
    }

    /** Called only from the foreground UI's explicit Root button. */
    fun requestRoot() {
        if (!visible.value) {
            reportError("请在应用前台主动请求 Root 授权。")
            return
        }
        if (rootRequestInFlight || rootReader.state.value.access == RootAccess.REQUESTING) {
            reportError("Root 授权请求正在进行，请稍候。")
            return
        }
        if (rootReader.state.value.access == RootAccess.GRANTED) return
        val generation = ++rootRequestGeneration
        rootRequestInFlight = true
        scope.launch {
            try {
                val granted = rootReader.requestAuthorization()
                if (generation == rootRequestGeneration && !granted) {
                    val detail = rootReader.state.value.message.ifBlank { "授权被拒绝或不可用" }
                    reportError("Root 请求失败：$detail；基本采集仍可使用。")
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (generation == rootRequestGeneration) reportError("Root 请求失败：${e.javaClass.simpleName}；基本采集仍可使用。")
            } finally {
                if (generation == rootRequestGeneration) rootRequestInFlight = false
            }
        }
    }

    fun disableRoot() {
        rootRequestGeneration++
        rootRequestInFlight = false
        rootReader.close()
        mutableState.update { it.copy(rootState = rootReader.state.value) }
    }

    /** Explicit user action: grant only this app's notification, overlay and battery-whitelist entries. */
    fun grantRootPermissions() {
        if (!visible.value) {
            reportError("请在应用前台确认一键授权。")
            return
        }
        if (rootReader.state.value.access != RootAccess.GRANTED) {
            reportError("请先启用 Root 只读会话，再一键授权。")
            return
        }
        if (state.value.permissionGrantRunning) return
        mutableState.update { it.copy(permissionGrantRunning = true, permissionGrantMessage = null) }
        scope.launch {
            try {
                val result = rootReader.grantPermissions()
                mutableState.update {
                    it.copy(permissionGrantRunning = false, permissionGrantMessage = result.message)
                }
                if (!result.anyGranted) reportError("一键授权未成功：${result.message}")
                else reconcileService()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableState.update { it.copy(permissionGrantRunning = false) }
                reportError("一键授权失败：${e.javaClass.simpleName}")
            }
        }
    }

    fun stopAll() {
        disableRoot()
        stopRequested = true
        pendingHistoryIntent = false
        mutableState.update { it.copy(overlayEnabled = false, settings = it.settings.copy(historyEnabled = false)) }
        try { preferences.setHistoryEnabled(false) }
        catch (_: Exception) { reportError("已停止监控，但无法保存关闭设置。") }
        context.stopService(Intent(context, MonitorService::class.java))
    }

    fun reportError(message: String) { mutableState.update { it.copy(error = message) } }
    fun dismissError() { mutableState.update { it.copy(error = null) } }

    fun clearHistory() {
        historyGeneration++
        scope.launch {
            try {
                history.clear()
                historyGeneration++
                mutableState.update { it.copy(history = emptyList()) }
                refreshHistory()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { reportError("无法清除历史：${e.javaClass.simpleName}") }
        }
    }

    suspend fun exportCsv(output: OutputStream) { history.exportCsv(output) }

    fun exportCsvTo(uri: Uri) {
        scope.launch {
            try {
                // Lock before opening/truncating a provider stream, not just around DB access.
                exportMutex.withLock {
                    withContext(Dispatchers.IO) {
                        val output = context.contentResolver.openOutputStream(uri, "wt")
                            ?: error("Document provider returned no stream")
                        output.use { history.exportCsv(it) }
                    }
                }
                Toast.makeText(context, "电池历史已导出为 CSV", Toast.LENGTH_SHORT).show()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { reportError("导出失败：${e.javaClass.simpleName}；目标文件可能不完整，请重新导出。") }
        }
    }

    private suspend fun refreshHistory() {
        val generation = historyGeneration
        try {
            val records = history.latest()
            if (generation == historyGeneration) mutableState.update { it.copy(history = records) }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { reportError("无法读取历史：${e.javaClass.simpleName}") }
    }

    private fun reconcileService() {
        val current = state.value
        val wanted = !stopRequested && (current.settings.historyEnabled || current.overlayEnabled)
        val intent = Intent(context, MonitorService::class.java)
        if (!wanted) {
            if (current.serviceRunning) context.stopService(intent)
            return
        }
        // Central permission gate also covers persisted settings restored after a cold start.
        MonitorNotifications.ensureChannel(context)
        if (!MonitorNotifications.allowed(context)) {
            stopAll()
            reportError("通知或监控通知渠道未开启，持续监控已关闭；请在系统设置允许通知后重试。")
            return
        }
        if (!visible.value && !current.serviceRunning) return
        try { ContextCompat.startForegroundService(context, intent) }
        catch (e: Exception) {
            stopAll()
            reportError("系统未允许持续监控：${e.javaClass.simpleName}。请在应用前台重新开启。")
        }
    }
}
