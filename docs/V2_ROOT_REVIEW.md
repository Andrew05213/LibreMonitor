# v0.2 Root / 特权读取静态审阅

只读审阅，未改任何实现。范围：`app/data/privileged/**`、`AndroidSampler`、`CpuCollector`、`AdvancedCpuCollector`、`GpuCollector`、`MonitorRuntime`、`MainActivity`、新增 `RootProtocolTest`。

## 方法与限制

- 只做静态阅读（含 core 侧的 `PrivilegedNodeFrame` / `Reading` / `CpuStatParser` / `SensorMath` 交叉核对），未执行 `su`、未连接设备、未运行 gradle 或单元测试。
- 因此下文所有“测试”均指应当补充/应当运行的用例，**不代表已运行通过**；文中的“必失败/必通过”只是按代码路径推导。
- 唯一的外部资料是一条公开脚本对真机 `dumpsys thermalservice` 文本形状的引用（`Current temperatures from HAL:`、`Cached temperatures`、`mValue` 已是 °C）：<https://raw.githubusercontent.com/google/perfetto/refs/heads/rfcs/media/0038/scripts/smb/_devstate.sh>。AOSP `ThermalService.java` 的直达抓取返回 404，故 `HAL Ready` 与分节的先后顺序未取得原文，见文末“待真机核对”。

## 结论

- **必须修正 2 项**（P1）：HAL 帧读数在协议层被无条件丢弃，导致 HAL 当前 CPU 温度整条路径不可达；新增 `RootProtocolTest` 的节点总数断言与实现差 1，测试会红。
- **可延后 8 项**（P2，多为一致性、可观测性与性能预算问题）。
- 未发现：任意命令/路径注入、`id` 非 0 被放行、后台自动 `su`、非用户动作触发授权、未授权时读取、计数器跨来源/跨会话复用、缺失数据被写成 0。
- 本阶段不作新功能要求；P2 中凡涉及行为变化者均标注“需 lead 决定”。

## 必须修正（P1）

### R-1 HAL `thermalservice` 读数被无条件判为不可用，HAL CPU 温度路径永不生效

- 位置：`app/src/main/java/org/libremonitor/app/data/privileged/RootProtocol.kt`，`readFrame()` 第 122–135 行，其中第 125 行：
  `node.key == "thermalservice" -> Reading.unavailable(node.key, "HAL dump completeness cannot be verified", ReadingStatus.ERROR)`
- 关联：`app/src/main/java/org/libremonitor/app/data/collectors/AdvancedCpuCollector.kt` 第 47 行
  `val hal = frame?.readings?.get("thermalservice")?.value?.let(::parseHal)`；`Reading.value` 恒为 `null`，故 `parseHal()` 在生产路径上**永远不会被调用**（只有 `AdvancedCpuCollectorTest` 直接调用它）。
- 触发：任意一次成功的 Root 帧。结果：`AdvancedCpuCollector.HAL_SOURCE` 永不出现，CPU 温度只能退化为 sysfs 区域传感器最大值；`V2_CONTRACTS.md` §特权读取里允许的 HAL 固定命令、§采样集成里的“先 HAL 后 sysfs”实际只有后半段。
- 附带成本：该节点在 `RootProtocol.kt` 第 43 行是**每帧都执行**的 `/system/bin/dumpsys thermalservice | head -c 65536 | base64`，是整包里最慢的一条命令（估计 dumpsys 自身数百毫秒级），花掉 2 秒预算后结果被抛掉。
- 最小修法（二选一，建议 A）：
  - A. 删除第 125 行的特殊分支，让 HAL 文本走通用解码（仍保留超限/UTF-8/NUL 拒绝）；完整性交给 `parseHal()` 已有的结构门（必须恰好一个 `Current temperatures from HAL:`、其后条目必须是完整 `Temperature{…}`、必须以新的 `…:` 分节收尾，见第 19–44 行）。这与“不得交付不完整帧”并不冲突：不完整的 HAL 文本会被 `parseHal` 判 `null`，于是回退 sysfs，不会产出错值。
  - B. 若确认“无法验证完整性”是最终决定，则同时删除第 43 行的 HAL 节点与 `AdvancedCpuCollector.parseHal()`/`HAL_SOURCE`，避免代码与文档宣称一个永不生效的能力（并与 `V2_CONTRACTS.md` 同步）。
