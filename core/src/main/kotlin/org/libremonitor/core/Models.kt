/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.core

/** Absence is data: unsupported and denied values must never render as zero. */
enum class ReadingStatus { AVAILABLE, UNSUPPORTED, PERMISSION_DENIED, ERROR }

data class Reading<T>(
    val value: T?,
    val source: String,
    val status: ReadingStatus = if (value == null) ReadingStatus.UNSUPPORTED else ReadingStatus.AVAILABLE,
    val reason: String? = null,
) {
    init {
        require((status == ReadingStatus.AVAILABLE) == (value != null)) {
            "A reading is available if and only if it has a value"
        }
    }
    val available: Boolean get() = status == ReadingStatus.AVAILABLE

    companion object {
        fun <T> unavailable(source: String, reason: String,
                            status: ReadingStatus = ReadingStatus.UNSUPPORTED): Reading<T> {
            require(status != ReadingStatus.AVAILABLE)
            return Reading(null, source, status, reason)
        }
    }
}

data class CpuSample(
    val usagePercent: Reading<Double>,
    val perCoreUsagePercent: List<Reading<Double>>,
    val frequenciesMhz: List<Reading<Long>>,
    val temperatureC: Reading<Double>,
    /** IDs correspond by index to perCoreUsagePercent; actual IDs should be supplied when known. */
    val coreIds: List<Int> = perCoreUsagePercent.indices.toList(),
    val sensors: List<ThermalSensor> = emptyList(),
)

enum class SensorKind { CPU, GPU, BATTERY, SKIN, OTHER }

data class ThermalSensor(
    val id: String,
    val name: String,
    val temperatureC: Reading<Double>,
    val kind: SensorKind,
)

data class MemorySample(
    val totalBytes: Reading<Long>,
    val availableBytes: Reading<Long>,
    val swapTotalBytes: Reading<Long>,
    val swapFreeBytes: Reading<Long>,
)

data class BatterySample(
    val levelPercent: Reading<Int>,
    val temperatureC: Reading<Double>,
    val voltageV: Reading<Double>,
    /** Selected current in mA with explicit source/unit provenance; OEM sign is not calibrated. */
    val currentMa: Reading<Double>,
    val chargeCounterMah: Reading<Double>,
    /** Selected battery voltage x current; battery-terminal estimate, not charger or processor power. */
    val powerW: Reading<Double>,
    val status: String,
    val technology: String?,
    val healthStatus: Reading<String> = Reading.unavailable("battery", "Not sampled"),
    /** Same-battery, same-unit full-charge capacity / design capacity, as a percentage. */
    val healthPercent: Reading<Double> = Reading.unavailable("battery", "Not sampled"),
    val designCapacityMah: Reading<Double> = Reading.unavailable("battery", "Not sampled"),
    val fullChargeCapacityMah: Reading<Double> = Reading.unavailable("battery", "Not sampled"),
    val cycleCount: Reading<Long> = Reading.unavailable("battery", "Not sampled"),
    val chargingSource: Reading<String> = Reading.unavailable("battery", "Not sampled"),
    val electricalDiagnostics: BatteryElectricalDiagnostics? = null,
)

enum class BatteryCurrentMode(val label: String) { AUTO("自动识别"), MICROAMPS("标准 µA"), MILLIAMPS("厂商 mA") }
data class BatteryElectricalDiagnostics(val rawApiCurrent: Reading<Long>, val selectedSupply: String? = null,
    val currentUnit: String = "µA", val nodes: Map<String, Reading<String>> = emptyMap())

data class GpuSample(
    val name: Reading<String> = Reading.unavailable("gpu", "Not sampled"),
    val usagePercent: Reading<Double> = Reading.unavailable("gpu", "Not sampled"),
    val frequencyMhz: Reading<Double> = Reading.unavailable("gpu", "Not sampled"),
    val temperatureC: Reading<Double> = Reading.unavailable("gpu", "Not sampled"),
)

data class NetworkSample(
    val downloadBytesPerSecond: Reading<Double>,
    val uploadBytesPerSecond: Reading<Double>,
    val totalReceivedBytes: Reading<Long>,
    val totalSentBytes: Reading<Long>,
    val bluetoothDownload: Reading<Double> = Reading.unavailable("Bluetooth PAN", "尚未采集"),
    val bluetoothUpload: Reading<Double> = Reading.unavailable("Bluetooth PAN", "尚未采集"),
)

data class StorageSample(val totalBytes: Reading<Long>, val availableBytes: Reading<Long>)

