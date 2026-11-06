/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.data

import kotlinx.coroutines.CancellationException
import org.libremonitor.app.data.privileged.RootSession
import org.libremonitor.core.FpsSample
import org.libremonitor.core.PrivilegedNodeFrame
import org.libremonitor.core.Reading
import org.libremonitor.core.RootState

internal data class PrivilegedSample(
    val frame: PrivilegedNodeFrame?,
    val fps: FpsSample,
    val fpsReadElapsedMillis: Long,
    val fpsDurationMillis: Long = 0,
    val frameDurationMillis: Long = 0,
) {
    fun fpsAt(root: RootState, nowElapsedMillis: Long): FpsSample {
        val current = fps.validatedFor(root)
        if (!current.fps.available) return current
        if (nowElapsedMillis >= fpsReadElapsedMillis && nowElapsedMillis - fpsReadElapsedMillis <= 2_000L) return current
        val reason = "帧率读取后其他采集耗时过长，等待新的帧率样本"
        return current.copy(fps = Reading.unavailable("SurfaceFlinger", reason),
            onePercentLow = Reading.unavailable("SurfaceFlinger", reason),
            frameTimeMs = Reading.unavailable("SurfaceFlinger", reason))
    }
}

/** Slow FPS discovery must finish before capturing the short-lived CPU/GPU node frame. */
internal suspend fun readPrivilegedSample(
    reader: RootSession,
    includeDetails: Boolean,
    includeFps: Boolean = true,
    includeNetwork: Boolean = false,
    elapsedMillis: () -> Long,
): PrivilegedSample {
    val started = elapsedMillis()
    val fps = if (!includeFps) FpsSample() else try { reader.readFps() } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) { null } ?: FpsSample()
    val fpsReadElapsed = elapsedMillis()
    val frame = try {
        reader.readSamplingFrame(includeDetails, includeNetwork)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) { null }
    return PrivilegedSample(frame, fps, fpsReadElapsed,
        (fpsReadElapsed - started).coerceAtLeast(0), (elapsedMillis() - fpsReadElapsed).coerceAtLeast(0))
}