- 测试：在 `AdvancedCpuCollectorTest` 或新的 `CpuCollectorTest` 里加两条——
  (1) 构造含完整 HAL 文本的 `PrivilegedNodeFrame`，断言 `CpuSample.temperatureC` 来源为 HAL、`sensors` 里 HAL 条目 `kind == CPU` 且不含 `mType=3`（skin）/battery；
  (2) 构造被截断的 HAL 文本（去掉 `Current cooling devices from HAL:` 收尾），断言 `temperatureC` 回退 sysfs 而不是采用 HAL。
  说明：若选 B，则改为断言 `thermalservice` 键不被请求。

### R-2 `RootProtocolTest.fixedAllowlistAndLimits` 的节点总数断言与实现不一致（差 1）

- 位置：`app/src/test/java/org/libremonitor/app/data/privileged/RootProtocolTest.kt` 第 25 行 `assertEquals(478, nodes.size)`。
- 实现：`RootProtocol.nodes()` 第 28–45 行按顺序产生
  `1`（`/proc/stat`）+`256`（`cpu0..255/cpufreq/scaling_cur_freq`）+`192`（`thermal_zone0..95` 的 `type`/`temp`）+`26`（`battery`/`bms` × 13 个字段）+`3`（kgsl）+`1`（`thermalservice`）**= 479**。
  同测试第 27/28/29/30/31 行的 256、192、26、cpu255、上限断言与实现一致，只有总数差 1。
- 触发：运行 `:app:testDebugUnitTest`（未运行，静态推导为确定性失败；若实际通过，说明我对 `nodes()` 的计数理解有误，以运行输出为准）。
- 最小修法：把断言改为 479，或改成从公开常量推导（`1 + MAX_CORES + 2 * MAX_ZONES + …`，`powerFields` 目前是 private，可在测试里只断言分段计数 + 总数），避免以后增删节点时再漂移。
- 测试：该断言本身即测试；建议 lead 运行 `./gradlew :app:testDebugUnitTest --tests 'org.libremonitor.app.data.privileged.RootProtocolTest'` 复核。

## 可延后（P2，建议按优先级处理）

### R-3 帧接收预算按“线上 base64 字节”计，而各源上限按“原始字节”计，两者不自洽

- 位置：`RootProtocol.kt` 第 14 行 `MAX_FRAME = 512 * 1024`、第 18–19 行 `PROC_LIMIT/HAL_LIMIT`、第 30 行 `head -c ${limit + 1}`、第 108 行 `Budget(MAX_FRAME)`。
- 事实：各节点原始上限之和 = 262 144 + 65 536 + 24 576 + 6 656 + 768 + 65 536 = 425 216 B；base64 后约 566 964 B，再加 479 条行标记（约 20 KB），超过 524 288 B 的接收上限。即“每个节点都在文档上限内”的合法帧仍可能被判 `frame exceeds limit`（`Budget.take()` 抛 `ProtocolException` → `readFrame` 走通用异常分支 `fail(...)`）。
- 触发条件：现实中不会发生（真实 sysfs 值只有几到几十字节，`/proc/stat` 约 3 KB，远低于上限）；这是单位混用导致的“理论上限不自洽”，不是当前可触发的数据错误。
- 最小修法：把节点上限定义为“线上（base64）字节”并在 `frameScript` 生成时按 `4*ceil(raw/3)` 分配，或把 `MAX_FRAME` 调整为 `ceil(4/3 × Σraw) + 行标记`，同时在 `V2_CONTRACTS.md` 写清 512 KiB 指的是哪一侧。
- 测试：构造一个各节点都取满上限的输入，明确期望“可交付”或“文档化拒绝”，不要保持两种说法并存。

### R-4 帧有效期出现两个不同窗口：采样器 2 s，电池增益 5 s

- 位置：`AndroidSampler.kt` 第 69–72 行与第 87–90 行用 `2_000L`；`AdvancedBatteryCollector.kt` 第 21 行 `FRAME_MAX_AGE_MS = 5_000L`、第 125–128 行 `isFrameFresh()`，并有 `AdvancedBatteryCollectorTest.frameFreshnessBoundaryIsInclusiveAtFiveSeconds` 固化 5 s。`V2_CONTRACTS.md` §特权读取要求“拒绝超出单读包 2 秒有效窗口的帧”。
- 现状影响：采样器先把帧卡在 2 s 内，`enrich` 的 5 s 实际不生效，所以目前**没有**旧帧被采用；但两处注释/测试与契约不一致，后续若有人绕过采样器直接调用 `enrich` 就会漏。
- 最小修法：`FRAME_MAX_AGE_MS = 2_000L`，并把对应测试边界改为 2 s（或在契约里把 `enrich` 明确列为“内部调用，窗口由采样器保证”）。属对齐性修改，需 lead 决定取哪一侧。

