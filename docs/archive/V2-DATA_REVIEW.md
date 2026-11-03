<!-- SPDX-License-Identifier: Apache-2.0 -->
# v0.2 数据层只读审阅

- 日期：2026-11-03
- 范围：`ui/**`、`core` 的 Models/AdvancedModels/Calculations/SensorMath 及其测试、`BatteryCollector`/`AdvancedBatteryCollector` 及其测试、`MonitorUiState`；为核对 Root 接线另读了 `MainActivity`/`MonitorRuntime`/`RootReader`/`AndroidSampler`。
- 方法：只做只读文件检查（read/grep 与文件列表）；**没有执行 Gradle、单测、安装或设备操作**。本文所有“通过”都是静态代码路径判断，不是运行结果；未运行的部分不视为通过。
- 实现文件未改动，只新增本报告。

## 结论

- **P0：未发现**可静态复现的数据伪造、真 0 当缺失或 Root 回调断线。Root 回调链在当前最终文件里是接好的。
- **P1：5 项 + 1 项次要**，集中在：容量健康 >100% 被 UI 丢弃、`healthPercent` 混入厂商值、CPU 温度文案/HAL 路径、5s 帧窗口与 2s 契约、验证命令未跑 app 单测且 `RootProtocolTest` 期望值错误。

## 用户要求逐项验证

| 检查项 | 结论 | 依据 |
|---|---|---|
| 首页无细小说明字/长段落 | **通过** | `tile(...note=...)` 的 note 只进 `MetricDetail`；首页 `MetricTile` 只渲染 label、value、unit、core 的 MHz 副行，notes 只在底部详情 `MetricDetailSheet` 出现（`ui/LibreMonitorScreen.kt:726-767, 830-921`）。 |
| 核心指标 label+value+unit | **通过** | `MetricTile` 的值与单位分开渲染；`Quantity` 保留 unit；电量、CPU、内存、网络、存储、容量、循环均有单位（`ui/ReadingFormat.kt:49-67`）。 |
| 每核直接可见 | **通过** | 概览“逐核”按 `coreIds` 生成卡片，不隐藏、不折叠（`ui/LibreMonitorScreen.kt:311-327, 778-792`）。 |
| 关键来源在点击详情 | **通过** | 详情页含状态、来源、缺失原因、估算说明、更新时间（`ui/LibreMonitorScreen.kt:890-921`）。 |
| 缺失/真 0 | **通过（有限定）** | `Reading` 保证 AVAILABLE ⇔ 有值；缺失显示 “—”，真 0 仍显示 0（`core/Models.kt:7-27`；`ui/ReadingFormat.kt:69-82`；`CalculationsTest.kt:140-142`）。限定：容量健康 101–150% 被 UI 当无效（P1-1）。 |
| 过期状态没假数 | **通过（有观察）** | 顶栏用单调时间给“实时/已过期/等待中”全局徽标（`ui/LibreMonitorScreen.kt:204-264`）；过期时仍显示的是真实旧读数，没有置零或编造。没有逐卡“过期”标记；详情“更新时间”可核对。 |
| Root 启用回调真实接线 | **通过（静态）** | `MainActivity` 把 `onRootRequest/onRootDisable` 接到 `runtime::requestRoot/disableRoot`（`MainActivity.kt:68-69`）；Runtime 调 `RootReader.requestAuthorization/close` 并收集 `state`（`MonitorRuntime.kt:58-61, 142-175`）；`RootControl` 按钮/确认框调用 actions（`LibreMonitorScreen.kt:569-625`）。未运行，故不声称运行时已验证。 |
| 电池健康/设计/FCC 同源同单位同 pack | **基本通过** | 健康只从同一个 `Supply`（同一 `battery` 或 `bms` 目录）的 `charge_full/charge_full_design` 计算；缺 charge 对时才用同一对象的 energy 对；设计/满充 mAh 同源（`AdvancedBatteryCollector.kt:236-271, 101-106`）。厂商 `state_of_health` 回退见 P1-2。 |
| GOOD 非 100 | **通过** | `GOOD` 只写入 `healthStatus`；不会写 `healthPercent`，GOOD 单独存在时容量健康为 unavailable（`BatteryCollector.kt:16-25`；`AdvancedBatteryCollector.kt:87-97, 220-233`；测试 `AdvancedBatteryCollectorTest.kt:184-197`）。 |
| counter 非 FCC | **通过** | `charge_counter` 从不被读取或映射；`CHARGE_COUNTER` 只作剩余电荷，原符号/数值复制（`BatteryCollector.kt:64, 153-155`；`AdvancedBatteryCollector.kt:113-121`；测试 `AdvancedBatteryCollectorTest.kt:110-111, 367-386`）。 |
| energy 不靠瞬时电压换 mAh | **通过** | `energy_full/energy_full_design` 只在同单位 µWh 内做比值，不读 `voltage_now`、不换算 mAh（`AdvancedBatteryCollector.kt:185-191, 255-271`；测试 `AdvancedBatteryCollectorTest.kt:132-152`）。 |
| 双电芯不自翻倍 | **通过** | 没有乘 2/猜测电芯数；200% 比值被 `1..150%` 拒绝且不打折（`SensorMath.kt:39-48`；测试 `SensorMathTest.kt:119-125`）。 |
| staleFrame 不回退旧特权值 | **通过（采样层）/P1（收集器层）** | `AndroidSampler` 只接受 ≤2s 且 session 匹配的帧，并在采样结束时再次复核，失效就改用普通读取（`AndroidSampler.kt:69-72, 86-99`）；收集器自身窗口是 5s（P1-4）。 |
| API34 公共循环 0 vs sysfs 0 | **通过（数据层）** | API<34/缺 extra 不伪装 0；API34 公共 0 保留并带 reason；sysfs `cycle_count=0` 为 unknown/unavailable（`BatteryCollector.kt:43-58, 201-215`；`AdvancedBatteryCollector.kt:193-203`；测试 `AdvancedBatteryCollectorTest.kt:216-252`）。详情页未展示公共 0 的语义说明（P1-6）。 |
| CPU 分项 reset/重复行/guest 新测试合理 | **基本通过** | 见下方“测试评估”；实现被测试覆盖到按分项检测 reset、重复行、guest 不重复计入。 |

