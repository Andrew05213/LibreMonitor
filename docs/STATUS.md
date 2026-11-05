# Current validation status — 0.5.0-dev

Updated 2026-11-05. This is a development prototype, not a production or cross-device validated release.

## Current build

- Version `0.5.0-dev`, versionCode `15`, application ID `org.libremonitor.app`.
- Debug-signed development prerelease: [v0.5.0-dev](https://github.com/Andrew05213/LibreMonitor/releases/tag/v0.5.0-dev), with APK, SHA-256 and the validation report.
- Local offline validation passed **98 core + 78 app JVM tests (176 total)** with zero failures/errors/skips; debug APK packaging passed; Lint **0 errors, 1 existing HardwareIds warning**. The Android database instrumentation test compiled but was not executed. See [validation-report-performance-sessions.json](validation-report-performance-sessions.json) for the commands, lint counts and APK checksum. Previous 0.4.2 evidence below is historical.
- No phone installation, Android SQLite device test, gameplay capture, measured 10Hz throughput or overlay visual acceptance has been performed for this version.

## Performance sessions and overlay

- The overlay contains CPU, GPU, system memory and FPS only. Its close control hides the panel; recording has independent explicit start/end controls in the Performance tab and a persistent notification with Stop All.
- Sessions retain 14 metric readings, sources, availability states and failure reasons. Review shows sample means/minima/maxima, valid counts and a bounded timeline preview; missing readings break the curve rather than becoming zero.
- Screen-off pauses performance sampling. Reopening after a process death marks an unfinished session interrupted; saved points remain reviewable and recording is not restarted automatically.
- Separate `performance.db` schema v1, 20-session / 100,000-total-sample retention, and an explicit per-session 100,000-sample stop. Battery schema v1 and seven-column CSV are preserved. See [PERFORMANCE.md](PERFORMANCE.md).

## Unreleased faster sampling

- Added 100ms, 200ms and 500ms refresh choices, retaining 1s, 2s (default), 5s and 10s. Saved settings accept 100ms–10s.
- Live/overlay sampling targets the selected start-to-start interval by subtracting collection time. Slow reads may exceed that interval; samples do not overlap or catch up missed ticks. Background battery recording still defaults to 60s.
- Settings boundary/cadence and recording-demand JVM regressions passed as part of the 176-test build above. No phone installation or measured 100ms throughput claim.

## Historical 0.4.2 background optimization

- Based on PR #1 head `79efe22`; full foreground collection, reduced interactive overlay collection, battery-only background recording, and idle/screen-off suspension share one loop. Default background history cadence is 60s rather than the 2s UI interval.
- The original 0.4.2 overlay frame read 1 fixed CPU node instead of 102; 0.5.0 adds the three existing KGSL nodes for the visible GPU metric. Battery-only history launches no Root frame/FPS processes. Background inserts no longer reload the history list. See [PERFORMANCE.md](PERFORMANCE.md) for the current modes.
- Local offline Android validation passed: **85 core + 62 app JVM tests (147 total)**, zero failures/errors/skips; debug APK packaging passed; Lint **0 errors, 1 existing HardwareIds warning**. See [validation-report-performance.json](validation-report-performance.json) for the exact command and APK SHA-256. No CI run, phone performance measurement or installation has been performed for these changes.

## Historical 0.4.2 GPU correction

- KGSL percentage values with `%` now parse correctly; existing `gpubusy` readings provide a validated busy/total fallback. GPU tiles remain visible for source/status/reason inspection when unavailable.
- FPS discovery now precedes the Root node frame, preserving the node freshness budget; old FPS results expire if subsequent collection is slow. GPU node count and background sampling policy are unchanged.
- Local offline Android validation passed **85 core + 78 app JVM tests (163 total)**, zero failures/errors/skips; debug APK packaging passed; Lint **0 errors, 1 existing HardwareIds warning**. See [validation-report-gpu.json](validation-report-gpu.json). No phone installation, GPU-load comparison or non-KGSL compatibility claim. See [GPU.md](GPU.md).

## Device smoke evidence

On a OnePlus PJE110, Android 17 (API37), KernelSU:

- App installed and opened after the Android ICU regex fix.
- Root one-shot backend reported `已启用 · Root`; notification, overlay app-op and battery-optimization whitelist were granted through the confirmation-gated action.
- Overview showed CPU usage around 42.6%, CPU temperature 64.8°C and populated CPU0–CPU7 usage/frequency rows.
- Battery page showed 100%, 4.44V, -1mA, -0.00W, 34.5°C, GOOD, capacity health 82.4%, design 5500mAh, full 4530mAh, remaining 4376mAh, 883 cycles and AC.
- The icon-led UI was visually inspected on this device.

The above device observations predate the 0.4.2 FPS fix. Historical machine-readable evidence is in [validation-report-v041.json](validation-report-v041.json); 0.4.0 and 0.3.1 evidence remain in their versioned reports.

These are point-in-time smoke observations. They are not calibration data, not proof of cross-device support, and not a claim that every value is accurate. See [V027_DEVICE.md](V027_DEVICE.md) and [V030_UI.md](V030_UI.md).

## Implemented

- Compact icon dashboard: CPU hero/gauge, per-core bars, memory/Swap, network, storage, and GPU tiles with unavailable-state details.
- Battery detail page with voltage/current/power/temperature/health/capacity/cycle readings, real-history chart, CSV export and confirmed clear.
- Bluetooth card using public APIs: adapter state/name/address, LE support, connected profile count, paired devices, type, Class/services, known profile mapping, and explicit unavailable reasons for RSSI/version/battery.
- Optional Root session: one-shot `su -c` backend compatible with the tested KernelSU device; compact fixed read-only frame; explicit user confirmation; no arbitrary shell.
- FPS/1% low uses `SurfaceFlinger --latency` for bounded foreground-package candidates, including normalized Android 16+ layer descriptions. It checks monotonic freshness and progress, preserves long stalls, and requires 100 intervals for 1% low. Unavailable reasons appear in the dashboard/overlay. See [FPS.md](FPS.md) for window limits and pending phone tests.
- One-key permission grant after Root is active: notification permission, overlay app-op, battery-optimization whitelist and Bluetooth-connect permission, all app-scoped fixed commands.
- Optional foreground Root auto-restore setting: attempts once per process when the app is opened, still requires Root-manager approval, and is suppressed after manual Root stop. No boot autostart or background request.
- Opt-in battery history, foreground-service notification, draggable overlay, no internet permission, no boot receiver, no analytics.
- SQLite schema version 1 and seven-column CSV preserved.

## Not verified / not implemented

- Only debug-signed development prereleases; no production signing, Play Store submission or Play policy review.
- No cross-device/ROM/KernelSU compatibility matrix, no light-theme/large-font/TalkBack/320dp visual acceptance.
- No measured monitoring overhead or OEM background-survival test.
- Shizuku backend, universal game-FPS compatibility, non-KGSL GPU utilization, modem capture and widgets remain unimplemented/unverified. KGSL GPU parsing is host-tested but still needs device validation.
- CPU temperature is a named HAL/regional CPU sensor, not a verified per-core junction temperature; battery power is an uncalibrated battery-terminal estimate.
- Root denial may be reported as unavailable; a stuck cleanup worker may require restarting the app.

## Historical records

v0.1 and v0.2 snapshots/reviews are under [docs/archive/](archive/). They are retained for provenance, not as current claims.
