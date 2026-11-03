# LibreMonitor contributor rules

This is an independent Android monitoring app. Do not copy OpenMonitor code, branding, assets, activation logic, or proprietary/vendor binaries. Only reuse components with verified compatible licenses; record sources in `docs/REUSE.md`.

## Current scope

- Current development version is `0.3.1-dev`; verify `app/build.gradle.kts` before repeating a version in prose.
- The UI is an icon-led dashboard. Primary cards show label, value and unit; provenance and failure explanations belong in details.
- Root is optional and user-initiated. The tested backend is a one-shot fixed `su -c` command with a compact read-only allowlist. It must never expose arbitrary shell/path input, write kernel nodes, change SELinux, affect other apps, terminate processes, capture modem traffic, add analytics/internet, or auto-start at boot.
- After a separate confirmation, Root may run fixed app-scoped commands for this app's notification permission, overlay app-op and battery-optimization whitelist. Do not expand this into a general privileged command API.
- Shizuku, cross-app FPS, modem capture and widgets are unimplemented. Do not present them as available.

## Data correctness

- Missing, denied, stale or invalid readings must not become zero. `Reading` carries source, status and reason.
- Rates use monotonic elapsed time; reject counter resets and missing baselines.
- CPU temperature is a named HAL/regional sensor, not a verified per-core junction temperature.
- Battery power is an uncalibrated battery-terminal estimate, not charger/SoC power. Capacity health is only a same-battery, same-unit full/design ratio.
- One shared sampling engine feeds UI, recording and overlay; do not add duplicate loops.
- Continuous recording/overlay must be opt-in, visible and stoppable. No wake lock or boot receiver.

## Architecture and ownership

- `core/` is pure Kotlin; `app/` owns public Android collectors, private persistence, the shared runtime, Compose UI, Root backend and foreground/overlay service.
- `MonitorRuntime` is the single process-owned sampling loop. `OneShotRootReader` is the current Root backend; `RootReader` is retained for protocol tests/persistent-shell experiments.
- Root frames use fixed allowlists. Current compact frame limits are in `RootProtocol.compactNodes()`; do not add caller-provided paths.
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
