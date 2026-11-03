<!-- SPDX-License-Identifier: Apache-2.0 -->
# v0.2 协作契约

本文定义后续阶段的稳定接口和行为约束；本阶段只增加模型与 UI 接口，不实现采集、特权访问或界面功能。新代码使用 Apache-2.0，不复制 OpenMonitor 或 GPL 代码。

## Core 模型

以下均位于 `org.libremonitor.core`。已有字段及其默认值保持不变，新增字段追加在构造参数末尾，旧调用继续适用；`Reading.unavailable` 表示无可用值，不能用零代替。

- `BatterySample` 追加以下均默认为 unavailable 的字段：`healthStatus: Reading<String>`、`healthPercent: Reading<Double>`、`designCapacityMah: Reading<Double>`、`fullChargeCapacityMah: Reading<Double>`、`cycleCount: Reading<Long>`、`chargingSource: Reading<String>`。`GOOD` 是健康状态，不表示 100%。`healthPercent` 仅使用同一电池、同一单位的 full-charge capacity / design capacity × 100；不可混用 charge 与 energy 或不同电池，无法验证时 unavailable。mAh 容量字段只在具备可靠的 mAh 来源或有效换算条件时提供。
- `CpuSample` 追加 `coreIds: List<Int> = perCoreUsagePercent.indices.toList()` 和 `sensors: List<ThermalSensor> = emptyList()`。`coreIds` 与逐核使用率按索引对应；旧调用默认沿用旧逐核列表索引，采集器知道真实核 ID 时必须提供真实 ID。`ThermalSensor(id: String, name: String, temperatureC: Reading<Double>, kind: SensorKind)`，`SensorKind` 为 `CPU / GPU / BATTERY / SKIN / OTHER`。不得把区域传感器温度伪造为逐核温度或推测核号。
- `MonitorSnapshot` 追加 `gpu: GpuSample = GpuSample()`；`GpuSample` 包含 `name: Reading<String>`、`usagePercent: Reading<Double>`、`frequencyMhz: Reading<Double>`、`temperatureC: Reading<Double>`，各默认 unavailable。
- `RootAccess` 枚举值为 `DISABLED / REQUESTING / GRANTED / DENIED / UNAVAILABLE / ERROR`。`RootState(access: RootAccess = DISABLED, message: String = "", sessionId: Long = 0)`。
- `PrivilegedNodeFrame(readings: Map<String, Reading<String>>, sampledElapsedRealtimeMs: Long, sessionId: Long)`；键是固定的完整节点路径，HAL 的特殊键仅为 `thermalservice`。时间采用单调 elapsed realtime；会话标识用于拒绝关闭、替换或失效会话的帧。

不修改历史 `BatteryRecord`、SQLite 或 CSV 格式，不改变现有字段的含义或默认值。

## UI 接口

`org.libremonitor.app.MonitorUiState` 追加 `rootState: RootState = RootState()`；`MonitorActions` 追加 `onRootRequest: () -> Unit = {}`、`onRootDisable: () -> Unit = {}`。Root 状态仅表示当前活跃会话，不写入持久设置；不因模型存在而宣称后端已可用。

## 特权读取协作协议

后续实现类 `app.data.privileged.RootReader()`（应用包下的 `org.libremonitor.app.data.privileged.RootReader`）仅使用平台 Java/Android 和已有 coroutines，不新增依赖：

```kotlin
val state: StateFlow<RootState>
suspend fun requestAuthorization(): Boolean
suspend fun readFrame(): PrivilegedNodeFrame?
fun close()
```

- `requestAuthorization()` 只能由用户主动操作触发，授权请求超时 20 秒。一个已授权的持久 `su` 会话供采样复用，不得每轮重新 `su`。
- 不自动重试；任何授权失败或超时都锁存，不能在后台再次 `su`。`close()` 终止当前会话和未完成读取，并使其帧失效。禁用后再次授权只能由新的用户动作触发。
- `readFrame()` 单次读取整包最多 2 秒；frame 整包不超过 512 KiB，并有完整性 marker。超时、超限、缺失 marker、截断或关闭时不得交付不完整帧。过期帧必须拒绝：以单调时间检查读取时效，并拒绝非当前活跃会话或超出单读包 2 秒有效窗口的帧，不能回退到上次旧帧。
- 只允许以下固定只读节点，键为完整路径；HAL 使用固定命令及特殊键。禁止调用方传入任意命令或路径，不得写系统节点或执行其他特权操作：
  - `/proc/stat`，最多 256 KiB。
  - `/sys/devices/system/cpu/cpu0..255/cpufreq/scaling_cur_freq`，仅限实际核数并设上限。
  - `/sys/class/thermal/thermal_zone0..95/type` 和 `/sys/class/thermal/thermal_zone0..95/temp`，各最多 128 B。
  - `/sys/class/power_supply/{battery,bms}/{type,scope,present,status,health,charge_full_design,charge_full,energy_full_design,energy_full,cycle_count,state_of_health,voltage_now,current_now}`，各最多 256 B。
  - 固定命令 `/system/bin/dumpsys thermalservice`，输出最多 64 KiB，键 `thermalservice`。
  - KGSL 固定节点 `/sys/class/kgsl/kgsl-3d0/gpuclk`、`/sys/class/kgsl/kgsl-3d0/gpubusy`、`/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage`，各最多 256 B。

节点缺失、拒绝访问或无效值应通过对应 unavailable `Reading` 表达，不能造值。计数器速率使用单调时间，并拒绝重置或无效数据。

## 采样集成协议

- `AndroidSampler(context, rootReader: RootReader)` 构造；`deviceInfo()` 和 `sample()` 保持原有接口。
- `CpuCollector.sample(elapsedMillis: Long, frame: PrivilegedNodeFrame? = null)`。
- `BatteryCollector.sample(context)` 先读取普通 API，再由独立实现的 `AdvancedBatteryCollector.enrich(base: BatterySample, frame: PrivilegedNodeFrame?): BatterySample` 补充。
- CPU/电池 fallback 允许读取普通可读的固定节点；不能用应用自身指标冒充全局设备指标。所有采样继续由共享引擎提供给 UI、记录和悬浮窗，不新增重复采样循环。
- 状态、授权和读取的实际实现、设备探测、构建及测试均属于后续阶段。
