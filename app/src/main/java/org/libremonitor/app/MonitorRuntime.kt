/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app

import android.Manifest
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.widget.Toast
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
import org.libremonitor.core.HistoryCadence
import org.libremonitor.core.SamplingPlan
import org.libremonitor.core.PerformancePoint
import org.libremonitor.core.PerformanceSession
import org.libremonitor.core.MonitorSnapshot
import org.libremonitor.app.data.PerformanceStore
import java.io.OutputStream

/** Process-owned single sampling engine. No boot receiver or background auto-start. */
class MonitorRuntime(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val rootReader = OneShotRootReader()
    private val sampler = AndroidSampler(context, rootReader)
    private var rootRequestGeneration = 0L
    private var rootRequestInFlight = false
    private var autoRootAttempted = false
    private var rootManuallyDisabled = false
    private val history = BatteryHistoryStore(context)
    private val performanceStore = PerformanceStore(context)
    private var performanceStartedElapsed = 0L
    private var performanceGeneration = 0L
    private var reviewJob: Job? = null
    private val preferences = MonitorPreferences(context)
    private val visible = MutableStateFlow(false)
    private val serviceActive = MutableStateFlow(false)
    private val interactive = MutableStateFlow(isScreenInteractive())
    val screenInteractive: StateFlow<Boolean> = interactive.asStateFlow()
    private val visibleOwners = mutableSetOf<Any>() // Accessed only on Main.
    private val exportMutex = Mutex()
    private val mutableState = MutableStateFlow(MonitorUiState())
    private val earbudScanner = org.libremonitor.app.data.EarbudScanner(context) { observations, running, message ->
        mutableState.update { it.copy(earbudObservations = observations, bluetoothScanning = running, bluetoothScanMessage = message) }
    }
    val state: StateFlow<MonitorUiState> = mutableState.asStateFlow()
    private val historyCadence = HistoryCadence()
    private var stopRequested = false
    private var pendingHistoryIntent: Boolean? = null
    private var overlayIntent: org.libremonitor.core.OverlayOptions? = null
    private var historyGeneration = 0L

    init {
        scope.launch {
            try {
                performanceStore.initialize()
                refreshPerformanceSessions()
                mutableState.update { it.copy(performanceReady = true) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { reportError("无法读取性能记录：${e.javaClass.simpleName}") }
        }
        refreshBluetoothPermission()
        scope.launch {
            rootReader.state.collect { root -> mutableState.update { it.copy(rootState = root) } }
        }
        scope.launch {
            try {
                val device = withContext(Dispatchers.IO) { sampler.deviceInfo() }
                mutableState.update { it.copy(device = device) }
            }
            catch (e: Exception) { reportError("设备信息暂不可用：${e.javaClass.simpleName}") }
        }
        scope.launch {
            combine(preferences.settings, preferences.loaded) { settings, loaded -> settings to loaded }
                .collect { (settings, loaded) ->
                    if (loaded) {
                        val pending = pendingHistoryIntent
                        val effective = (if (pending == null) settings else settings.copy(historyEnabled = pending))
                            .copy(overlay = overlayIntent ?: settings.overlay)
                        // Keep this process's explicit user intent; equal values are not queue acknowledgements.
                        mutableState.update { it.copy(settings = effective) }
                        reconcileService()
                        maybeAutoRestoreRoot()
                    }
                }
        }
        scope.launch {
            // Listen only while there is a consumer. No screen polling, alarms or wake locks.
            combine(visible, serviceActive) { shown, service -> shown || service }
                .distinctUntilChanged().collectLatest { active ->
                    if (!active) return@collectLatest
                    val receiver = object : BroadcastReceiver() {
                        override fun onReceive(context: Context?, intent: Intent?) {
                            interactive.value = isScreenInteractive()
                        }
                    }
                    ContextCompat.registerReceiver(context, receiver, IntentFilter().apply {
                        addAction(Intent.ACTION_SCREEN_ON)
                        addAction(Intent.ACTION_SCREEN_OFF)
                    }, ContextCompat.RECEIVER_NOT_EXPORTED)
                    try {
                        interactive.value = isScreenInteractive()
                        awaitCancellation()
                    } finally { context.unregisterReceiver(receiver) }
                }
        }
        scope.launch {
            // Project only demand fields so snapshots/history/error updates never restart the loop.
            val options = state.map { Triple(it.settings, it.overlayEnabled, it.activePerformance != null) }.distinctUntilChanged()
            combine(visible, serviceActive, interactive, options) { shown, service, screen, (settings, overlay, performance) ->
                SamplingPlan.forDemand(shown, service, screen, overlay, settings, performance)
            }.distinctUntilChanged().collectLatest { plan ->
                if (plan.mode == SamplingPlan.Mode.IDLE) return@collectLatest
                if (plan.needsSnapshot) sampler.resetRateBaselines()
                while (currentCoroutineContext().isActive) {
                    val sampleStarted = SystemClock.elapsedRealtime()
                    // Failed samples retain a full retry delay even if collection was slow.
                    var nextDelayMs = if (plan.needsSnapshot) plan.sampleIntervalMs else plan.historyIntervalMs
                    try {
                        // Reuse the ledger across visibility/screen/interval changes.
                        if (plan.mode == SamplingPlan.Mode.HISTORY) {
                            delay(historyCadence.remainingMillis(SystemClock.elapsedRealtime(), plan.historyIntervalMs))
                        }
                        // Battery-only samples do not emit UI snapshots, so keep this gate here too.
                        if (serviceActive.value && !MonitorNotifications.allowed(context)) {
                            stopAll()
                            reportError("通知或监控通知渠道已关闭，持续监控已停止。")
                            return@collectLatest
                        }
                        val record: BatteryRecord
                        val elapsed: Long
                        if (plan.needsSnapshot) {
                            val session = state.value.activePerformance?.takeIf { plan.recordPerformance }
                            val started = performanceStartedElapsed
                            val overlayOptions = state.value.settings.overlay
                            val collected = sampler.sample(includeDetails = plan.mode == SamplingPlan.Mode.LIVE,
                                includePerformanceDetails = plan.recordPerformance ||
                                    (state.value.overlayEnabled && overlayOptions.needsHardwareDetails),
                                includeNetwork = state.value.overlayEnabled && overlayOptions.needsNetwork)
                            val snapshot = collected.copy(fps = collected.fps.validatedFor(rootReader.state.value))
                            // Main-thread publication and disableRoot cannot interleave here.
                            mutableState.update { it.copy(snapshot = snapshot) }
                            if (session != null && state.value.activePerformance?.id == session.id && snapshot.elapsedRealtimeMillis >= started) {
                                savePerformancePoint(session, started, snapshot)
                            }
                            record = BatteryRecord.from(snapshot)
                            elapsed = snapshot.elapsedRealtimeMillis
                        } else {
                            record = sampler.sampleBatteryRecord()
                            elapsed = SystemClock.elapsedRealtime()
                        }
                        val settings = state.value.settings
                        val due = historyCadence.remainingMillis(elapsed, settings.historyIntervalMs) == 0L
                        if (settings.historyEnabled && serviceActive.value && !stopRequested && due) {
                            // A record already accepted may finish after Stop; no subsequent record is accepted.
                            // Keep commit + cadence bookkeeping together even when UI demand disappears.
                            withContext(NonCancellable) {
                                history.insert(record)
                                historyCadence.recorded(elapsed)
                                if (visible.value && interactive.value) refreshHistory()
                            }
                        }
                        nextDelayMs = plan.delayAfterSampleMillis(SystemClock.elapsedRealtime() - sampleStarted)
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) {
                        reportError("采集或记录失败：${e.javaClass.simpleName}；请检查可用空间和系统限制。")
                    }
                    // Error retries also back off; failed inserts never advance the ledger.
                    delay(nextDelayMs)
                }
            }
        }
    }

    private fun isScreenInteractive(): Boolean =
        context.getSystemService(PowerManager::class.java)?.isInteractive ?: true

    fun refreshBluetoothPermission() {
        val granted = Build.VERSION.SDK_INT < 31 || ContextCompat.checkSelfPermission(
            context, Manifest.permission.BLUETOOTH_CONNECT,
        ) == PackageManager.PERMISSION_GRANTED
        mutableState.update { it.copy(bluetoothPermissionGranted = granted) }
    }

    fun setUiVisible(owner: Any, value: Boolean) {
        val wasVisible = visible.value
        if (value) visibleOwners.add(owner) else visibleOwners.remove(owner)
        visible.value = visibleOwners.isNotEmpty()
        if (!visible.value && state.value.bluetoothScanning) earbudScanner.stop("离开应用前台，扫描已停止。")
        if (value) {
            interactive.value = isScreenInteractive()
            if (!wasVisible) scope.launch { refreshHistory(); refreshPerformanceSessions() }
            reconcileService()
            maybeAutoRestoreRoot()
        }
    }

    fun setAutoRestoreRoot(value: Boolean) {
        mutableState.update { it.copy(settings = it.settings.copy(autoRestoreRoot = value)) }
        try { preferences.setAutoRestoreRoot(value) }
        catch (e: Exception) { reportError("无法保存自动恢复设置：${e.javaClass.simpleName}") }
        if (value) {
            autoRootAttempted = false
            rootManuallyDisabled = false
            maybeAutoRestoreRoot()
        }
    }

    private fun maybeAutoRestoreRoot() {
        if (autoRootAttempted || rootManuallyDisabled || !visible.value) return
        if (!state.value.settings.autoRestoreRoot) return
        if (rootReader.state.value.access == RootAccess.GRANTED) return
        autoRootAttempted = true
        requestRootInternal(auto = true)
    }

    fun setHistoryEnabled(value: Boolean) {
        pendingHistoryIntent = value
        if (value) { stopRequested = false; historyCadence.reset() }
        mutableState.update { it.copy(settings = it.settings.copy(historyEnabled = value)) }
        try { preferences.setHistoryEnabled(value) }
        catch (e: Exception) { reportError("无法保存记录设置：${e.javaClass.simpleName}") }
        reconcileService()
    }

    fun setSampleInterval(value: Long) {
        try { preferences.setSampleIntervalMs(value) }
        catch (e: Exception) { reportError("无法保存刷新间隔：${e.javaClass.simpleName}") }
    }

    fun setOverlayOptions(options: org.libremonitor.core.OverlayOptions) {
        overlayIntent = options
        mutableState.update { it.copy(settings = it.settings.copy(overlay = options)) }
        try { preferences.setOverlayOptions(options) }
        catch (e: Exception) { reportError("无法保存悬浮窗设置：${e.javaClass.simpleName}") }
    }
    fun startEarbudScan() { if (visible.value) earbudScanner.start() }
    fun stopEarbudScan() { earbudScanner.stop() }
    fun refreshEnvironmentPermissions() { sampler.invalidateEnvironment() }

    fun setOverlayEnabled(value: Boolean) {
        if (value) stopRequested = false
        mutableState.update { it.copy(overlayEnabled = value) }
        reconcileService()
    }

    fun setServiceRunning(value: Boolean) {
        serviceActive.value = value
        mutableState.update { it.copy(serviceRunning = value) }
        if (!value && (state.value.activePerformance != null || state.value.performanceBusy)) stopPerformanceRecording(interrupted = true)
    }

    fun startPerformanceRecording(name: String) {
        if (!visible.value || !state.value.performanceReady || state.value.performanceBusy || state.value.activePerformance != null) return
        stopRequested = false
        val generation = ++performanceGeneration
        mutableState.update { it.copy(performanceBusy = true) }
        // Request the foreground service while the initiating Activity is still visible.
        reconcileService()
        if (generation != performanceGeneration || stopRequested) return
        val started = SystemClock.elapsedRealtime()
        scope.launch {
            try {
                val epoch = System.currentTimeMillis()
                val title = name.trim().take(80).ifEmpty {
                    "游戏记录 " + java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(epoch))
                }
                val session = performanceStore.start(title, epoch, state.value.settings.sampleIntervalMs)
                if (generation != performanceGeneration || stopRequested) {
                    performanceStore.finish(session.id, System.currentTimeMillis(), SystemClock.elapsedRealtime() - started)
                    return@launch
                }
                performanceStartedElapsed = started
                mutableState.update { it.copy(activePerformance = session, performanceBusy = false, performanceReview = null) }
                reconcileService()
                refreshPerformanceSessions()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (generation == performanceGeneration) mutableState.update { it.copy(performanceBusy = false) }
                reportError("无法开始性能记录：${e.javaClass.simpleName}")
            }
        }
    }

    fun stopPerformanceRecording(interrupted: Boolean = false) {
        performanceGeneration++
        val session = state.value.activePerformance
        val duration = SystemClock.elapsedRealtime() - performanceStartedElapsed
        mutableState.update { it.copy(activePerformance = null, performanceBusy = false) }
        if (session != null) scope.launch {
            try {
                performanceStore.finish(session.id, System.currentTimeMillis(), duration, interrupted)
                refreshPerformanceSessions()
                if (visible.value) reviewPerformance(session.id)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { reportError("无法完成性能记录：${e.javaClass.simpleName}；已写入的样本仍保存在本机。") }
        }
        reconcileService()
    }

    fun reviewPerformance(id: Long) {
        reviewJob?.cancel()
        reviewJob = scope.launch {
            mutableState.update { it.copy(performanceReviewLoading = true) }
            try {
                val review = performanceStore.review(id)
                mutableState.update { it.copy(performanceReview = review, performanceReviewLoading = false) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                mutableState.update { it.copy(performanceReviewLoading = false) }
                reportError("无法打开性能记录：${e.javaClass.simpleName}")
            }
        }
    }

    private suspend fun savePerformancePoint(session: PerformanceSession, started: Long, snapshot: MonitorSnapshot) {
        try {
            withContext(NonCancellable) {
                val saved = performanceStore.append(session.id, PerformancePoint.from(snapshot, started))
                if (state.value.activePerformance?.id == session.id) {
                    if (saved) mutableState.update { it.copy(activePerformance = it.activePerformance?.copy(
                        sampleCount = it.activePerformance.sampleCount + 1,
                        durationMillis = snapshot.elapsedRealtimeMillis - started)) }
                    else {
                        stopPerformanceRecording()
                        reportError("本场记录已达到 ${PerformanceStore.MAX_POINTS} 个样本，已自动结束并保存。")
                    }
                }
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            stopPerformanceRecording(interrupted = true)
            reportError("性能记录写入失败，已停止记录：${e.javaClass.simpleName}；可查看此前已保存的样本。")
        }
    }

    private suspend fun refreshPerformanceSessions() {
        try {
            val sessions = performanceStore.sessions()
            mutableState.update { it.copy(performanceSessions = sessions) }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { reportError("无法读取性能记录列表：${e.javaClass.simpleName}") }
    }

    /** Called from the foreground UI's explicit Root button. */
    fun requestRoot() {
        rootManuallyDisabled = false
        requestRootInternal(auto = false)
    }

    private fun requestRootInternal(auto: Boolean) {
        if (!visible.value) {
            if (!auto) reportError("请在应用前台主动请求 Root 授权。")
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
                    reportError(
                        if (auto) "Root 自动恢复失败：$detail；可在设置中手动重试。"
                        else "Root 请求失败：$detail；基本采集仍可使用。",
                    )
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
        rootManuallyDisabled = true
        rootReader.close()
        mutableState.update {
            it.copy(rootState = rootReader.state.value,
                snapshot = it.snapshot?.let { sample -> sample.copy(fps = sample.fps.validatedFor(rootReader.state.value)) })
        }
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
        if (state.value.bluetoothScanning) earbudScanner.stop("已停止全部监控和前台耳机扫描。")
        disableRoot()
        stopRequested = true
        stopPerformanceRecording()
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
        val wanted = !stopRequested && (current.settings.historyEnabled || current.overlayEnabled ||
            current.activePerformance != null || current.performanceBusy)
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