## P1 发现

### P1-1 容量健康 101–150% 在 UI 被当无效值丢弃

- 位置：`ui/LibreMonitorScreen.kt:498-500`（`tile("容量健康", battery.healthPercent, validPercent, ...)`）；`ui/ReadingFormat.kt:84`（`validPercent` 仅 0..100）；`core/SensorMath.kt:44-48`（`healthPercent` 明确允许 1..150，且不 clamp）。
- 现象：`SensorMath` 和 `AdvancedBatteryCollector` 都保留合法的 >100% 健康度（测试 `SensorMathTest.kt:84-86` 断言 101%），但 UI 用 0..100 的通用校验，101–150% 会显示 “—”，详情写“样本无效，未将其作为 0 显示”。这是真实数据被隐藏，不是伪造。
- 影响：与“合法大于100%不缩放”的模型契约和测试矛盾；用户可能误以为健康数据不可用。
- 建议修复：为健康度单独定义校验（如 `1.0..150.0`，0/负值另行决定），不要复用 CPU/内存使用的 `validPercent`；保留原值不 clamp。
- 建议测试：纯 JVM 校验函数测试 `100/101/150` 通过、`150.1/0/NaN` 拒绝；UI/格式化测试断言 120% 渲染为 `120.0 %` 而不是 “—”。

### P1-2 `healthPercent` 混入厂商 `state_of_health`，与契约和“容量健康”文案不一致

- 位置：`AdvancedBatteryCollector.kt:91-97, 205-218`；契约 `docs/V2_CONTRACTS.md:10`；UI 文案 `ui/LibreMonitorScreen.kt:499-500`。
- 现象：没有 FCC/energy 对时，厂商 `state_of_health`（0..100）被直接放进 `BatterySample.healthPercent`，source 标注“不是 FCC/设计容量算法”。但契约规定 `healthPercent` 只能来自同一电池/同一单位的 full-charge ÷ design；UI 的“容量健康”说明仍无条件写“满充容量 ÷ 设计容量”。详情里 source 与 note 会互相矛盾。
- 影响：同一个字段表示两种语义；用户看到“容量健康”时无法仅从 label/note 判断是 FCC 还是厂商百分比。`state_of_health=0` 还会按 0% 显示，存在“厂商未知哨兵被当 0”的风险（Linux 驱动语义需确认）。
- 建议修复：二选一并与 lead 确认契约：a) `healthPercent` 严格只接受 FCC/energy 对，厂商百分比另设字段/不显示；b) 保留回退但增加 `healthBasis`（FCC / energy / vendor），UI 的 label/note 按 basis 渲染，且把 0 当作待确认值。
- 建议测试：只有 `state_of_health=87` 时按决定断言（unavailable 或 `healthBasis=VENDOR` 且文案不含“FCC/设计”）；`state_of_health=0` 单独加测试；只有 energy 对时断言 UI 不再声称 mAh FCC。

