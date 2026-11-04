/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.data

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.SystemClock
import android.view.Display
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.libremonitor.app.data.collectors.BatteryCollector
import org.libremonitor.app.data.collectors.AdvancedBatteryCollector
import org.libremonitor.app.data.collectors.BluetoothCollector
import org.libremonitor.app.data.collectors.GpuCollector
import org.libremonitor.app.data.privileged.RootSession
import org.libremonitor.core.RootAccess
import org.libremonitor.app.data.collectors.CpuCollector
import org.libremonitor.app.data.collectors.MemoryCollector
import org.libremonitor.app.data.collectors.NetworkCollector
import org.libremonitor.app.data.collectors.StorageCollector
import org.libremonitor.app.data.collectors.boundedProcessorCount
import org.libremonitor.app.data.collectors.isolate
import org.libremonitor.core.DeviceInfo
import org.libremonitor.core.MonitorSnapshot

/**
 * One shared runtime calls sample serially; this class never starts its own loop.
 * All file/API sampling is read-only and dispatched off the UI thread.
 */
class AndroidSampler(context: Context, private val rootReader: RootSession) {
    private val context = context.applicationContext
    private val cpu = CpuCollector()
    private val network = NetworkCollector()

    fun deviceInfo(): DeviceInfo {
        val display = isolate(
            { context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY) },
            { null },
        )
        val mode = isolate({ display?.mode }, { null })
        val metrics = context.resources.displayMetrics
        // No hidden APIs or hardware database. NaN represents an unavailable refresh rate:
        // DeviceInfo has no nullable/status-bearing field for this one metadata value.
        val refresh = isolate(
            { display?.refreshRate?.takeIf { it.isFinite() && it > 0f } ?: Float.NaN },
            { Float.NaN },
        )
        return DeviceInfo(
            manufacturer = Build.MANUFACTURER.orEmpty().ifBlank { "未知" },
            model = Build.MODEL.orEmpty().ifBlank { "未知" },
            androidVersion = Build.VERSION.RELEASE.orEmpty().ifBlank { "未知" },
            sdkInt = Build.VERSION.SDK_INT,
            abis = Build.SUPPORTED_ABIS?.filter { it.isNotBlank() }.orEmpty(),
            processorCount = boundedProcessorCount(),
            screenWidthPx = mode?.physicalWidth?.takeIf { it > 0 } ?: metrics.widthPixels,
            screenHeightPx = mode?.physicalHeight?.takeIf { it > 0 } ?: metrics.heightPixels,
            refreshRateHz = refresh,
        )
    }

    suspend fun sample(): MonitorSnapshot = withContext(Dispatchers.IO) {
        // Exactly one privileged frame per shared sample; no implicit authorization request.
        val candidate = try { rootReader.readFrame() } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) { null }
        val elapsed = SystemClock.elapsedRealtime()
        val epoch = System.currentTimeMillis()
        val root = rootReader.state.value
        val frame = candidate?.takeIf {
            root.access == RootAccess.GRANTED && it.sessionId == root.sessionId &&
                elapsed >= it.sampledElapsedRealtimeMs && elapsed - it.sampledElapsedRealtimeMs <= 2_000L
        }
        // Independent operations are intentionally serial. Cancellation propagates.
        cpu.rootIdentity = "${root.access}:${root.sessionId}"
        val cpuSample = isolate({ cpu.sample(elapsed, frame) }, cpu::failure)
        val memorySample = isolate({ MemoryCollector.sample(context) }, MemoryCollector::failure)
        val baseBattery = isolate({ BatteryCollector.sample(context) }, BatteryCollector::failure)
        val batterySample = isolate({ AdvancedBatteryCollector.enrich(baseBattery, frame) }, { baseBattery })
        val gpuSample = isolate({ GpuCollector.sample(frame) }, { org.libremonitor.core.GpuSample() })
        val networkSample = isolate({ network.sample() }, network::failure)
        val storageSample = isolate({ StorageCollector.sample(context) }, StorageCollector::failure)
        val bluetoothSample = isolate({ BluetoothCollector.sample(context) }, { org.libremonitor.core.BluetoothSample() })
        val fpsCandidate = try { rootReader.readFps() } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) { null } ?: org.libremonitor.core.FpsSample()
        // A close/replacement during collection invalidates the whole privileged sample.
        // Re-read without privilege instead of publishing data from a revoked session.
        val completedAt = SystemClock.elapsedRealtime()
        val fpsSample = fpsCandidate.validatedFor(rootReader.state.value)
        if (frame != null && (completedAt < frame.sampledElapsedRealtimeMs ||
                completedAt - frame.sampledElapsedRealtimeMs > 2_000L || rootReader.state.value.let {
                    it.access != RootAccess.GRANTED || it.sessionId != frame.sessionId
                })) {
            val now = SystemClock.elapsedRealtime()
            val currentRoot = rootReader.state.value
            cpu.rootIdentity = "${currentRoot.access}:${currentRoot.sessionId}"
            val basicCpu = isolate({ cpu.sample(now) }, cpu::failure)
            val basicBattery = isolate({ AdvancedBatteryCollector.enrich(baseBattery, null) }, { baseBattery })
            val basicGpu = isolate({ GpuCollector.sample(null) }, { org.libremonitor.core.GpuSample() })
            return@withContext MonitorSnapshot(System.currentTimeMillis(), now, basicCpu, memorySample,
                basicBattery, networkSample, storageSample, basicGpu, bluetoothSample, fpsSample)
        }
        MonitorSnapshot(
            timestampEpochMillis = epoch,
            elapsedRealtimeMillis = elapsed,
            cpu = cpuSample,
            memory = memorySample,
            battery = batterySample,
            network = networkSample,
            storage = storageSample,
            gpu = gpuSample,
            bluetooth = bluetoothSample,
            fps = fpsSample,
        )
    }
}
