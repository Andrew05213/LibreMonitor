# Documentation index

## Current

| Document | Purpose |
|---|---|
| [README](../README.md) / [中文 README](../README.zh-CN.md) | Project summary, current features, build and privacy boundaries. |
| [STATUS.md](STATUS.md) | Current 0.4.2 validation, prior device smoke results and open limitations. |
| [CHANGELOG.md](CHANGELOG.md) | Development history from 0.1.0 to 0.4.2. |
| [FPS.md](FPS.md) | Read-only FPS compatibility fix, test evidence, measurement limits and pending device checks. |
| [PERFORMANCE.md](PERFORMANCE.md) | Background sampling modes, deterministic work reduction and phone measurement checklist. |
| [GPU.md](GPU.md) | KGSL percentage/counter formats, Root sampling order, diagnostics and compatibility limits. |
| [validation-report-gpu.json](validation-report-gpu.json) | GPU correction Android test/build results, lint and APK checksum. |
| [validation-report-performance.json](validation-report-performance.json) | Local optimization tests, lint, APK checksum and measurement limitations. |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Current data path, time/cancellation, Root backend, storage and service design. |
| [BUILDING.md](BUILDING.md) | Standard SDK/CI build and the ARM64/proot local toolchain. |
| [TESTING.md](TESTING.md) | Automation and device test checklist. |
| [REUSE.md](REUSE.md) | Adopted dependencies, licenses and evaluated-but-not-used components. |
| [V027_DEVICE.md](V027_DEVICE.md) | 0.2.7 Root/CPU/battery device smoke notes (historical, still useful). |
| [V030_UI.md](V030_UI.md) | Icon-led UI design notes and visual smoke evidence. |
| [validation-report-v041.json](validation-report-v041.json) | Historical 0.4.1 FPS/1% low build evidence. |
| [validation-report-v040.json](validation-report-v040.json) | 0.4.0 Bluetooth build evidence. |
| [validation-report-v031.json](validation-report-v031.json) | 0.3.1 tests, CI, APK and device-smoke evidence. |
| [validation-report-v02.json](validation-report-v02.json) | v0.2 machine-readable build evidence. |
| [validation-report-v021.json](validation-report-v021.json) | v0.2.1 crash-fix evidence. |
| [validation-report-v022.json](validation-report-v022.json) | v0.2.2 permission-grant evidence. |

## Historical archive

The following are retained for provenance and are **not current claims**:

- [STATUS-v0.1.md](archive/STATUS-v0.1.md)
- [STATUS-v0.2.md](archive/STATUS-v0.2.md)
- [CONTRACTS-v0.1.md](archive/CONTRACTS-v0.1.md)
- [V2-CONTRACTS.md](archive/V2-CONTRACTS.md)
- [V2-ROOT_REVIEW.md](archive/V2-ROOT_REVIEW.md)
- [V2-DATA_REVIEW.md](archive/V2-DATA_REVIEW.md)
- [V2-PREFLIGHT.md](archive/V2-PREFLIGHT.md)
- [V2-PIPE_HANDOFF.md](archive/V2-PIPE_HANDOFF.md)
- [V2-TESTING.md](archive/V2-TESTING.md)

## Maintenance rules

- Update `STATUS.md` and `CHANGELOG.md` when a build or device claim changes.
- Keep version claims tied to `app/build.gradle.kts`; do not copy a version number into prose without checking it.
- Treat archive files as immutable history except for factual corrections or link repairs.
- Do not describe a backend as available unless it is implemented and actually probed/authorized on a device.