### R-5 用户拒绝与“su 缺失/不可用”无法区分，`DENIED` 只在超时/取消路径产生

- 位置：`RootReader.kt` 第 78–103 行 stderr 只做计数丢弃；第 265–274 行通用异常一律 `RootAccess.UNAVAILABLE`；`DENIED` 仅出现在第 128–130、160、236、248–252 行。
- 触发：用户在 Root 管理器里点“拒绝”，su 管理器通常写 stderr 后退出（具体文案因实现而异），stdout EOF → 通用异常 → 状态显示“不可用”并建议重试，而非“未授权”。
- 影响：状态语义与 `RootAccess` 契约不完全对应（不影响安全性，也不会重试）。
- 最小修法：stderr 保留前若干字节（仍受 `MAX_STDERR` 限制），在 EOF/退出异常时若命中 `Permission denied`/`denied` 则映射 `DENIED`；或在 `V2_CONTRACTS.md` 里明确“拒绝与不可用统一为 UNAVAILABLE”。

### R-6 Root 状态在采样过程中变化时，CPU 计数基线会被多余地重建两次

- 位置：`AndroidSampler.kt` 第 74–76 行把 `"transition:A:B->C:D"` 写进 `cpu.rootIdentity`；`CpuCollector.kt` 第 30 行 `sameBaseline` 比较该字符串、第 39 行写回 `previousRootIdentity`。
- 触发：授权/停用恰好发生在某次 `sample()` 的 `beforeRoot` 读取与 `readFrame()` 之间（轮询 1 s、授权异步，属常见时序）。本次身份为 `transition:…`，下一次变为普通 `GRANTED:1`，字符串不等 → 即使来源与会话都没变，基线仍再重建一次。
- 影响：每次 Root 状态切换后额外丢一个采样间隔的 CPU 使用率（表现为一次“首次有效样本，需下一次计数计算使用率”），不是错值。
- 最小修法：身份只用当前状态元组（`access:sessionId`）；切换检测已经由 `source`/`session` 比较覆盖（授权必然使 `sessionId` 变化，停用必然使来源变 `basic`），`transition:` 前缀可直接删除；或在 `sample()` 里把它只用于“本次”而不写入 `previousRootIdentity`。
- 测试：对 `CpuCollector` 加纯 JVM 用例：root→root 连续两次采样，第二次应给出 `usagePercent`（当前实现因身份字符串变化会返回不可用）。

### R-7 `outstandingWorkers` 计数没有超时兜底，一次永久阻塞会让 Root 再也无法请求

- 位置：`RootReader.kt` 第 44、136–145、166–189、199–207 行。`requestAuthorization()` 在 `prior == null && outstandingWorkers.get() != 0` 时直接返回 `false` 并置 `UNAVAILABLE`（“Previous root session is still closing”）。
- 触发：某个 operation/start/stderr worker 因 su 侧永不读取 stdin、或 `process.destroyForcibly()` 未能释放阻塞读而一直不返回 `finally`。此后每次“手动重试”都被拒，只能杀进程恢复。
- 影响：低概率、但结论是“不可自愈”，且状态里没有任何“卡住”的诊断信息。
- 最小修法：给 `outstandingWorkers` 一个“陈旧判定”（记录最后一次增量时间，超过 `authTimeoutMs` 仍非 0 时允许新请求并记录诊断），或在 `requestRoot` 的错误文案里明确“请重启应用”。属加固，需 lead 决定是否纳入本阶段。

### R-8 单帧成本与 2 s 预算偏紧：479 个节点、每次约 350–1900 次 fork、脚本约 92 KB

- 位置：`RootProtocol.nodes()` 第 28–45 行与 `frameScript()` 第 48–68 行；`RootReader.kt` 第 292–296 行整包一次 `withTimeout(readTimeoutMs)`。
- 事实（静态估算）：每节点形如 `( head -c N 'path' ) 2>/dev/null | base64 | tr -d '\n'`，成功执行时约 4 次 `fork+exec`；典型 8 核 + 25 个热区设备约 350+ 次 fork（热区/供电节点不存在时也各要 1 次），脚本本身约 92 KB（超过管道容量，写入必然与 shell 读脚本交错）。此外 `dumpsys thermalservice` 每次都跑。
- 影响：慢设备/多热区设备上单帧可能接近或超过 `readTimeoutMs = 2000`。届时 `timed()` 的取消分支（第 123–135 行）会置 `ERROR` 并销毁会话，随后只能靠用户“手动重试”，表现为 Root 反复失效、采样被拖慢 2 s。当前无真机数据，无法判定是否已触发（**需实测**，本阶段不执行）。
- 最小修法：压缩整包——用单进程 `awk`（`getline < path`）或一次 `cat` 多节点 + 单一 `base64`，把 fork 降到个位数；只对实际存在的 cpu/热区/供电节点发命令（`[ -e ]` 已用于 cpu，可推广）；把 `gpubusy` 等不被消费的节点移出白名单以减小帧。这是实现优化，不是新功能。
- 测试：真机测单帧耗时分布（P50/P95）与超时率；在拿到数据前不要把“Root 可用”写成已通过。

