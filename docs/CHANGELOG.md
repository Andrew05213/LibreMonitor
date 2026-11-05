# Changelog

Dates are development dates; this is not a published release history.

## Unreleased — KGSL GPU usage

- Fixed valid GPU percentage readings such as `35 %` being rejected; added strict `gpubusy` busy/total fallback with invalid/empty windows remaining unavailable.
- Kept GPU tiles visible when both readings fail so source, permission and unsupported-node reasons remain inspectable.
- Moved FPS discovery before the short-lived Root node frame, preventing slow FPS queries from invalidating freshly captured GPU/CPU/battery data. Added an explicit age check for the earlier FPS result.
- Preserved the three fixed GPU nodes and background sampling reductions. Added parser, protocol, freshness and collection-order regressions. KGSL device accuracy and non-KGSL support remain unverified/unimplemented; see [GPU.md](GPU.md).

## Unreleased — background resource usage

- Added demand-driven sampling: full foreground snapshots, reduced interactive background overlay snapshots, battery-only background history, and no sampling without a consumer.
- Background history follows the record interval instead of the UI refresh interval (defaults: 60s instead of 2s), skips Root/FPS and hardware detail collectors, and avoids reloading history rows until visible.
- Overlay Root frames now read only `/proc/stat` rather than all 102 compact-frame nodes. Screen-off removes the overlay and suspends live work; recording, if enabled, continues at the battery-only cadence without a wake lock.
- Added interruptible Root frame/FPS waits with cleanup, preserved accepted history writes across demand changes, and reset CPU/network baselines on live resume.
- Added demand/cadence, reduced CPU-frame/baseline and process-cancellation regressions. These establish behavior, not measured phone CPU or battery savings.

## 0.4.2-dev — 2026-11-04

- Fix Android 16+ `RequestedLayerState{...}` / layer-ID wrappers before querying the exact SurfaceFlinger layer name. Probe up to four matching candidates, preferring SurfaceView/BLAST, with focused/resumed-activity fallback.
- Validate Root identity, nonce, complete bounded output, actual-present timestamps, monotonic age and per-layer progress. Do not repeatedly present unchanged history as live FPS.
- Preserve long frame intervals in FPS/1% low. Require 100 valid intervals for 1% low and explain its bounded recent-history window.
- Tag and reject FPS results from a closed/replaced Root session through Main-thread publication, use 250ms service timeouts when supported with an eight-second whole-operation fallback budget, and display explicit failure reasons in the dashboard and overlay.
- Add synthetic command-fixture, parser/tracker and fake-process regressions. No phone install, real Root execution, calibration or device-performance claim accompanies this fix; see [FPS.md](FPS.md).

## 0.4.1-dev — 2026-11-04

- Added FPS and 1% low from SurfaceFlinger frame timestamps for the focused package layer, shown in the dashboard and overlay. 1% low is the average of the slowest 1% frame intervals; unavailable when Root/layer data is missing.
- Added frame-time parsing tests.

## 0.4.0-dev — 2026-11-04

- Added a Bluetooth card using Android public APIs: adapter state/name/address, LE support, connected profile count, paired devices, classic/LE/dual type, Class/services and known profile UUID mapping.
- Core Bluetooth version, per-device live RSSI and per-device battery are shown as unavailable when Android does not publicly expose them; no synthetic values.
- Added `BLUETOOTH_CONNECT` runtime permission handling and included it in the confirmation-gated Root one-key grant.
- Added explicit permission prompt/denial states and compact Bluetooth rows in the overview.
- Added an opt-in “restore Root when the app opens” setting. It is foreground-only, runs at most once per process, is suppressed after manual Root stop, and still requires Root-manager approval.

## 0.3.1-dev — 2026-11-03

- Redesigned the UI around icon-led metric cards, circular CPU/battery gauges, per-core bars and icon navigation.
- Added `androidx.compose.material:material-icons-extended`.
- Kept existing collectors, Root permission flow, history schema and CSV format unchanged.
- Device smoke-tested the redesign on OnePlus PJE110 / Android 17 / KernelSU.

## 0.3.0-dev — 2026-11-03

- First icon-led dashboard iteration; superseded by 0.3.1 within the same development day.

## 0.2.7-dev — 2026-11-03

- Replaced the interactive Root shell with a one-shot `su -c` backend compatible with the tested KernelSU setup.
- Added a compact Root frame (8 cpufreq nodes, 32 thermal zones, battery/bms, KGSL) to avoid the 479-node frame timeout.
- Removed the empty `uid_map` dependency from Root identity verification; UID 0 plus capability bits are checked.
- Verified CPU/per-core/temperature and battery health/capacity/cycle readings on device.
- Kept Root reads and the one-key permission grant behind explicit user actions.

## 0.2.1-dev — 2026-11-03

- Fixed an Android ICU `PatternSyntaxException` in the HAL temperature regex that crashed app startup.
- Escaped the closing brace and used `\z`; added the version bump and build evidence.

## 0.2.0-dev — 2026-11-03

- Added Root capability probing, battery health/capacity/cycles fields, compact dashboard direction and permission-grant plumbing.
- Added Root protocol safety tests, fixed CI SDK discovery and documented the one-shot permission boundary.

## 0.1.0-dev — 2026-11-03

- Initial independent prototype: device/CPU/memory/battery/network/storage readings, opt-in battery history, CSV export, foreground service and overlay.
- Established the source/reason/`Reading` model so unavailable values are not displayed as zero.

## Known limitations carried forward

- No production signing, release artifact or Play submission.
- No cross-device compatibility matrix.
- Shizuku, modem capture and widgets are not implemented; game-FPS compatibility remains ROM-dependent.
- CPU temperature and battery health remain source-dependent, not calibrated guarantees.
