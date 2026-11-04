# Current validation status — 0.4.0-dev

Updated 2026-11-03. This is a development prototype, not a production or cross-device validated release.

## Current build

- Version `0.4.0-dev`, versionCode `12`, application ID `org.libremonitor.app`.
- Build: `bash scripts/build-local.sh :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`.
- Result: **BUILD SUCCESSFUL**.
- Tests: **77 core + 36 app JVM tests**, 0 failures, 0 errors, 0 skipped.
- Android Lint: **0 errors, 7 warnings**, all newer-dependency suggestions; no baseline or suppression.
- GitHub Actions: the 0.3.1 run [41625409166](https://github.com/Andrew05213/LibreMonitor/actions/runs/41625409166) passed; the 0.4.0 commit requires a new run (not yet claimed as passed here).
- Debug APK SHA-256 `67cd9426b191460be00a22d85b7b17f417a6fccf024b469da0406a039058114e`; local deliverable `LibreMonitor-0.4.0-dev.apk`.
- APK is debug-signed with the same development certificate used by earlier builds; no production signing exists.

## Device smoke evidence

On a OnePlus PJE110, Android 17 (API37), KernelSU:

- App installed and opened after the Android ICU regex fix.
- Root one-shot backend reported `已启用 · Root`; notification, overlay app-op and battery-optimization whitelist were granted through the confirmation-gated action.
- Overview showed CPU usage around 42.6%, CPU temperature 64.8°C and populated CPU0–CPU7 usage/frequency rows.
- Battery page showed 100%, 4.44V, -1mA, -0.00W, 34.5°C, GOOD, capacity health 82.4%, design 5500mAh, full 4530mAh, remaining 4376mAh, 883 cycles and AC.
- The icon-led UI was visually inspected on this device.

Machine-readable evidence for 0.4.0 is in [validation-report-v040.json](validation-report-v040.json); 0.3.1 evidence remains in [validation-report-v031.json](validation-report-v031.json).

These are point-in-time smoke observations. They are not calibration data, not proof of cross-device support, and not a claim that every value is accurate. See [V027_DEVICE.md](V027_DEVICE.md) and [V030_UI.md](V030_UI.md).

## Implemented

- Compact icon dashboard: CPU hero/gauge, per-core bars, memory/Swap, network, storage, GPU when available.
- Battery detail page with voltage/current/power/temperature/health/capacity/cycle readings, real-history chart, CSV export and confirmed clear.
- Bluetooth card using public APIs: adapter state/name/address, LE support, connected profile count, paired devices, type, Class/services, known profile mapping, and explicit unavailable reasons for RSSI/version/battery.
- Optional Root session: one-shot `su -c` backend compatible with the tested KernelSU device; compact fixed read-only frame; explicit user confirmation; no arbitrary shell.
- One-key permission grant after Root is active: notification permission, overlay app-op, battery-optimization whitelist and Bluetooth-connect permission, all app-scoped fixed commands.
- Opt-in battery history, foreground-service notification, draggable overlay, no internet permission, no boot receiver, no analytics.
- SQLite schema version 1 and seven-column CSV preserved.

## Not verified / not implemented

- No production signing, Play Store submission, Play policy review or release artifact.
- No cross-device/ROM/KernelSU compatibility matrix, no light-theme/large-font/TalkBack/320dp visual acceptance.
- No measured monitoring overhead or OEM background-survival test.
- Shizuku backend, cross-app FPS, GPU utilization coverage, modem capture and widgets remain unimplemented.
- CPU temperature is a named HAL/regional CPU sensor, not a verified per-core junction temperature; battery power is an uncalibrated battery-terminal estimate.
- Root denial may be reported as unavailable; a stuck cleanup worker may require restarting the app.

## Historical records

v0.1 and v0.2 snapshots/reviews are under [docs/archive/](archive/). They are retained for provenance, not as current claims.
