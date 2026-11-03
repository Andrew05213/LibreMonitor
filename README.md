# LibreMonitor

An independent, offline-first Android monitor. Provisional name; Apache-2.0.
Android 8.0+ (API26), Kotlin + Compose Material3. No account, analytics, internet permission or boot receiver. Root access is optional and user-initiated: bounded reads plus a confirmation-gated one-click grant for this app's notification, overlay and battery-whitelist entries.

## v0.2 prototype

- Device information and best-effort CPU/core frequency, RAM/swap, battery, aggregate network and data-partition storage readings.
- Missing/denied metrics retain a source and reason, never a fabricated zero. CPU temperature uses recognisable HAL CPU sensors or CPU-labelled sysfs zones, not verified per-core junction temperatures.
- Optional user-initiated Root session reads a bounded fixed allowlist. After a separate confirmation, the same session may run three fixed app-scoped commands: grant this app's notification permission, allow its overlay app-op, and add it to the battery-optimization whitelist. No arbitrary command interface, other-app changes or kernel/security writes. Availability depends on the device and Root manager. GPU driver metrics and battery capacity ratios appear only when supported; they are not synthetic defaults.
- Opt-in battery history: one sample/minute, seven-day retention, maximum50,000 rows; recent360 rows charted. Public battery-current sign is not OEM-calibrated. Power is battery-terminal `V×I`, not charger or processor power.
- Draggable lightweight overlay with open/stop controls and a persistent foreground-service notification.
- Streaming CSV export through the system document picker; no broad storage permission.
- One sampling engine feeds UI, history and overlay. UI-only monitoring stops when the app is no longer visible. Continuous recording/overlay requires user opt-in and a visible notification. System/OEM power management may still stop it; no wake lock or silent restart.

**Not implemented:** Shizuku mode, cross-app FPS, CPU temperature calibration, modem capture, widgets, i18n beyond the Chinese prototype UI. Capacity health is shown only as a same-battery, same-unit full/design ratio; support is device-dependent.

## Build

Conservative baseline: JDK17, Gradle8.9, AGP8.7.2, Kotlin/Compose compiler2.1.21, compile/targetSDK35. This is not a claim of latest versions or current Play Store submission eligibility.

On a normal Linux/macOS/Windows Android SDK host:

```sh
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

On this ARM64 Android/proot host, read `docs/BUILDING.md`. The project-local helper downloads significant tools, requires explicit Android SDK license acceptance, and uses a disclosed third-party static ARM64 aapt2. Do not execute it blindly on another host.

Debug APK output: `app/build/outputs/apk/debug/app-debug.apk`. Debug signing is for development only. No production signing keys are included, and the app is not automatically installed.

## Development

`core/` is pure Kotlin with counter parsing, monotonic-rate math, nullable readings and CSV tests. `app/` owns public Android collectors, local persistence, a shared runtime, Compose UI, and the opt-in foreground/overlay service.

Read `AGENTS.md`, `docs/CONTRACTS.md` and `docs/REUSE.md` for boundaries, licenses and components considered. No monitoring-app source or brand assets were copied; OpenMonitor was inspected only as a functional reference.

## Privacy and measurements

Everything is stored in app-private local storage. Android backup is disabled. Exported CSV can contain usage/charging patterns; share it deliberately. No cellular identifiers, SSID/BSSID, process lists, or credentials are collected. Clear history requires confirmation in the app.

Some Android versions/ROMs deny `/proc/stat` or CPU-frequency nodes. A missing global CPU metric is expected in basic mode, not a reason to display app CPU or refresh rate under a misleading label. Aggregate TrafficStats counters are not per-app or billing measurements and VPN/interface accounting can differ. For every feature, test the actual device/ROM/permission combination.

## Validation status

See `docs/STATUS-V02.md` for v0.2 commands, results, artifacts, and limitations; `docs/STATUS.md` records v0.1. A source build is not proof of on-device correctness or background reliability.
