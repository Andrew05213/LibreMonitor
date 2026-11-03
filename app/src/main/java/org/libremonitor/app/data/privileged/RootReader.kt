/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.data.privileged

import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import org.libremonitor.core.PermissionGrantResult
import org.libremonitor.core.PrivilegedNodeFrame
import org.libremonitor.core.RootAccess
import org.libremonitor.core.RootState
import java.io.OutputStream
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** User-triggered, non-persistent, read-only privileged session. */
class RootReader internal constructor(
    private val startProcess: () -> Process,
    private val clock: () -> Long,
    private val authTimeoutMs: Long = 60_000L,
    private val readTimeoutMs: Long = 2_000L,
) : RootSession {
    constructor() : this(
        // Some root managers do not expose an interactive shell without -c.
        // This is still a fixed command with no caller-provided input.
        { ProcessBuilder("su", "-c", "/system/bin/sh").redirectErrorStream(false).start() },
        { SystemClock.elapsedRealtime() },
    )

    private val lock = Any()
    private val operation = Mutex()
    private val mutableState = MutableStateFlow(RootState())
    override val state: StateFlow<RootState> = mutableState.asStateFlow()
    private var active: Session? = null
    private var generation = 0L
    private var nextSessionId = 0L
    private val random = SecureRandom()
    private val outstandingWorkers = AtomicInteger()

    private inner class Session(val process: Process, val generation: Long) {
        val closed = AtomicBoolean(false)
        val stderrOverflow = AtomicBoolean(false)
        @Volatile var stderrThread: Thread? = null

        fun close() {
            if (!closed.compareAndSet(false, true)) return
            // Destroy first so blocked reads/writes are released. Never target unrelated processes.
            fun release(name: String, action: () -> Unit) {
                outstandingWorkers.incrementAndGet()
                Thread({
                    try { action() } catch (_: Exception) { }
                    finally { outstandingWorkers.decrementAndGet() }
                }, name).apply { isDaemon = true; start() }
            }
            release("libremonitor-root-destroy") {
                process.destroy()
                process.destroyForcibly()
            }
            release("libremonitor-root-stdin-close") { process.outputStream.close() }
            release("libremonitor-root-stdout-close") { process.inputStream.close() }
            release("libremonitor-root-stderr-close") { process.errorStream.close() }
            stderrThread?.interrupt()
        }
    }

    private fun randomId(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    private fun beginStderrDrain(session: Session) {
        outstandingWorkers.incrementAndGet()
        val thread = Thread({
            val buffer = ByteArray(1024)
            var count = 0
            try {
                while (!session.closed.get()) {
                    val read = session.process.errorStream.read(buffer)
                    if (read < 0) break
                    count += read
                    if (count > RootProtocol.MAX_STDERR) {
                        session.stderrOverflow.set(true)
                        fail(session, RootAccess.ERROR, "Root stderr exceeded limit")
                        break
                    }
                }
            } catch (_: Exception) {
                if (!session.closed.get()) fail(session, RootAccess.ERROR, "Root stderr failed")
            } finally {
                outstandingWorkers.decrementAndGet()
            }
        }, "libremonitor-root-stderr")
        thread.isDaemon = true
        session.stderrThread = thread
        thread.start()
    }

    private fun closeAsync(session: Session) {
        session.close()
    }

    private fun fail(session: Session, access: RootAccess, message: String) {
        synchronized(lock) {
            if (active === session) {
                active = null
                generation++
                mutableState.value = RootState(access, message, mutableState.value.sessionId)
            }
            closeAsync(session)
        }
    }

    private suspend fun <T> timed(session: Session, millis: Long, auth: Boolean = false, block: () -> T): T =
        withTimeout(millis) {
            suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation {
                    synchronized(lock) {
                        if (active === session) {
                            active = null
                            generation++
                            mutableState.value = RootState(
                                if (auth) RootAccess.DENIED else RootAccess.ERROR,
                                "Root operation timed out or cancelled", mutableState.value.sessionId,
                            )
                        }
                        closeAsync(session)
                    }
                }
                outstandingWorkers.incrementAndGet()
                val worker = Thread({
                    try {
                        val value = block()
                        if (continuation.isActive) continuation.resume(value)
                    } catch (e: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    } finally {
                        outstandingWorkers.decrementAndGet()
                    }
                }, "libremonitor-root-operation")
                worker.isDaemon = true
                worker.start()
            }
        }

    private suspend fun start(token: Long): Session = suspendCancellableCoroutine { continuation ->
        val pending = AtomicReference<Session?>(null)
        continuation.invokeOnCancellation {
            synchronized(lock) {
                val abandoned = if (generation == token) {
                    generation++
                    val current = active
                    active = null
                    mutableState.value = RootState(RootAccess.DENIED, "Authorization timed out or cancelled", mutableState.value.sessionId)
                    current
                } else null
                (abandoned ?: pending.get())?.let { closeAsync(it) }
            }
        }
        outstandingWorkers.incrementAndGet()
        val worker = Thread({
            try {
                val created = Session(startProcess(), token)
                pending.set(created)
                if (!continuation.isActive) {
                    created.close()
                } else {
                    val installed = synchronized(lock) {
                        if (generation != token || !continuation.isActive) false
                        else { active = created; true }
                    }
                    if (!installed) {
                        created.close()
                        if (continuation.isActive) continuation.resumeWithException(IllegalStateException("Session closed"))
                    } else {
                        continuation.resume(created) { closeAsync(created) }
                    }
                }
            } catch (e: Exception) {
                if (continuation.isActive) continuation.resumeWithException(e)
            } finally {
                outstandingWorkers.decrementAndGet()
            }
        }, "libremonitor-root-start")
        worker.isDaemon = true
        worker.start()
    }

    override suspend fun requestAuthorization(): Boolean {
        // Contention is rejected instead of forming an unbounded queue.
        if (!operation.tryLock()) return false
        val prior = synchronized(lock) { active }
        if (prior == null && outstandingWorkers.get() != 0) {
            synchronized(lock) {
                if (mutableState.value.access != RootAccess.DISABLED) {
                    mutableState.value = RootState(RootAccess.UNAVAILABLE, "Previous root session is still closing", mutableState.value.sessionId)
                }
            }
            operation.unlock()
            return false
        }
        var session: Session? = null
        try {
            val token: Long
            synchronized(lock) {
                if (active != null && mutableState.value.access == RootAccess.GRANTED) return true
                generation++
                token = generation
                mutableState.value = RootState(RootAccess.REQUESTING, "Waiting for user authorization", mutableState.value.sessionId)
            }
            // Process startup itself runs on IO and is included in the authorization deadline.
            return try {
                withTimeout(authTimeoutMs) {
                    val created = start(token)
                    session = created
                    val process = created.process
                    beginStderrDrain(created)
                    val id = randomId()
                    val ok = timed(created, authTimeoutMs, auth = true) {
                        writeScript(process.outputStream, RootProtocol.probeScript(id))
                        val budget = RootProtocol.Budget(4096)
                        val lines = List(4) { RootProtocol.readLine(process.inputStream, budget) }
                        RootProtocol.verifyProbe(lines, id)
                    }
                    synchronized(lock) {
                        if (!ok || active !== created || created.closed.get() || created.stderrOverflow.get()) {
                            if (active === created) {
                                active = null
                                generation++
                                mutableState.value = RootState(RootAccess.DENIED, "Root identity could not be verified", mutableState.value.sessionId)
                            }
                            closeAsync(created)
                            false
                        } else {
                            nextSessionId++
                            mutableState.value = RootState(RootAccess.GRANTED, "Root session active", nextSessionId)
                            true
                        }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                session?.let { fail(it, RootAccess.DENIED, "Authorization timed out") }
                synchronized(lock) {
                    if (generation == token) {
                        generation++
                        mutableState.value = RootState(RootAccess.DENIED, "Authorization timed out", mutableState.value.sessionId)
                    }
                }
                false
            } catch (e: CancellationException) {
                session?.let { fail(it, RootAccess.ERROR, "Authorization cancelled") }
                synchronized(lock) {
                    if (generation == token) {
                        generation++
                        mutableState.value = RootState(RootAccess.ERROR, "Authorization cancelled", mutableState.value.sessionId)
                    }
                }
                throw e
            } catch (_: Exception) {
                session?.let { fail(it, RootAccess.UNAVAILABLE, "Root authorization unavailable") }
                synchronized(lock) {
                    if (generation == token) {
                        generation++
                        mutableState.value = RootState(RootAccess.UNAVAILABLE, "Root authorization unavailable", mutableState.value.sessionId)
                    }
                }
                false
            }
        } finally {
            operation.unlock()
        }
    }

    override suspend fun readFrame(): PrivilegedNodeFrame? {
        if (!operation.tryLock()) return null
        try {
            val session: Session
            val sessionId: Long
            synchronized(lock) {
                session = active ?: return null
                if (mutableState.value.access != RootAccess.GRANTED || session.closed.get()) return null
                sessionId = mutableState.value.sessionId
            }
            val started = clock()
            return try {
                val readings = timed(session, readTimeoutMs) {
                    val id = randomId()
                    val nodes = RootProtocol.nodes()
                    writeScript(session.process.outputStream, RootProtocol.frameScript(id, nodes))
                    RootProtocol.readFrame(session.process.inputStream, id, nodes)
                }
                val ended = clock()
                if (ended < started || ended - started > readTimeoutMs || ended - started > 2_000L) {
                    fail(session, RootAccess.ERROR, "Root frame expired")
                    null
                } else synchronized(lock) {
                    if (active !== session || session.closed.get() || session.stderrOverflow.get() ||
                        mutableState.value.access != RootAccess.GRANTED || mutableState.value.sessionId != sessionId
                    ) null else PrivilegedNodeFrame(readings, ended, sessionId)
                }
            } catch (_: TimeoutCancellationException) {
                null
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                fail(session, RootAccess.ERROR, "Root frame failed or incomplete")
                null
            }
        } finally {
            operation.unlock()
        }
    }

    /**
     * Fixed app-scoped grants only. No caller-provided package, permission, path or
     * shell text reaches the privileged session. Requires the user's explicit UI action.
     */
    override suspend fun grantPermissions(): PermissionGrantResult {
        if (!operation.tryLock()) {
            return PermissionGrantResult(message = "Root 正在处理其他请求，请稍候重试")
        }
        try {
            val session: Session
            synchronized(lock) {
                session = active ?: return PermissionGrantResult(message = "Root 会话未启用")
                if (mutableState.value.access != RootAccess.GRANTED || session.closed.get()) {
                    return PermissionGrantResult(message = "Root 会话不可用，请重新授权")
                }
            }
            return try {
                val lines = timed(session, authTimeoutMs) {
                    val id = randomId()
                    writeScript(session.process.outputStream, RootProtocol.permissionScript(id))
                    RootProtocol.readPermissionResults(session.process.inputStream, id)
                }
                val notifications = lines[0].endsWith(":OK")
                val overlay = lines[1].endsWith(":OK")
                val battery = lines[2].endsWith(":OK")
                PermissionGrantResult(
                    notifications = notifications,
                    overlay = overlay,
                    batteryOptimization = battery,
                    message = buildString {
                        append("通知 ").append(if (notifications) "已授予" else "未授予")
                        append(" · 悬浮窗 ").append(if (overlay) "已允许" else "未允许")
                        append(" · 电池白名单 ").append(if (battery) "已加入" else "未加入")
                    },
                )
            } catch (_: TimeoutCancellationException) {
                fail(session, RootAccess.ERROR, "Root permission grant timed out")
                PermissionGrantResult(message = "授权命令超时，Root 会话已失效")
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                PermissionGrantResult(message = "授权命令执行失败，未确认全部权限")
            }
        } finally {
            operation.unlock()
        }
    }

    override fun close() {
        val old: Session?
        synchronized(lock) {
            generation++
            old = active
            active = null
            mutableState.value = RootState(RootAccess.DISABLED, "Root session disabled", mutableState.value.sessionId)
            old?.let { closeAsync(it) }
        }
    }

    private fun writeScript(output: OutputStream, script: String) {
        output.write(script.toByteArray(Charsets.US_ASCII))
        output.flush()
    }
}
