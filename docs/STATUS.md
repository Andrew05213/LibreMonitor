# Current validation status — 0.4.2-dev

Updated 2026-11-04. This is a development prototype, not a production or cross-device validated release.

## Current build

- Version `0.4.2-dev`, versionCode `14`, application ID `org.libremonitor.app`.
- Isolated host Kotlin/JVM validation passed **77 core + 57 app tests (134 total)**. Android framework calls in collector tests used host fixtures; the generated FPS script ran only against synthetic commands. No real Root command or phone operation was executed. See [FPS.md](FPS.md).
- Full Android unit tests, debug packaging and lint are checked by the branch's GitHub Actions workflow. Do not infer a phone validation result from these checks.
- Prior 0.4.1 build/CI evidence remains in `validation-report-v041.json`; it is not evidence for the revised FPS implementation.
- This fix has not been installed on the user's phone. Production signing and device FPS accuracy remain unverified.

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

- Compact icon dashboard: CPU hero/gauge, per-core bars, memory/Swap, network, storage, GPU when available.
- Battery detail page with voltage/current/power/temperature/health/capacity/cycle readings, real-history chart, CSV export and confirmed clear.
- Bluetooth card using public APIs: adapter state/name/address, LE support, connected profile count, paired devices, type, Class/services, known profile mapping, and explicit unavailable reasons for RSSI/version/battery.
- Optional Root session: one-shot `su -c` backend compatible with the tested KernelSU device; compact fixed read-only frame; explicit user confirmation; no arbitrary shell.
- FPS/1% low uses `SurfaceFlinger --latency` for bounded foreground-package candidates, including normalized Android 16+ layer descriptions. It checks monotonic freshness and progress, preserves long stalls, and requires 100 intervals for 1% low. Unavailable reasons appear in the dashboard/overlay. See [FPS.md](FPS.md) for window limits and pending phone tests.
- One-key permission grant after Root is active: notification permission, overlay app-op, battery-optimization whitelist and Bluetooth-connect permission, all app-scoped fixed commands.
- Optional foreground Root auto-restore setting: attempts once per process when the app is opened, still requires Root-manager approval, and is suppressed after manual Root stop. No boot autostart or background request.
- Opt-in battery history, foreground-service notification, draggable overlay, no internet permission, no boot receiver, no analytics.
- SQLite schema version 1 and seven-column CSV preserved.

## Not verified / not implemented

- No production signing, Play Store submission, Play policy review or release artifact.
- No cross-device/ROM/KernelSU compatibility matrix, no light-theme/large-font/TalkBack/320dp visual acceptance.
- No measured monitoring overhead or OEM background-survival test.
- Shizuku backend, universal game-FPS compatibility, GPU utilization coverage, modem capture and widgets remain unimplemented/unverified.
- CPU temperature is a named HAL/regional CPU sensor, not a verified per-core junction temperature; battery power is an uncalibrated battery-terminal estimate.
- Root denial may be reported as unavailable; a stuck cleanup worker may require restarting the app.

## Historical records

v0.1 and v0.2 snapshots/reviews are under [docs/archive/](archive/). They are retained for provenance, not as current claims.