### R-9 `frameScript` 未固定 `PATH`，基础命令缺失时会得到“全空但格式完整”的帧

- 位置：`RootProtocol.frameScript()` 第 48–68 行依赖 `head`/`base64`/`tr`，而 `probeScript()` 第 72 行专门 `PATH=/system/bin; export PATH;`；`readFrame()` 对全空节点只给出“Missing, denied or empty”，`RootReader` 仍保持 `GRANTED`。
- 触发：su 环境 `PATH` 异常或 `base64` 不可用（少见）。结果：界面显示 Root 已启用，但所有特权指标不可用，且没有任何“环境异常”诊断。
- 最小修法：`frameScript` 同样固定 `PATH`；或在 `readFrame` 发现“键集完整但全部不可用”时返回会话级错误（`ERROR` + 原因），避免“已启用但全空”。
- 测试：伪造一个全空但 marker 完整的帧，断言将其视为环境失败而不是逐项“缺失”。

### R-10 各源上限存在 1 字节的口径差，`V2_CONTRACTS.md` 的“256 KiB/64 KiB”未精确对应

- 位置：`RootProtocol.kt` 第 18–19、31–43 行：`/proc/stat` 上限 262 143（`head -c 262144`）、`type`/`temp` 127（`head -c 128`）、HAL 65 535（`head -c 65536`）；而普通读取 `SafeReadings.readBounded` 用 262 144 / 128 / 256。`CpuCollector` 第 27 行与 `AdvancedBatteryCollector` 第 19 行分别用 262 144 与 256。
- 影响：只影响“刚好等于契约上限”的边界（会按“截断”拒收），不会产生错值；同一条数据走 Root 与走普通读取的上限相差 1 B，属口径不统一。
- 最小修法：统一为“节点内容最多 N 字节”的单一常量并在契约里写明（例如 `head -c $((N+1))` 判截断），顺带消除这一字节差。

## 已核对且正确（不重复列为问题）

