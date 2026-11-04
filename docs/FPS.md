# FPS collection — 0.4.2-dev

## What changed

The original implementation used the last package-matching `SurfaceFlinger --list` line verbatim. Android 16+ can print `RequestedLayerState{<name> parentId=...}` here, but `--latency` compares against the exact layer name. Passing the debug description yields a refresh-period header without useful frame timestamps. This is a code/source-supported compatibility cause, not a diagnosis already confirmed on the user's phone.

The fixed script strips that wrapper and optional numeric layer-ID prefixes, uses focused-app / resumed-activity fallback, and probes up to four matching layers. SurfaceView/BLAST layers take priority over containers. Names are quoted shell data, never evaluated as commands. Other packages are rejected at a package boundary. No global compositor statistics are enabled or cleared; no node, app permission or Root configuration is changed.

The response checks UID 0, a per-operation random marker, completion, base64/byte limits, layer ownership and the second (actual-present) timestamp column. Zero and pending `INT64_MAX` entries are excluded. A per-session tracker rejects future/older-than-three-second timestamps and repeated non-advancing history. Switching application or Root session starts fresh. FPS returned after a Root close/replacement is discarded.

Positive long frame intervals, including stalls over one second, are preserved. FPS is the reciprocal of the mean available frame interval. 1% low is the reciprocal of the mean of the slowest ceil(1% of intervals), and is unavailable until at least 100 valid intervals exist. The available history is limited to the compositor buffer and at most ten seconds; missed frames between polls cannot be recovered. This is not a whole-game benchmark.

The FPS process wait budget is at most two seconds, while each fixed dumpsys call has a one-second service timeout. The shared legacy process transport may still spend up to two additional seconds joining streams; this is not an end-to-end two-second deadline. Sampling remains serial and two `su` operations per active Root cycle remain. Generic Root close/authorization races and process-cleanup shortcomings from the earlier review are separate open issues.

The dashboard and overlay expose a reason for unavailable data: missing focus/layer, missing/new/old frame history, denied service access, malformed response, timeout or a stopped Root session. A static screen is unavailable rather than a fabricated zero or indefinitely repeated 60 FPS. Unsupported ROMs can still return no per-layer timestamps.

## Automated evidence

The isolated host Kotlin/JVM runner passed 134 tests (77 core + 57 app) with Kotlin 2.1.21 / Java 17. Android framework APIs used by the other collector tests were replaced with minimal host fixtures. The generated FPS shell itself was executed only against synthetic `dumpsys` and `id` executables, never `su` or a phone. Fixture tests cover Android debug wrappers, legacy raw names, multiple candidates, null-focus fallback, foreign packages, and literal shell characters in names. Parser/tracker tests cover nonce/size/permission errors, repeated/old/future/pending frames, short samples and a retained 1.5-second stall. Fake-process tests cover real backend parsing, disabled Root, timeout and a late result after close.

Full Android unit tests, APK build and lint must also pass on the branch's GitHub Actions workflow. A green build is not evidence that the phone's compositor exports usable timestamps.

## Device verification — pending separate approval

No APK installation, real Root command or device permission/configuration change was performed for this fix. Preserve the existing signing key and app data when building a test APK.

1. After approval, test the existing Root permission with the signed test build. With the target game actively rendering and the overlay open, record the reported target layer, FPS, 1% low (or its missing-sample reason), and any collection failure reason.
2. Exercise a normal Android scrolling UI and a SurfaceView/Vulkan game; switch between them, rotate, background/resume, and ensure the displayed target changes without retaining the previous application's FPS.
3. Freeze/static-screen behavior must become explicitly unavailable on a non-advancing response, rather than retaining a live number. Resume animation and confirm recovery. Check long-stall accounting using a controlled test animation; do not suspend SurfaceFlinger or another app's processes.
4. Start a read, then stop Root: a late FPS result must not repopulate the display. Root authorization races and stopping the underlying legacy process itself require the separate backend cleanup work.
5. At the default two-second setting, run 30 minutes and then two hours; measure actual publish intervals, timeouts, CPU, residual workers/FDs and charge use against the same basic-monitoring baseline. No runtime-overhead or battery-life claim is made yet.
6. If all candidate layers lack timestamps, retain the explicit ROM/interface error and gather only a separately approved, fixed read-only diagnostic capture. Do not silently substitute display refresh rate, UI-only gfxinfo statistics for a game, or globally enable/clear compositor counters.

## Primary references

- [AOSP Android 16 SurfaceFlinger listLayers and dumpStats](https://android.googlesource.com/platform/frameworks/native/+/refs/heads/android16-release/services/surfaceflinger/SurfaceFlinger.cpp): debug strings in `--list`, exact `getName()` match in `--latency`.
- [AOSP RequestedLayerState debug string](https://android.googlesource.com/platform/frameworks/native/+/refs/heads/main/services/surfaceflinger/FrontEnd/RequestedLayerState.cpp): wrapper and parent metadata format.
- [AOSP FrameTracker](https://android.googlesource.com/platform/frameworks/native/+/master/services/surfaceflinger/FrameTracker.cpp): actual-present column and pending-fence sentinel.

These sources were inspected for interoperability; no monitoring-app code or vendor binaries were reused.
