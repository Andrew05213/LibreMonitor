# KGSL GPU usage correction

The unreleased fix addresses three concrete failures in the earlier collector:

- `gpu_busy_percentage` was parsed as a bare floating-point number, although KGSL can return `35 %\n`. Valid driver percentages were rejected.
- The Root frame already included `gpubusy`, but the collector never used it. KGSL exposes `busy total` for its recent statistics window, so a valid pair can supply `100 × busy / total` when the percentage node is unavailable or malformed.
- Both GPU readings being unavailable hid the entire GPU card, including the details needed to diagnose missing/denied nodes. The card now remains visible and its tiles expose source/status/reason in details.

The parser accepts only an entire percentage value, with optional `%` and whitespace, bounded to 0–100. The fallback accepts exactly two nonnegative integer counters with `total > 0` and `busy <= total`. It does not difference consecutive reads: these are the driver's recent window, not guaranteed boot-lifetime counters. Zero busy with a positive total is valid idle; zero total, missing fields, overflow and malformed input are unavailable, never assumed idle. Frequency remains the existing KGSL `gpuclk` reading in Hz converted to MHz and is independent of usage availability.

The fixed Root frame still contains the same three GPU paths. It reads the direct percentage before `gpubusy`, since some KGSL implementations clear old statistics when the GPU is powered off. No polling loop, writable node, vendor binary, arbitrary path or new runtime dependency was added. Background battery-only recording and reduced overlay collection still skip GPU.

## Collection ordering and freshness

Previously, a slow FPS query ran after the Root node frame and could age CPU/GPU/battery nodes beyond the two-second limit, forcing ordinary-permission fallback and losing otherwise readable GPU data. The shared serial sampler now completes FPS discovery before capturing the node frame. Both still use the same Root session and operation mutex. Node freshness and Root-session checks remain enforced; the collector also rejects stale/future frames directly.

An FPS result that waits over two seconds after its read completes is now marked unavailable instead of being published as current. Slow node reads therefore cannot silently leave old FPS statistics on screen. Cancellation still propagates and no duplicate Root frame is requested.

## Interface research and licensing

The wire formats and window semantics were checked against the original OnePlus KGSL driver:

- [OnePlusOSS/android_kernel_oneplus_sm8350, kgsl_pwrctrl.c at 13ca08f](https://github.com/OnePlusOSS/android_kernel_oneplus_sm8350/blob/13ca08fdf0979fdd61d5e8991661874bb2d19150/drivers/gpu/msm/kgsl_pwrctrl.c): `_gpu_busy_show`/`gpu_busy_percentage_show` emits a percentage with `%`; `gpubusy_show` emits `busy_old total_old` and can clear statistics when powered off.

This is interface research only. No GPL kernel implementation was copied, linked or bundled; the Kotlin parser, tests and scheduling changes are independent Apache-2.0 code. No other monitoring app was used as a source base. See [REUSE.md](REUSE.md).

## Validation limits

Host tests cover suffix/plain/decimal percentages, invalid input, busy/total fallback, zero/overflow windows, permission denial, independent frequency availability, real Root-protocol decoding into the collector, stale/future frames, slow FPS ordering, reduced overlay selection, FPS expiry, Root close and cancellation. All readings/processes used by these tests are synthetic.

This patch does not implement Mali, PowerVR or arbitrary devfreq discovery, nor guarantee every Adreno/OEM kernel exposes these KGSL nodes. No phone install or GPU-load calibration has been performed. On the target device, test idle, UI animation and a GPU workload with Root disabled/enabled, inspect both GPU tiles' details, and compare reported source values using the same three fixed nodes. Root cannot make absent nodes exist or guarantee SELinux permits access. Driver-wide load is not an individual game's GPU percentage.
