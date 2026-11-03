# v0.2.7 device smoke test (historical)

> Historical record. It describes the pre-icon UI and the first working one-shot Root build. Current status is in [STATUS.md](STATUS.md).

Date: 2026-11-03. Device: OnePlus PJE110, Android 17 (API37), KernelSU root manager. Debug APK installed over v0.2.6 with the same debug signature.

## Observed working

- Root session: `已启用 · Root`, one-shot `su -c` backend, no timeout after the uid_map compatibility fix.
- One-key grant result: notification granted, overlay allowed, battery-optimization whitelist added.
- Overview: CPU usage ~35.4%, CPU temperature 58.0°C, 8 per-core usage/frequency rows populated.
- Battery: 97%, 4.23V, 0mA, 0.00W at the sampled idle moment, 33.9°C, health status GOOD, capacity health 82.4%, design 5500mAh, full-charge 4530mAh, remaining charge 4178mAh, cycle count 883, charging source “未连接外部电源”.
- No installation crash after the Android ICU regex fix.

## Limitations

- One device/ROM/Root manager only. Values are a point-in-time smoke observation, not calibration or cross-device accuracy evidence.
- 0mA/0.00W can be a real idle report or a vendor/current-sign limitation; do not interpret it as a calibrated power measurement.
- Root frame uses a compact fixed allowlist (8 cpufreq nodes, 32 thermal zones, battery/bms fields, KGSL); devices with more cores or different sensor layout may show unavailable values.
- Root frame latency and battery life impact were not measured; no claim of “lightweight” runtime overhead.
- No release signing, Play submission or remote CI result for this exact commit is claimed here.
