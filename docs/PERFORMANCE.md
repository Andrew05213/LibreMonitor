# Performance sessions and sampling

## Record and review a game

1. Open **性能**, optionally name the game/session, then tap **开始性能记录**. Notification permission is required; Root is requested only through its separate foreground control.
2. Enable **游戏悬浮窗** if wanted, then open the game. The draggable two-by-two panel shows CPU, GPU, system memory and FPS. Tap its handle to return to the app; × hides only the panel while recording continues. A small red dot indicates an active recording.
3. Tap **结束并保存** in the Performance tab or **停止全部** in the persistent notification. Select the saved session to inspect its metrics and timeline. The latter also closes the overlay and stops battery history/Root as before.

Sessions run only after explicit start and are not resumed automatically after a process kill or reboot. Screen-off pauses performance sampling; the session remains open and resumes on screen-on, leaving a visible time gap in its preview. If the service stops unexpectedly or the process dies, saved samples remain available and the session is marked interrupted. Phone/ROM background restrictions can still stop the service.

## One shared sampling loop

The background optimization began with the 0.4.2 FPS fix (PR #1 head `79efe22`). The same process-owned loop now selects work from these consumers:

| Demand | Work | Cadence |
|---|---|---|
| Interactive, visible Activity | Full snapshot | UI refresh (default 2s) |
| Interactive, background overlay only | Aggregate CPU, GPU, memory, public battery and FPS | UI refresh |
| Interactive, explicit performance recording | CPU/GPU usage, CPU/GPU frequency, memory, FPS, temperatures and battery readings | UI refresh, even with overlay closed |
| Background history, or screen-off with history enabled | Public battery APIs only | History interval (default 60s) |
| Screen-off overlay without history, or no consumers | None | No sampling timer |

Overlay-only collection skips thermal/per-core frequency, network, storage and Bluetooth. Its privileged frame contains `/proc/stat` plus the three existing KGSL GPU nodes (4 total), compared with 102 nodes in the full compact frame. Performance recording uses the existing full compact hardware frame for frequency/thermal/battery data, while hidden network/storage/Bluetooth work remains skipped. FPS still probes the foreground surface at each visible/recording sample. No new Root paths, automatic authorization, wake locks or boot receivers are added.

## Faster visible refresh

Settings offer 100ms, 200ms, 500ms, 1s, 2s, 5s and 10s; 2s remains the default. Subsecond choices are labeled in milliseconds. Model validation, preference loading and saving accept the same 100ms–10s range, so a saved fast setting survives reopening the app.

Live, overlay and performance sampling subtract collection/persistence time from the selected interval before waiting. For example, a 40ms cycle at 100ms waits another 60ms. A 150ms cycle starts its next cycle after completion without attempting missed ticks. One loop continues to own all sampling; failed attempts wait a full interval before retrying. Root and SurfaceFlinger queries can take longer than the target, and faster refresh increases collection work. No measured 10Hz phone throughput is claimed. Background battery history still uses its independent 60s default rather than the visible refresh interval.

## Saved metrics and review

Each point stores CPU/GPU usage, system memory usage, FPS, recent-window 1% low, recent-window frame time, signed battery-terminal power, the mean of currently readable CPU-core frequencies, GPU frequency, CPU regional temperature, battery temperature/level, raw battery current and battery voltage. FPS target-layer identity and every reading's availability, source and reason are retained. Missing/invalid values remain unavailable, not zero; GPU is KGSL-only and FPS remains dependent on Root/SurfaceFlinger compatibility.

The review's means/minima/maxima cover **all valid saved samples**, with explicit valid/total counts. Means are sample-weighted, not wall-time-weighted. The chart previews at most 601 chronological points; missing data between preview points breaks the line. Large time gaps also break it. Window 1% low values describe the recent SurfaceFlinger window at each point; they do not establish a single whole-game 1% low. Battery-terminal `V × CURRENT_NOW` power retains the OEM's raw current sign and is uncalibrated. CPU frequency means cover only readable cores.

`performance.db` is separate from battery history and has its own schema v1. Its two tables retain at most 20 sessions and 100,000 total compressed sample payloads by removing oldest **ended** sessions. A single session reaching 100,000 points is ended and saved with an explicit message. Payloads have a version and bounded decompression. Review streams all points into statistics instead of retaining the full session in UI memory. Existing battery schema v1, its 50,000-row limit and seven-column CSV are unchanged.

With defaults, an idealized hour of background history schedules 60 battery samples instead of approximately 1,800 full snapshots: 30 times fewer sampling cycles and zero Root frame/FPS processes in that mode. These are deterministic work counts, not measured CPU, memory or battery percentages. Doze, process death and slow collectors can delay samples; no wake lock, alarm or boot receiver was added.

History deadlines use monotonic elapsed time and survive consumer/screen changes. Only successful inserts advance the ledger; an accepted insert may finish after Stop. Background recording does not repeatedly decode the latest 360 rows. Opening the UI reloads them. SQLite v1 and the seven-column CSV are unchanged. Public API readings may be unavailable on an OEM that previously required Root; fields remain empty rather than using cached values.

Reduced snapshots leave omitted fields unavailable. History-only samples do not replace the full-snapshot timestamp. Live resume resets CPU/network baselines, so the first counter-based rate waits for the next sample. A dynamic screen receiver is registered while consumers exist. Screen-off removes the overlay and cancels live work, including interruptible Root waits; screen-on restores enabled consumers. No background Root authorization is requested.

## Validation and phone measurements

Automated checks cover all UI/service/screen/overlay/history combinations, history deadlines and resets, the fixed reduced Root script, CPU resume baselines, and cancellation/cleanup of a blocked synthetic Root process. Existing FPS protocol tests remain in place. Synthetic processes/counters are not device measurements.

Session JVM regressions cover compressed reading round-trips, missing/invalid values, zero and signed power, sample means, bounded previews with missing-data breaks, and background/screen-off demand. `PerformanceStoreTest` is an Android instrumentation test for database reopen, interruption recovery and post-Finish rejection; compiling it is not executing it. Run it on an authorized device/emulator with `./gradlew :app:connectedDebugAndroidTest`. No phone gameplay or overlay visual validation has been performed for 0.5.0.

Before claiming measured savings, compare the same phone, ROM, sampling settings, Root mode and workload against the prior APK:

1. Record 15–30 minutes each of idle background history, static overlay, moving/game overlay and screen-off history. Repeat with history and overlay off.
2. Use `adb shell dumpsys cpuinfo`, `adb shell dumpsys meminfo org.libremonitor.app` and a Perfetto trace for process scheduling, wakeups and process creation. Keep measurement cadence identical and account for its overhead.
3. Check exported CSV timestamps, row counts and missing values. Toggle screen, rotate/resume, stop from the notification and revoke overlay/notification permissions. Verify no duplicate records or sampling after demand disappears.
4. Verify no new Root sampling processes during screen-off, restored FPS discovery after screen-on, fresh CPU/network baselines, and no burst of catch-up history rows.

No before/after phone trace, RSS comparison or battery drain measurement has been collected for this optimization.
