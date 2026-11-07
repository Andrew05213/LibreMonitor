# LibreMonitor contributor rules

This is an independent Android monitoring app. Do not copy OpenMonitor code, branding, assets, activation logic, or proprietary/vendor binaries. Only reuse components with verified compatible licenses; record sources in `docs/REUSE.md`.

## Current scope

- Current development version is `0.7.1-dev`; verify `app/build.gradle.kts` before repeating a version in prose.
- The UI is an icon-led dashboard. Primary cards show label, value and unit; provenance and failure explanations belong in details.
- Bluetooth uses public APIs only. Do not fake core version, per-device RSSI or battery; permission-denied and unavailable states must remain explicit.
- Earbud BLE scans are explicit, foreground-only, bounded to 30s and expire readings after 60s. Do not associate rotating addresses by name, or persist raw advertisements. Bluetooth PAN counters are not headphone audio throughput.
- SoC names use exact catalog identifiers. Core types come from ARM part IDs or explicitly labeled capacity inference; do not map a marketing core-count template onto unverified logical CPU IDs.
- Root is optional and user-initiated. A settings opt-in may attempt foreground auto-restore once when the app opens; it must never run at boot, in the background, or after the user manually stopped Root. The tested backend is a one-shot fixed `su -c` command with a compact read-only allowlist. It must never expose arbitrary shell/path input, write kernel nodes, change SELinux, affect other apps, terminate processes, capture modem traffic, add analytics/internet, or auto-start at boot.
- After a separate confirmation, Root may run fixed app-scoped commands for this app's notification permission, overlay app-op, battery-optimization whitelist and Bluetooth-connect permission. Do not expand this into a general privileged command API.
- Shizuku, modem capture and widgets are unimplemented. FPS/1% low is a Root-dependent SurfaceFlinger best effort for the focused layer, not a universal game-FPS guarantee; show unavailable rather than a refresh-rate proxy.

## Data correctness

- Missing, denied, stale or invalid readings must not become zero. `Reading` carries source, status and reason.
- Rates use monotonic elapsed time; reject counter resets and missing baselines.
- CPU temperature is a named HAL/regional sensor, not a verified per-core junction temperature.
- Battery power is an uncalibrated battery-terminal estimate, not charger/SoC power. Capacity health is only a same-battery, same-unit full/design ratio.
- Current units come from documented interfaces, an explicit driver marker or a user override, never the reading magnitude. Oplus current adaptation requires the exact supported model marker; preserve raw signs and same-supply voltage/current pairing.
- FPS has at most one pending query driven by the shared engine, with no independent polling loop. It may overlap one fixed node operation; cached FPS never counts as a newly recorded measurement.
- Overlay controls persist fractional Float values while accepting legacy Int preferences. Keep sliders continuous, update previews in place and persist on release.
- One shared sampling engine feeds UI, recording and overlay; do not add duplicate loops.
- Continuous recording/overlay must be opt-in, visible and stoppable. No wake lock or boot receiver.

## Architecture and ownership

- `core/` is pure Kotlin; `app/` owns public Android collectors, private persistence, the shared runtime, Compose UI, Root backend and foreground/overlay service.
- `MonitorRuntime` is the single process-owned sampling loop. `OneShotRootReader` is the current Root backend; `RootReader` is retained for protocol tests/persistent-shell experiments.
- Root frames use fixed allowlists. Current compact frame limits are in `RootProtocol.compactNodes()`; do not add caller-provided paths.
- Dashboard-only Wi-Fi diagnostics run at most once per five-second cache window, with a fixed three-second deadline. Return only filtered SoftAP state/count fields, never raw dumps, SSIDs, keys or client identities.
- SQLite schema version 1 and the seven-column CSV format are compatibility boundaries. Schema changes require an explicit migration.

## Validation

- Run `bash scripts/build-local.sh :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` on this host when the toolchain is available.
- Run standard `./gradlew` on a normal Android SDK host/CI.
- Report precisely what was tested; synthetic tests and mock frames are not device measurements.
- Only debug signing is used. No production keys or secrets in the repository.
- Update `docs/STATUS.md`, `docs/CHANGELOG.md` and `docs/INDEX.md` when a claim or version changes.

## Working model

The user has requested heavy use of DeepSeek V4.1 Flash Max and GPT-6.1 Terra High, with Sol High for lead/integration if needed. Verify configured provider/model identifiers and explicit reasoning effort through the supported delegation route. Never silently replace the selected model or effort; report a failed dispatch accurately.

## License

New independent code is Apache-2.0. Preserve third-party notices. A change requiring copyleft dependencies needs a lead review first.
