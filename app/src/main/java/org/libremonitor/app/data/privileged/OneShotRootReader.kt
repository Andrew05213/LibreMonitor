/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.data.privileged

import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.libremonitor.core.FpsSample
import org.libremonitor.core.PermissionGrantResult
import org.libremonitor.core.PrivilegedNodeFrame
import org.libremonitor.core.RootAccess
import org.libremonitor.core.RootState
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/**
 * KernelSU/Magisk-compatible root backend. Each operation is one fixed `su -c`
 * command; no interactive shell is required and no caller text enters the command.
 */
class OneShotRootReader internal constructor(
    private val startProcess: (String) -> Process,
    private val authTimeoutMs: Long = 60_000L,
    private val readTimeoutMs: Long = 15_000L,
    private val monotonicNanos: () -> Long = { System.nanoTime() },
) : RootSession {
    constructor() : this({ script ->
        ProcessBuilder("su", "-c", script).redirectErrorStream(false).start()
    })

    private val operation = Mutex()
    private val mutableState = MutableStateFlow(RootState())
    override val state: StateFlow<RootState> = mutableState.asStateFlow()
    private var nextSessionId = 0L
    private val random = SecureRandom()
    private val fpsTracker = FpsTracker()
    private val fpsLifecycle = Any()
    private var metadataAt = Long.MIN_VALUE
    private var metadataSession = -1L
    private var metadata = emptyMap<String, org.libremonitor.core.Reading<String>>()
    private var thermalZones = emptySet<Int>()

    override suspend fun requestAuthorization(): Boolean = operation.withLock {
        mutableState.value = RootState(RootAccess.REQUESTING, "Waiting for user authorization", mutableState.value.sessionId)
        val id = randomId()
        val output = withContext(Dispatchers.IO) { run(RootProtocol.probeScript(id), authTimeoutMs) }
        val lines = output?.lineSequence()
            ?.filter { it.startsWith("LM:$id:") }
            ?.toList()
            .orEmpty()
        val ok = RootProtocol.verifyProbe(lines, id)
        if (ok) {
            fpsTracker.reset()
            nextSessionId++
            mutableState.value = RootState(RootAccess.GRANTED, "Root one-shot session active", nextSessionId)
        } else {
            val detail = lines.joinToString(" | ").take(240)
            mutableState.value = RootState(
                if (output == null) RootAccess.UNAVAILABLE else RootAccess.DENIED,
                if (output == null) "Root command failed or timed out" else "Root identity could not be verified: $detail",
                mutableState.value.sessionId,
            )
        }
        ok
    }

    override suspend fun readFrame(): PrivilegedNodeFrame? = readFrame(RootProtocol.compactNodes())

    override suspend fun readOverlayFrame(): PrivilegedNodeFrame? = readFrame(RootProtocol.overlayNodes())
    override suspend fun readSamplingFrame(details: Boolean, network: Boolean): PrivilegedNodeFrame? {
        val session = state.value.sessionId
        val now = SystemClock.elapsedRealtime()
        val discover = details && (metadataSession != session || now - metadataAt !in 0..29_999)
        val nodes = RootProtocol.samplingNodes(details, network, discover, thermalZones)
        val frame = readFrame(nodes) ?: return null
        if (discover) {
            thermalZones = (0 until 32).filter { frame.readings["/sys/class/thermal/thermal_zone$it/type"]?.available == true }.toSet()
            metadata = frame.readings.filterKeys { it.startsWith("/sys/class/power_supply/") || it.endsWith("/type") }
            metadataAt = frame.sampledElapsedRealtimeMs
            metadataSession = session
        }
        // Capacities, cycles and sensor labels are explicitly annotated slow metadata.
        // Never merge cached temperatures, frequencies, current, voltage or GPU readings.
        val slow = if (details && metadataSession == frame.sessionId && frame.sampledElapsedRealtimeMs - metadataAt in 0..29_999)
            metadata.filterKeys { !it.endsWith("/current_now") && !it.endsWith("/voltage_now") && !it.endsWith("/status") }
                .mapValues { (_, reading) -> reading.copy(reason = listOfNotNull(reading.reason, "静态字段缓存，最长 30 秒").joinToString("；")) }
            else emptyMap()
        return frame.copy(readings = slow + frame.readings)
    }
    override suspend fun readDiagnosticsFrame(): PrivilegedNodeFrame? = readFrame(RootProtocol.diagnosticNodes(), minOf(readTimeoutMs, 3_000L))

    private suspend fun readFrame(nodes: List<RootProtocol.Node>, timeoutMs: Long = readTimeoutMs): PrivilegedNodeFrame? {
        if (!operation.tryLock()) return null
        return try {
            val root = mutableState.value
            if (root.access != RootAccess.GRANTED) return null
            val id = randomId()
            val started = SystemClock.elapsedRealtime()
            val output = runInterruptible(Dispatchers.IO) {
                run(RootProtocol.frameScript(id, nodes), timeoutMs)
            } ?: return null
            // Root manager banners or prompts must not be parsed as frame data.
            val begin = "LM:$id:BEGIN"
            val clean = output.substring(output.indexOf(begin).coerceAtLeast(0))
            if (!clean.startsWith(begin)) return null
            val readings = RootProtocol.readFrame(
                ByteArrayInputStream(clean.toByteArray(Charsets.US_ASCII)), id, nodes,
            )
            val ended = SystemClock.elapsedRealtime()
            if (ended < started || ended - started > timeoutMs) return null
            synchronized(this) {
                if (mutableState.value.access != RootAccess.GRANTED ||
                    mutableState.value.sessionId != root.sessionId) null
                else PrivilegedNodeFrame(readings, ended, root.sessionId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } finally {
            operation.unlock()
        }
    }

    override suspend fun grantPermissions(): PermissionGrantResult = operation.withLock {
        if (mutableState.value.access != RootAccess.GRANTED) {
            return@withLock PermissionGrantResult(message = "Root 会话未启用")
        }
        val id = randomId()
        val output = withContext(Dispatchers.IO) {
            run(RootProtocol.permissionScript(id), authTimeoutMs)
        } ?: return@withLock PermissionGrantResult(message = "授权命令超时或失败")
        val lines = output.lineSequence().filter { it.startsWith("LM:$id:") }.toList()
        if (lines.size < 5 || lines[4] != "LM:$id:END") {
            return@withLock PermissionGrantResult(message = "授权结果不完整")
        }
        val notifications = lines[0].endsWith(":OK")
        val overlay = lines[1].endsWith(":OK")
        val battery = lines[2].endsWith(":OK")
        val bluetooth = lines[3].endsWith(":OK")
        PermissionGrantResult(
            notifications = notifications,
            overlay = overlay,
            batteryOptimization = battery,
            bluetooth = bluetooth,
            message = "通知 " + (if (notifications) "已授予" else "未授予") +
                " · 悬浮窗 " + (if (overlay) "已允许" else "未允许") +
                " · 电池白名单 " + (if (battery) "已加入" else "未加入") +
                " · 蓝牙 " + (if (bluetooth) "已授予" else "未授予"),
        )
    }

    override suspend fun readFps(): FpsSample? {
        if (!operation.tryLock()) return FpsTracker.unavailable("Root 正在处理其他请求")
        return try {
            val root = mutableState.value
            if (root.access != RootAccess.GRANTED) return FpsTracker.unavailable("请先启用 Root 帧率读取")
            val id = randomId()
            val output = runInterruptible(Dispatchers.IO) {
                run(RootProtocol.fpsScript(id), minOf(readTimeoutMs, FpsProtocol.PROCESS_TIMEOUT_MS))
            } ?: return FpsTracker.unavailable("帧率读取失败或超时；请检查 Root 状态及 ROM 接口支持")
            // A close/replacement while dumpsys was running invalidates this result too.
            synchronized(fpsLifecycle) {
                val current = mutableState.value
                if (current.access != RootAccess.GRANTED || current.sessionId != root.sessionId) {
                    FpsTracker.unavailable("Root 会话已停用或更换，已丢弃迟到帧率")
                } else fpsTracker.sample(FpsProtocol.decode(output, id), monotonicNanos()).copy(rootSessionId = root.sessionId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            FpsTracker.unavailable("帧率响应读取或解析失败")
        } finally {
            operation.unlock()
        }
    }

    override fun close() {
        synchronized(fpsLifecycle) {
            mutableState.value = RootState(RootAccess.DISABLED, "Root session disabled", mutableState.value.sessionId)
            fpsTracker.reset()
        }
    }

    private fun run(script: String, timeoutMs: Long): String? {
        val process = try {
            startProcess(script)
        } catch (_: Exception) {
            return null
        }
        val output = BoundedBuffer(RootProtocol.MAX_FRAME)
        val errors = BoundedBuffer(RootProtocol.MAX_STDERR)
        val outThread = Thread({ copy(process.inputStream, output) }, "libremonitor-root-out").apply { isDaemon = true }
        val errThread = Thread({ copy(process.errorStream, errors) }, "libremonitor-root-err").apply { isDaemon = true }
        outThread.start()
        errThread.start()
        return try {
            val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) return null
            outThread.join(1_000L)
            errThread.join(1_000L)
            if (process.exitValue() == 0 && !output.overflow && !errors.overflow) output.text() else null
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            null
        } finally {
            // Screen/consumer changes cancel blocking reads as well as the coroutine delay.
            process.destroyForcibly()
            runCatching { process.inputStream.close() }
            runCatching { process.errorStream.close() }
            runCatching { process.outputStream.close() }
        }
    }

    private fun copy(input: InputStream, output: BoundedBuffer) {
        input.use {
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val count = try { it.read(buffer) } catch (_: Exception) { -1 }
                if (count < 0) break
                if (!output.write(buffer, count)) break
            }
        }
    }

    private fun randomId(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    private class BoundedBuffer(private val limit: Int) {
        private val bytes = ByteArrayOutputStream()
        var overflow = false
        fun write(buffer: ByteArray, count: Int): Boolean {
            if (bytes.size() + count > limit) {
                overflow = true
                return false
            }
            bytes.write(buffer, 0, count)
            return true
        }
        fun text(): String = bytes.toString(Charsets.UTF_8.name())
    }
}