### P1-3 CPU 温度文案称“经过验证的映射”，实现是名称白名单且 HAL 路径不可达

- 位置：UI note `ui/LibreMonitorScreen.kt:298-300`；实现 `AdvancedCpuCollector.kt:10, 46-75`；`RootProtocol.kt:122-125`；解析器 `AdvancedCpuCollector.kt:19-44`。
- 现象：
  - UI 说明写“只采用经过验证的 CPU 温度传感器映射；不把任意 thermal zone…”。
  - 实际没有验证硬件映射：`isCpuSensorType` 只做 cpu/cpuss/cluster 名称白名单，然后取区域温度最大值；源码自己的 KDoc 与 source 文案都写“区域传感器，非验证过的核心结温”（`AdvancedCpuCollector.kt:10, 65, 72`）。
  - `RootProtocol.readFrame` 对 `thermalservice` 无条件返回 unavailable（“HAL dump completeness cannot be verified”），所以真实 Root 帧永远走不到 `parseHal`；HAL 解析器只在单测里被执行。
- 影响：详情页同时显示“经过验证的映射”和“非验证核心结温”，是自相矛盾的用户可见文案；CPU 温度的 HAL 能力实际上是死路径，UI/测试暗示的能力与生产路径不一致。
- 建议修复：先把 note 改成“CPU/CPUSS/Cluster 命名的区域传感器最高值，非验证核心结温”；HAL 要么实现可验证完整性（marker/完整读取）后真正交付，要么删除该分支及对应测试并在契约中说明不采用。
- 建议测试：把 sysfs 映射抽成可注入 reader 的纯函数，断言非白名单 zone 不参与 CPU 温度；为 `readFrame` 增加“thermalservice 被主动标记 unavailable”的测试，避免未来误以为 HAL 已生效；UI note 与 source 文案一致性测试（字符串级）。

### P1-4 电池收集器帧窗口 5s，与契约的 2s 有效窗口不一致

- 位置：`AdvancedBatteryCollector.kt:20-21, 42-44, 124-128`；契约 `docs/V2_CONTRACTS.md:35`；调用方 `AndroidSampler.kt:69-72, 86-99`；测试 `AdvancedBatteryCollectorTest.kt:294-310`。
- 现象：收集器自带 `FRAME_MAX_AGE_MS = 5_000`，并把这当作契约窗口；但契约写的是“拒绝超过单读包 2 秒有效窗口的帧”，`AndroidSampler` 也确实在 2s 处过滤。当前唯一生产调用方会挡住 2–5s 的帧，所以暂未产生用户可见假数，但这一层本身违反契约，测试还把 5s 当成正确边界。
- 影响：任何直接调用 `enrich` 的新路径（或未来重构）会接受 2–5s 的旧特权容量，属于数据正确性防线不一致。
- 建议修复：将窗口改为 2_000ms（或从共享常量派生），与契约/采样层一致；若产品决定 5s，则先改契约并写明理由。
- 建议测试：`now - 2_000` 用帧；`now - 2_001` 必须走普通读取且不得返回帧值；未来时间戳仍拒绝。

### P1-5 验证命令没有跑 app 单测；`RootProtocolTest` 期望值本身是错的

- 位置：验证记录 `docs/STATUS.md:10, 14`、`docs/validation-report.json:2, 6-13`；`app/src/test/.../RootProtocolTest.kt:23-31`；节点表 `RootProtocol.kt:28-44`。
- 现象：
  - 记录的构建命令是 `:core:test :app:assembleDebug :app:lintDebug`，只跑 24 个 core JVM 测试；v0.2 新增的 `AdvancedBatteryCollectorTest`、`AdvancedCpuCollectorTest`、`RootProtocolTest` 都不在这个命令里，没有通过证据。STATUS 仍是 0.1.0-dev，而 build 已 0.2.0-dev。
  - `RootProtocolTest.fixedAllowlistAndLimits` 断言 `478` 个节点，但 `nodes()` 实际是 `1(/proc/stat) + 256(cpufreq) + 192(thermal) + 26(power_supply) + 3(kgsl) + 1(thermalservice) = 479`。一旦跑 `:app:testDebugUnitTest`，这是一个确定失败。
