# Validation and device smoke-test checklist

A successful compile is not a measurement-accuracy test. Only entries in STATUS.md are actual results; the list below is a plan.

## Automated
- `:core:test`: CPU guest-time accounting, interval usage, counter resets/zero traffic, memory units and malformed values, reading availability invariants, nullable/quoted CSV and formula guards, opt-in settings bounds.
- `:app:assembleDebug`: Kotlin/Compose compile, manifests, ARM64 aapt2 daemon/resource pipeline, D8, debug packaging/signing.
- `:app:lintDebug`: Android compatibility and manifest/API lint. No warning suppression merely to make a failed build look green.

## Device tests (require a separately approved install)
1. Install debug APK and launch in basic mode. No network, root, storage or phone-state permission should be requested.
2. Confirm public device metadata, RAM, battery level/temperature/voltage reflect the OS. Compare reported source/units; do not infer CPU temperature from battery.
3. On denied `/proc/stat`, total/per-core usage shows an unavailable reason; first valid counter sample is not a zero rate. Per-core frequency availability is device-specific.
4. Switch between1/2/5/10seconds, rotate, background/resume, open via launcher/notification/overlay. Confirm one sampling engine and no stale UI shown as current. Background without opt-in service stops sampling.
5. Turn on history, allow notifications, wait over a minute, check stored real data and chart gaps. No sample rate should be fabricated or guaranteed during deep sleep.
6. Deny notifications; disable app notifications or the monitor channel while service runs. Verify continuous functions stop, explain why, and do not silently resume.
7. Enable overlay only after explicit system overlay grant. Drag within screen, open existing Activity, revoke overlay grant, stop from overlay and notification. History and overlay are independent; stopping all disables both.
8. While Android permission UI is open, rotate/recreate the Activity, then grant/deny. The pending action must be preserved but must not enable without permission.
9. Export through DocumentsUI; parse UTF-8 CSV, check null fields remain empty and raw signed current/power have correct labels. Repeated export to the same URI should serialize; unsupported provider/truncation errors must be explicit.
10. Clear history using confirmation, including during recording/export. Old read results must not refill the cleared UI; subsequent newly recorded samples may appear while recording remains on.
11. Rapidly toggle history and interval with the overlay running; old asynchronous preferences must not re-enable a user-disabled function.
12. Kill/relaunch the process and reboot. There must be no boot autostart; app-private settings/history may persist and deliberate app opening can restore opted-in recording if notification requirements are still met.
13. Check7day cleanup/50k cap, low storage, corrupted preferences, failed database open, clock changes, night mode,320dp width/large fonts, TalkBack and screenshot redaction.
14. Quantify monitor overhead (CPU, battery, wakeups and frames) before calling it lightweight. No performance numbers or battery health claims without measurements.

Stopping prevents accepting new history records; a transaction accepted before Stop may finish. The prototype does not claim to roll back a committed sample. There is no wake lock, process watchdog, boot receiver, privilege escalation or assurance of OEM background survival.