/** SurfaceFlinger frame timing for the foreground layer; not display refresh rate. */
data class FpsSample(
    val fps: Reading<Double> = Reading.unavailable("FPS", "Not sampled"),
    val onePercentLow: Reading<Double> = Reading.unavailable("1% low", "Not sampled"),
    val frameTimeMs: Reading<Double> = Reading.unavailable("Frame time", "Not sampled"),
    val target: Reading<String> = Reading.unavailable("SurfaceFlinger", "Not sampled"),
    val rootSessionId: Long? = null,
    val hasNewReading: Boolean = true,
    val sampledElapsedRealtimeMillis: Long? = null,
) {
    /** Validate again at publication, not only when the IO operation returns. */
    fun validatedFor(root: RootState): FpsSample {
        if (root.access == RootAccess.GRANTED && (!fps.available || rootSessionId == root.sessionId)) return this
        val reason = "Root 会话已停用或更换，等待新的帧率样本"
        return FpsSample(Reading.unavailable("SurfaceFlinger", reason), Reading.unavailable("SurfaceFlinger", reason),
            Reading.unavailable("SurfaceFlinger", reason), Reading.unavailable("SurfaceFlinger", reason), hasNewReading = hasNewReading)
    }
    companion object { const val NOT_RESAMPLED = "本轮未重新采集帧率" }
}

/** Public-API Bluetooth view. RSSI/core version are deliberately unavailable when Android does not expose them. */
data class BluetoothDeviceInfo(
    val name: String,
    val address: String?,
    val type: String,
    val bondState: String,
    val deviceClass: String,
    val majorClass: String,
    val services: List<String>,
    val protocol: String,
    val batteryPercent: Reading<Int>,
    val rssiDbm: Reading<Int>,
    val earbudBattery: EarbudBattery? = null,
)

data class BluetoothSample(
    val supported: Reading<Boolean> = Reading.unavailable("Bluetooth", "Not sampled"),
    val enabled: Reading<Boolean> = Reading.unavailable("Bluetooth", "Not sampled"),
    val name: Reading<String> = Reading.unavailable("Bluetooth", "Not sampled"),
    val address: Reading<String> = Reading.unavailable("Bluetooth", "Not sampled"),
    val state: Reading<String> = Reading.unavailable("Bluetooth", "Not sampled"),
    val leSupported: Reading<Boolean> = Reading.unavailable("Bluetooth", "Not sampled"),
    val connectedCount: Reading<Int> = Reading.unavailable("Bluetooth", "Not sampled"),
    val version: Reading<String> = Reading.unavailable("Bluetooth", "Not sampled"),
    val devices: List<BluetoothDeviceInfo> = emptyList(),
    val capabilities: Map<String, Reading<String>> = emptyMap(),
)

data class MonitorSnapshot(
    val timestampEpochMillis: Long,
    val elapsedRealtimeMillis: Long,
    val cpu: CpuSample,
    val memory: MemorySample,
    val battery: BatterySample,
    val network: NetworkSample,
    val storage: StorageSample,
    val gpu: GpuSample = GpuSample(),
    val bluetooth: BluetoothSample = BluetoothSample(),
    val fps: FpsSample = FpsSample(),
    val environment: EnvironmentSample = EnvironmentSample(),
)

data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val sdkInt: Int,
    val abis: List<String>,
    val processorCount: Int,
    val screenWidthPx: Int,
    val screenHeightPx: Int,
    val refreshRateHz: Float,
    val cpuIdentity: CpuIdentity = CpuIdentity(),
)

data class BatteryRecord(
    val timestampEpochMillis: Long,
    val levelPercent: Int?,
    val temperatureC: Double?,
    val voltageV: Double?,
    val currentMa: Double?,
    val powerW: Double?,
    val status: String,
) {
    companion object {
        fun from(snapshot: MonitorSnapshot): BatteryRecord = with(snapshot.battery) {
            BatteryRecord(snapshot.timestampEpochMillis, levelPercent.value, temperatureC.value,
                voltageV.value, currentMa.value, powerW.value, status)
        }
    }
}

data class MonitorSettings(
    val historyEnabled: Boolean = false,
    val sampleIntervalMs: Long = 2_000L,
    val historyIntervalMs: Long = 60_000L,
    val retentionDays: Int = 7,
    /** Foreground convenience only: still requires Root-manager authorization and never boots silently. */
    val autoRestoreRoot: Boolean = false,
    val overlay: OverlayOptions = OverlayOptions(),
    val batteryCurrentMode: BatteryCurrentMode = BatteryCurrentMode.AUTO,
) {
    init {
        require(sampleIntervalMs in SAMPLE_INTERVAL_RANGE_MS)
        require(historyIntervalMs in 10_000L..3_600_000L)
        require(retentionDays in 1..30)
    }

    companion object {
        val SAMPLE_INTERVAL_RANGE_MS: LongRange = 100L..10_000L
    }
}

/** Implementations must probe individual capabilities, not infer them from a mode name. */
enum class BackendKind { BASIC, SHIZUKU, ROOT }
data class BackendCapability(val backend: BackendKind, val available: Boolean, val explanation: String)

object BackendCapabilities {
    val initial = listOf(
        BackendCapability(BackendKind.BASIC, true, "公共 API 与可读节点；逐项检测"),
        BackendCapability(BackendKind.SHIZUKU, false, "预留接口，当前版本尚未接入"),
        BackendCapability(BackendKind.ROOT, false, "当前版本不执行特权命令"),
    )
}
