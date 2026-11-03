# Validation and test checklist

A successful compile is not a measurement-accuracy test. Current actual results are in [STATUS.md](STATUS.md); this page contains the automated contract and the remaining device test plan.

## Automated (current)

```sh
bash scripts/build-local.sh :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

- `:core:test`: CPU guest-time accounting, interval usage, component-level counter regression, memory units, malformed values, `Reading` invariants, nullable/quoted/safe CSV, settings bounds.
- `:app:testDebugUnitTest`: Root protocol framing/limits, compact allowlist, permission script/result parsing, identity probe, HAL/sysfs CPU temperature parsing, battery capacity/health parsing.
- `:app:assembleDebug`: Kotlin/Compose compile, manifest merge, Android resource pipeline, D8 and debug packaging/signing.
- `:app:lintDebug`: Android/manifest/API lint; no baseline or suppression merely to hide a failure.
- GitHub Actions runs the same Gradle tasks on Ubuntu. Current evidence: [run 41625409166](https://github.com/Andrew05213/LibreMonitor/actions/runs/41625409166).

Current result: **77 core + 36 app tests**, 0 failures/errors; Lint **0 errors, 7 dependency-version warnings**.

## Device smoke test (already partly performed)

The OnePlus PJE110 / Android 17 / KernelSU smoke test confirmed the following once; repeat after UI/collector changes:

1. Install over the previous debug build; no network/root/location/storage/boot permission prompt should appear merely on launch.
2. Open the dashboard; check CPU gauge, temperature, per-core rows, memory/Swap, network and storage.
3. Enable Root through the in-app confirmation; verify the Root manager prompt and `已启用 · Root`. Deny/timeout once and confirm basic mode still samples.
4. Open battery; check level, voltage, current, power, temperature, health status, capacity health, design/full capacity, remaining charge, cycles and source. A real `0` is valid; missing data must be `—`.
5. Tap metric cards; verify source/status/reason/sample time detail sheet. Do not expect a fabricated sample.
6. Use the one-key permission grant; confirm the dialog lists notification/overlay/battery whitelist and that denial leaves the app usable. Revoke one permission and confirm the related function stops.
7. Toggle history and overlay, rotate/recreate, background/resume, and use the persistent Stop action. Confirm one sampling loop and no stale UI shown as live.
8. Export CSV through DocumentsUI; check UTF-8, seven columns, null fields empty, signed current/power labels and provider error behavior. Clear history with confirmation.
9. Kill/relaunch and reboot: no boot autostart; deliberate app opening may restore opted-in recording only after notification checks.
10. Check 7-day/50k cleanup, low storage, corrupted preferences, clock changes, dark/light theme, 320dp width, large fonts, TalkBack and screenshot redaction.
11. Measure monitor CPU/battery/wakeup impact before calling it lightweight.

## Still unverified

- Cross-device/ROM/KernelSU compatibility, especially Root frame latency and sensor names.
- Light theme, large fonts, TalkBack, 320dp and RTL layout.
- OEM background survival and quantified monitoring overhead.
- Shizuku, cross-app FPS, modem capture and widgets are unimplemented.

Stopping prevents accepting new history records; an accepted transaction may finish. There is no wake lock, boot receiver, silent watchdog or assurance of OEM background survival.
