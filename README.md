# LibreMonitor

**English** | [简体中文](README.zh-CN.md)

An independent, offline-first Android system monitor. Apache-2.0.

Current development version: **0.7.1-dev** (versionCode 18). Android 8.0+ (API26), Kotlin, Jetpack Compose Material3. No account, analytics, internet permission or boot receiver.

See [0.7.1 Root transport notes](docs/ROOT_FRAME_BATCHING.md) for diagnostic findings and batched node reads.

## Current prototype

- Native green Material3 three-tab navigation: Functions / Overview / Settings, with continuous overlay controls, identified Oplus power-unit support and FPS queries outside the fast overview path. See [0.7.0 development notes](docs/POWER_AND_NATIVE_UI.md).

- Icon-led dashboard with CPU usage gauge, CPU temperature, per-core usage/frequency bars, memory/Swap, network and storage cards.
- Battery page with level gauge, voltage, current, power, temperature, health status, capacity health, design/full-charge capacity, remaining charge, cycle count and charging source.
- Bluetooth card: adapter state/name/address, LE support, connected profile count, paired devices, classic/LE/dual type, Class/services, known profile mapping (A2DP/HFP/AVRCP/HID/HOGP/SPP/…), plus explicit unavailable states for core version, per-device RSSI and battery.
- FPS and 1% low from SurfaceFlinger frame timestamps for the foreground layer, shown in the dashboard and overlay when Root is available; unavailable when no layer/frame data can be read.
- Optional, user-initiated Root session. A setting can attempt foreground auto-restore when the app is opened; it still needs Root-manager approval and never boots or requests root in the background. The tested implementation uses a one-shot fixed `su -c` command with a compact read-only allowlist, not an arbitrary shell. It is device/ROM/Root-manager dependent.
- After a separate confirmation, Root can grant this app only: notification permission, overlay app-op, battery-optimization whitelist and Bluetooth-connect permission.
- Opt-in local battery history, real-history chart, seven-day/50,000-row limits, CSV export through the system document picker.
- Compact two-by-two overlay showing only CPU, GPU, memory and FPS, with drag, return-to-app and independent close controls.
- Opt-in performance sessions: record 14 usage/frame/frequency/temperature/battery-terminal metrics in the background, then inspect per-session curves, valid-sample means and extrema. Saved samples remain reviewable after a process interruption.
- Refresh choices from 100ms to 10s; actual throughput depends on collection cost. Performance recording has a persistent notification, pauses on screen-off and resumes on screen-on.
- Missing, denied, stale or invalid readings remain unavailable (`—`) rather than becoming zero.
- Custom overlay content/order (12 fields), one/two columns, font size, background opacity and collapsible translucent edge-arrow docking.
- Exact SoC catalog lookup and prime/performance/efficiency core groups; seven chip families including Snapdragon 8 Gen 2. Unknown core identities remain explicit.
- Bluetooth PAN network throughput, expanded protocol capabilities and an explicit 30s foreground BLE scan for supported AirPods/Pro battery advertisements. Coarse readings expire after 60s; rotating addresses are not name-matched to paired devices.
- Mobile-network fields, hotspot capabilities/filtered Root SoftAP diagnostics, power-saving and thermal system state. Scan/location and phone-state permissions are optional manual grants; no location, phone number or IMEI is collected. See [device extensions](docs/DEVICE_EXTENSIONS.md).

## Not implemented

- Shizuku backend, cross-app FPS, modem capture, widgets and a full multi-language UI.
- CPU temperature is a HAL or CPU-labelled regional sensor, not verified per-core junction temperature.
- Battery health is a same-battery, same-unit full-charge/design ratio; it is device-dependent and not a lab health certificate.
- Battery power is an uncalibrated terminal `V×I` estimate, not charger/SoC power.
- Only debug-signed development prereleases; no production signing or Play Store submission exists.

## Build

Conservative baseline: JDK17, Gradle8.9, AGP8.7.2, Kotlin/Compose compiler2.1.21, compile/target SDK35.

Normal Linux/macOS/Windows Android SDK host:

```sh
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

This Android/ARM64 proot host needs a project-local JDK/SDK/Gradle toolchain; read [docs/BUILDING.md](docs/BUILDING.md). The helper downloads significant tools and uses a disclosed third-party static ARM64 aapt2. Do not run it blindly on another host.

Debug APK output: `app/build/outputs/apk/debug/app-debug.apk`. Debug signing is for development only; the app is not automatically installed.

## Documentation

- [Current status and device smoke evidence](docs/STATUS.md)
- [Changelog](docs/CHANGELOG.md)
- [Documentation index](docs/INDEX.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Build instructions](docs/BUILDING.md)
- [Test checklist](docs/TESTING.md)
- [Component reuse and licenses](docs/REUSE.md)
- [v0.2 Root/device notes](docs/V027_DEVICE.md)
- [v0.3 icon UI notes](docs/V030_UI.md)
- [Historical v0.1/v0.2 records](docs/archive/)

## Privacy and measurements

Everything is stored in app-private local storage. Android backup/transfer is disabled. Exported CSV can contain usage and charging patterns; share it deliberately. No cellular identifiers, SSID/BSSID, process lists or credentials are collected. Clear history requires confirmation.

Some Android versions/ROMs deny `/proc/stat` or CPU-frequency nodes. A missing global CPU metric is expected in basic mode and is not replaced with app CPU or refresh rate. TrafficStats counters are aggregate device counters, not per-app or billing figures.

A source build is not proof of on-device correctness. Read [STATUS.md](docs/STATUS.md) before treating a measurement as validated.