- 影响：不能把 v0.2 的数据层称为“测试通过”；至少一个测试预期会红。
- 建议修复：把 `:app:testDebugUnitTest` 加入验证命令并实际运行；把 478 改成计算式或 479；同时补测 thermalservice 主动不可用、HAL 不生效的现状。
- 建议测试：上述计数测试 + 运行记录写入 STATUS/validation-report，避免再用 0.1 的证据描述 0.2。

### P1-6（次要）详情页丢弃可用读数的 `reason`，API34 公共循环 0 的语义未展示

- 位置：`ui/LibreMonitorScreen.kt:744-767`（`reason = if (quantity == null) ... else null`）、循环卡片 `514-516`；数据层 reason `BatteryCollector.kt:53-57`；sysfs 0 `AdvancedBatteryCollector.kt:197-200`。
- 现象：公共广播的 0 次是 available，详情只显示来源，不显示“0 仅表示系统报告值、不能推断健康”的 reason；sysfs 0 显示 “—” 且 reason 只出现在缺失原因。数据层的语义差别正确，但详情页没有把差异讲清楚。
- 建议修复：详情页对 available 读数也显示 `Reading.reason`（作为“数据说明”），或给循环次数增加专门的 0 语义文案。
- 建议测试：详情模型测试：公共 0 带“系统报告值”说明；sysfs 0 为 unavailable 且 reason 含 Linux ABI 语义。

## 测试评估

### CPU 分项（`core/src/test/.../CalculationsTest.kt`）

- 已覆盖且与实现一致：
  - 分项 reset 被总量增长掩盖：`maskedPerCoreResetIsMissingWhenTotalsGrow`（41-46）；
  - iowait 下降同样判缺失、不宣称重启（47-52）；
  - guest/guest_nice 不重复计入（15-20, 53-60）；
  - 重复聚合行/重复核行不污染邻居（71-83）；
  - 稀疏核、热插拔、溢出、组件结构变化（84-108）。
- 结论：这些新测试是合理的，覆盖了关键差分语义。
- 建议补充：首个重复行本身格式错误时名称被保守丢弃（当前是 `seen` 先占位，行为正确但无测试）；`coreIds` 与逐核列表索引对齐的采集器测试；组件长度相同但顺序/语义变化的负例。

### 电池（`AdvancedBatteryCollectorTest.kt`）

- 已覆盖：同目录 charge 对 → mAh + 80%；不跨 battery/bms 配对；energy 只做比值、不读 voltage_now；厂商 SoH 标注非 FCC；无效 FCC 不被厂商值替换；公共 0 vs sysfs 0；stale/future frame；有界读取；符号复制。
- 缺口（对应上面 P1）：101–150% 经 UI 的路径；2s（而非 5s）边界；vendor-only 的契约决定；`state_of_health=0`；frame sessionId 不匹配；`BatteryCollector` 的 `batteryHealthReading`/`batteryChargingSourceReading`/`batteryCycleCountReading` 没有直接测试（可做纯 JVM 断言）。
- 没有 `BatteryCollectorTest`；健康状态码/PLUGGED 位图/API 34 extra 判定属于可测纯逻辑，建议补。

### 其它

- 未运行 `:core:test` 或 `:app:testDebugUnitTest`；不能声称测试通过。
- 未安装 APK、未连设备、未读取真实节点；所有“真机行为”都未验证。

## 未验证 / 限制

- 静态审阅不能代替编译：本报告不声称 `:app:assembleDebug` 或 lint 通过。
- “首页无说明字”是 Compose 结构判断；没有截图/大字体/TalkBack 冒烟。
- Root 只读链路的接线是静态确认；没有执行 su、授权、超时或会话失效测试。
- 电池健康/循环的厂商语义（尤其 `state_of_health=0`）未在真实设备上取样验证。
