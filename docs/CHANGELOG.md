# Changelog

Dates are development dates; this is not a published release history.

## 0.7.0-dev — 2026-11-07

- Replaced four main tabs with native Material3 功能 / 概览 / 设置 navigation, compact function/settings rows and green accents. Moved technical footnotes to detail views and kept missing/interrupted states explicit.
- Added continuous fractional overlay opacity/font controls, separate dock opacity, live in-place previews and legacy preference migration, with persistence on release.
- Added same-supply live battery voltage/current selection, an explicitly identified Oplus mA interface profile, raw electrical diagnostics and a manual current-unit override. Signed battery-terminal power is preserved; no magnitude-based or dual-cell scaling is used.
- Moved FPS to a single bounded pending query driven by the shared engine, allowed fixed FPS/node operations to overlap, kept slow unrelated probes off the overview and displayed actual refresh intervals. Cached FPS is not recorded as new samples. See [POWER_AND_NATIVE_UI.md](POWER_AND_NATIVE_UI.md).

## Unreleased — FPS reliability (`fix/fps-reliability`)

- Rotated bounded four-layer discovery pages to reach candidates beyond the first four; prioritized SurfaceView before BLAST, cached validated targets for at most five seconds and cached dumpsys timeout capability per Root session. Foreground package checks and Root session validation remain mandatory.
- Distinguished empty/header-only/pending/malformed/denied/timed-out responses, rejected partial failed responses and stopped empty containers from masking a stalled surface.
- Added explicit JSON diagnostic export with bounded response previews, run failure frequencies, actual active-sampling intervals, collector timings and batch-write/backpressure timing. No automatic upload, extra Root query, new permission or schema migration is involved.
- Aggregated saved-session FPS failure/target counts and replaced low-coverage headline averages with “样本不足”. See [FPS_RELIABILITY.md](FPS_RELIABILITY.md) for privacy, reset, candidate and measurement limits.

## Unreleased — sampling pipeline optimization

- Added page/expanded-overlay sampling demand and skipped unneeded FPS queries; visual preferences no longer restart sampling baselines.
- Cached thermal discovery and battery metadata for up to 30 seconds, retained fresh live values, and reduced steady Root frames to fixed subsets of the existing allowlist. Paused slow Root hotspot diagnostics during recording/sub-second sampling.
- Added a bounded background writer with ten-point/one-second-target batches, explicit backpressure/failure handling and a Stop drain. Review now reads 128-row chunks and decompresses outside the writer lock.
- Updated overlay opacity in place, saved sliders on release and avoided formatting hidden/unchanged/unselected values. See [PERFORMANCE_OPTIMIZATION.md](PERFORMANCE_OPTIMIZATION.md) for crash-loss limits and validation boundaries.

## 0.6.0-dev — 2026-11-05

- Added physical left/right edge docking with translucent semicircular arrows, tap-to-expand, draggable handles, long-press collapse, rotation/inset clamping, and scrolling for large custom layouts.
- Added persisted overlay content/order, 12 selectable fields, one/two columns, 12–24sp values, 30–100% background opacity and optional edge snapping. Custom detail/network fields use the existing shared sampling loop.
- Added an exact-identifier TSV SoC catalog (seven families), Snapdragon names and three core tiers from ARM part IDs; capacity inference and unknown identities stay explicit.
- Added Bluetooth PAN byte-counter rates from a fixed `/proc/net/dev` read (not audio throughput), additional public protocol/PHY capabilities, and an explicit 30-second foreground Apple proximity scan. Supported battery bins expire after 60s and are not associated by device name.
- Added optional phone-state grants, default connectivity/carrier/SIM/data-radio fields, Wi-Fi/hotspot capabilities, and system idle/power/thermal/kernel fields. Visible-dashboard state uses a five-second cache. A separate fixed Root diagnostic filters only SoftAP state/client-count fields, with a three-second deadline and no raw SSID/key/client output.
- New runtime permissions are requested only through the related manual actions. No location/IMEI/phone-number query, internet permission, packet capture or background BLE scan was added. See [DEVICE_EXTENSIONS.md](DEVICE_EXTENSIONS.md).

## 0.5.0-dev — 2026-11-05

- Published as a debug-signed [development prerelease](https://github.com/Andrew05213/LibreMonitor/releases/tag/v0.5.0-dev), with APK, checksum and local validation report.
- Redesigned the draggable overlay as a compact 2×2 CPU/GPU/memory/FPS panel. Close hides the panel while an explicitly started recording continues; the notification can stop all monitoring.
- Added opt-in named performance sessions, a Performance tab, local session history, selectable timeline previews and full-session valid-sample means/minima/maxima for 14 metrics, including CPU/GPU frequency, temperature and signed battery-terminal power estimates.
- Recording uses the shared loop, continues with the app in the background or the panel closed, pauses on screen-off and stops through the app or persistent notification. Process interruption never auto-restarts a session; reopening marks unfinished sessions interrupted and retains saved samples.
- Added a separate version-1 performance database with compressed versioned reading payloads, preserving source/status/reason. Existing battery schema and CSV remain unchanged. Retention is 20 sessions / 100,000 total samples; a single session stops and saves at 100,000 samples.
- Overlay-only Root frames now include `/proc/stat` and the three existing KGSL nodes (4 nodes). Recording uses existing compact hardware reads for frequency/thermal/battery detail while skipping network/storage/Bluetooth when the app is hidden. No new Root path or permission is added.

## Unreleased — faster sampling

- Added 100ms, 200ms and 500ms refresh options alongside 1s, 2s, 5s and 10s. Model validation and preference loading/saving share the 100ms–10s bounds; the default remains 2s.
- Live and overlay samples subtract collection time from the next wait. Slow collectors remain serial, with no overlapping samples or catch-up bursts; errors retain a full retry delay. The selected rate is a target, not a device throughput guarantee.
- Background battery history retains its independent cadence (60s by default). See [PERFORMANCE.md](PERFORMANCE.md).

## Unreleased — KGSL GPU usage

- Fixed valid GPU percentage readings such as `35 %` being rejected; added strict `gpubusy` busy/total fallback with invalid/empty windows remaining unavailable.
- Kept GPU tiles visible when both readings fail so source, permission and unsupported-node reasons remain inspectable.
- Moved FPS discovery before the short-lived Root node frame, preventing slow FPS queries from invalidating freshly captured GPU/CPU/battery data. Added an explicit age check for the earlier FPS result.
- Preserved the three fixed GPU nodes and background sampling reductions. Added parser, protocol, freshness and collection-order regressions. KGSL device accuracy and non-KGSL support remain unverified/unimplemented; see [GPU.md](GPU.md).
- Review follow-up: removed the shell `awk` dependency so FPS layer discovery works on Android 8/8.1, and moved FPS failure text out of the primary card into metric details.

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
- Tag and reject FPS results from a closed/replaced Root session through Main-thread publication, use 250ms service timeouts when supported with an eight-second whole-operation fallback budget, and display explicit failure reasons in metric details and the overlay.
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
