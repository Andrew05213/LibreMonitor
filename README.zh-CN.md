# LibreMonitor

独立的离线 Android 系统监控工具，Apache-2.0 许可。

当前开发版本：**0.4.1-dev**（versionCode 12）。支持 Android 8.0+（API26），使用 Kotlin、Jetpack Compose Material3。无账号、无分析 SDK、无联网权限、无开机自启。

[English](README.md) | **简体中文**

## 当前功能

- 图标化仪表盘：CPU 占用环形仪表、CPU 温度、逐核占用/频率进度条、内存/Swap、网络和存储卡片。
- 电池页：电量环形仪表、电压、电流、功率、温度、健康状态、容量健康度、设计/满充容量、剩余电荷、循环次数和充电源。
- FPS 与 1% low：从 SurfaceFlinger 前台图层帧时间计算，在仪表盘和悬浮窗显示；没有 Root/找不到图层时明确不可用。
- 蓝牙页卡：适配器状态/名称/地址、LE 支持、已连接 Profile 数、已配对设备、经典/低功耗/双模类型、Class/服务和已知 Profile 映射（A2DP/HFP/AVRCP/HID/HOGP/SPP 等）；核心版本、逐设备 RSSI 和电量在系统不公开时明确显示不可用。
- 可选、由用户主动触发的 Root 会话；可开启“打开应用时自动恢复 Root”，但仅限前台、仍需 Root 管理器批准，不会开机自启或后台提权。当前实现使用一次性固定 `su -c` 命令和紧凑只读白名单，不开放任意 shell；是否可用取决于设备、ROM 和 Root 管理器。
- Root 启用后，经单独确认可“一键授权”四项仅属于本应用的权限：通知、悬浮窗、电池优化白名单和蓝牙连接。
- 可选的本地电池历史、真实数据趋势图、7 天/50,000 条限制，以及通过系统文件选择器导出 CSV。
- 可拖动轻量悬浮窗，常驻前台通知和停止入口。
- 缺失、被拒绝、过期或无效的读数显示为 `—`，不会伪造为 0。

## 尚未实现

- Shizuku 后端、跨应用 FPS、modem 抓取、桌面小组件和完整多语言界面。
- CPU 温度来自 HAL 或带 CPU 标签的区域传感器，不是经过验证的逐核结温。
- 电池健康度只表示同一电池、同一单位下的满充/设计容量比，不能替代实验室健康检测。
- 电池功率是未校准的电池端 `V×I` 估算，不是充电器或 SoC 功耗。
- 没有生产签名、正式 Release 或 Play 商店发布。

## 构建

保守基线：JDK17、Gradle8.9、AGP8.7.2、Kotlin/Compose 编译器2.1.21、compile/target SDK35。

普通 Linux/macOS/Windows Android SDK 环境：

```sh
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

在这台 Android/ARM64 proot 主机上，需要项目本地工具链；请先读 [docs/BUILDING.md](docs/BUILDING.md)。辅助脚本会下载较大的工具，并使用有明确来源说明的第三方静态 ARM64 aapt2，不要在不了解来源的其他机器上直接执行。

Debug APK 输出：`app/build/outputs/apk/debug/app-debug.apk`。Debug 签名仅供开发；应用不会自动安装。

## 文档

- [当前状态与真机烟测](docs/STATUS.md)
- [更新日志](docs/CHANGELOG.md)
- [文档索引](docs/INDEX.md)
- [架构说明](docs/ARCHITECTURE.md)
- [构建说明](docs/BUILDING.md)
- [测试清单](docs/TESTING.md)
- [组件复用与许可证](docs/REUSE.md)
- [v0.2 Root/设备记录](docs/V027_DEVICE.md)
- [v0.3 图标界面记录](docs/V030_UI.md)
- [v0.1/v0.2 历史归档](docs/archive/)

## 隐私与测量边界

所有数据保存在应用私有存储中；Android 备份/迁移已禁用。导出的 CSV 可能包含使用和充电规律，请谨慎分享。不收集蜂窝标识、SSID/BSSID、进程列表或凭据。清除历史需要确认。

部分 Android 版本/ROM 会拒绝 `/proc/stat` 或 CPU 频率节点。基础模式下没有全局 CPU 读数是正常情况，不会拿应用自身 CPU 或屏幕刷新率冒充。TrafficStats 是整机累计计数，不是单应用或计费数据。

源码能编译不等于设备测量已经验证；把某个读数当作可信数据前，请先阅读 [STATUS.md](docs/STATUS.md)。
