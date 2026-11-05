# Background resource usage

The unreleased optimization is based on the 0.4.2 FPS fix (PR #1 head `79efe22`). One process-owned loop selects work from consumers:

| Demand | Work | Cadence |
|---|---|---|
| Interactive, visible Activity | Full snapshot | UI refresh (default 2s) |
| Interactive, background overlay | Aggregate CPU, memory, public battery and FPS | UI refresh |
| Background history, or screen-off with history enabled | Public battery APIs only | History interval (default 60s) |
| Screen-off overlay without history, or no consumers | None | No sampling timer |

The overlay skips thermal/per-core frequency, GPU, network, storage and Bluetooth collection. Its privileged frame contains the fixed `/proc/stat` node, compared with 102 nodes in the full compact frame. FPS still probes the current foreground surface at each live overlay sample. The configured visible refresh rate and FPS semantics are preserved.

With defaults, an idealized hour of background history schedules 60 battery samples instead of approximately 1,800 full snapshots: 30 times fewer sampling cycles and zero Root frame/FPS processes in that mode. These are deterministic work counts, not measured CPU, memory or battery percentages. Doze, process death and slow collectors can delay samples; no wake lock, alarm or boot receiver was added.

History deadlines use monotonic elapsed time and survive consumer/screen changes. Only successful inserts advance the ledger; an accepted insert may finish after Stop. Background recording does not repeatedly decode the latest 360 rows. Opening the UI reloads them. SQLite v1 and the seven-column CSV are unchanged. Public API readings may be unavailable on an OEM that previously required Root; fields remain empty rather than using cached values.

Reduced snapshots leave omitted fields unavailable. History-only samples do not replace the full-snapshot timestamp. Live resume resets CPU/network baselines, so the first counter-based rate waits for the next sample. A dynamic screen receiver is registered while consumers exist. Screen-off removes the overlay and cancels live work, including interruptible Root waits; screen-on restores enabled consumers. No background Root authorization is requested.

## Validation and phone measurements

Automated checks cover all UI/service/screen/overlay/history combinations, history deadlines and resets, the fixed reduced Root script, CPU resume baselines, and cancellation/cleanup of a blocked synthetic Root process. Existing FPS protocol tests remain in place. Synthetic processes/counters are not device measurements.

Before claiming measured savings, compare the same phone, ROM, sampling settings, Root mode and workload against the prior APK:

1. Record 15–30 minutes each of idle background history, static overlay, moving/game overlay and screen-off history. Repeat with history and overlay off.
2. Use `adb shell dumpsys cpuinfo`, `adb shell dumpsys meminfo org.libremonitor.app` and a Perfetto trace for process scheduling, wakeups and process creation. Keep measurement cadence identical and account for its overhead.
3. Check exported CSV timestamps, row counts and missing values. Toggle screen, rotate/resume, stop from the notification and revoke overlay/notification permissions. Verify no duplicate records or sampling after demand disappears.
4. Verify no new Root sampling processes during screen-off, restored FPS discovery after screen-on, fresh CPU/network baselines, and no burst of catch-up history rows.

No before/after phone trace, RSS comparison or battery drain measurement has been collected for this optimization.