- **持久 `su` 仅由用户动作触发**：`RootReader()` 构造不启动进程（`RootReader.kt` 第 31–34 行只保存 lambda），唯一的 `ProcessBuilder("su")` 在 `start()`（第 169 行）里，而 `start()` 只由 `requestAuthorization()`（第 195 行）调用；全仓库只有 `MonitorRuntime.requestRoot()`（第 142–168 行）调用它，且先过 `visible.value` 前台门槛 + UI 二次确认对话框（`LibreMonitorScreen.kt` 第 605–624 行）；`AndroidSampler.sample()` 明确只读帧、不请求授权（第 61 行注释与实现一致）。无 boot/后台入口。
- **id 必须为 0**：`verifyProbe()` 第 78–88 行要求 `UID:0`、`CapEff` 含 bit1/bit21、`uid_map` 为单行 `0 0 4294967295`；proot/容器式 uid_map 会被拒（与 `docs/V2_TESTING.md` 第 11 行的要求一致）。
- **不注入**：`frameScript` 第 48–50 行 `require(id 为 32 位 hex)` + `require(nodes == nodes())`，命令全部由常量拼出，路径用单引号包裹；节点数据以 base64 传输，不参与 shell 语法。未发现调用方可控的命令/路径进入脚本的通道。
- **拒绝/超时/关闭/迟到**：授权超时 20 s 锁存在 `DENIED`（第 247–255 行），失败不重试且无自动 `su`；`readFrame` 遇到超时/超限/缺 marker/截断/异常一律不交付帧（第 292–314 行），`timed()` 取消时销毁会话（第 123–135 行），同时消除管道中残留字节导致下一帧错位的风险；帧在 `AndroidSampler` 侧再次校验 `GRANTED` + `sessionId` + 单调时效（第 69–72、87–90 行），关闭/替换会话的迟到帧会被丢弃；`close()` 幂等（`Session.closed` CAS），并分别释放 destroy/stdin/stdout/stderr（第 51–69 行）。
- **帧完整性**：BEGIN/END marker、逐节点 `LM:<id>:<index>:` 顺序校验、逐行只允许 ASCII 且禁 CR（第 90–101 行）、单帧预算（第 108 行）、逐节点解码长度与 `limit+1` 截断判定（第 116–123 行）、UTF-8 严格解码 + NUL 拒绝（第 127–132 行）。stderr 有 16 KiB 上限并在越限时关闭会话（第 88–92 行）。
- **截断不会被误解析**：所有“超限”都是“整项不可用”，不会把半行温度/频率/`/proc/stat` 交给解析器；`readBounded` 同样多读 1 B 判超限（`SafeReadings.kt` 第 68–86 行）。
- **基本读数不被 Root 授权拖死**：授权期间 `operation` 被占用，`readFrame()` 的 `tryLock` 立即返回 `null`，基本采样继续；单帧读取本身有 2 s 上限。
- **单采样与计数器基线**：只有 `MonitorRuntime` 一个循环调用 `AndroidSampler`（第 79–107 行），`AndroidSampler` 每轮只取一帧；`CpuCollector` 在来源切换（root/basic）、会话变化、解析失败、前后时间戳非单调时都会重建基线（第 30、36–39、48–50 行），`CpuUsage.percent` 还额外拒绝计数回退（`Calculations.kt`）。
- **缺失值不置零**：频率/温度/容量/循环/健康/GPU 全部用 `Reading.unavailable` 表达，`cycle_count=0` 按 Linux ABI 视为未知（`AdvancedBatteryCollector.kt` 第 193–203 行），`GOOD` 不被当作 100%（第 108–111 行）。
- **HAL 段选择逻辑本身正确（只是不可达，见 R-1）**：`parseHal()` 要求 `HAL Ready: true` 唯一、`Current temperatures from HAL:` 在 Ready 之后且唯一、只接受 `mType=0`（CPU），因此不会把 `Cached temperatures`、skin(`mType=3`)、battery 当作当前 CPU；截断的文本会被末尾“必须出现新分节”这一条拒绝。
- **GPU 不假造占用**：`GpuCollector` 只把 `gpu_busy_percentage` 当驱动直接上报的百分比（0–100 之外/非数字→不可用），**没有**把 `gpubusy` 的两个计数当占用率（`GpuCollector.kt` 第 8、22–25 行）；`gpubusy` 目前只是每帧被读一次但从不消费（可随 R-8 一起从白名单裁剪，属契约内节点，非错误）。

## 已知缺口（非本阶段 bug，不建议现在做）

- `GpuSample.name` / `temperatureC` 从未填值（白名单里没有可靠的 GPU 名称/温度节点，`thermal_zone` 里 `kind=GPU` 的传感器只在 `CpuSample.sensors` 里）；UI 上的 GPU 名称/温度会一直是“—”。
- Root 会话在 UI 隐藏且无持续记录/悬浮窗时也不会自动关闭（契约只要求“用户动作触发授权”和“close() 终止会话”）；是否要在无人使用时自动收口，需要 lead 决定，属行为变化。

## 待真机核对（静态无法判定，不写成 bug）

1. `dumpsys thermalservice` 真实文本里 `HAL Ready: true` 是否出现在 `Current temperatures from HAL:` **之前**（`parseHal` 第 24–26 行要求此顺序）。若顺序相反，即使修好 R-1 也会一直回退 sysfs。
2. 温度行字段名是否为 `mStatus`（正则见 `AdvancedCpuCollector.kt` 第 14–16 行）；若是 `mThrottlingStatus` 等，R-1 修复后仍会全部解析失败。
3. 单个合法满帧的真实耗时与超时率（R-8），以及 `size` 上限在真机上是否真的需要 256 B。
4. 本机有没有可用 `su`：按规则本阶段未执行，任何“Root 可用”都不应在报告中出现。

## 建议的最小验证清单（供 lead，本次均未执行）

1. `./gradlew :app:testDebugUnitTest --tests 'org.libremonitor.app.data.privileged.RootProtocolTest'` —— 复核 R-2（预期当前失败）。
2. R-1 修复后补 `CpuCollector`/`AdvancedCpuCollector` 集成用例（完整 HAL→用 HAL；截断 HAL→回退 sysfs）。
3. R-6 修复后补 `CpuCollector` 纯 JVM 用例（连续两次 root 采样第二次有值）。
4. 真机：`dumpsys thermalservice` 原文、单帧耗时分布；未获用户授权前不执行 `su`。
