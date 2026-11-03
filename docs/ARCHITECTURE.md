# Architecture

## Data path

```
public Android APIs + fixed readable proc/sysfs nodes
                    |
           AndroidSampler (serial IO)
                    |
     MonitorRuntime (process-owned single loop)
           /             |             \
 Compose/lifecycle   SQLite history   overlay TextView
                           |
                   system-picker CSV
```

`core` is pure Kotlin. A `Reading<T>` carries source, availability state, nullable value, and reason. A zero is valid only when actually observed. A missing source is not simulated with app CPU, screen refresh rate, an arbitrary thermal zone, or another unrelated proxy.

## Time and cancellation

- CPU uses cumulative counters and interval deltas. Guest counters are not added twice; iowait is treated as idle and this convention is documented.
- Network rates use monotonic elapsed time, reject counter resets and missing baselines, and do not infer billing or per-app usage.
- The main sampler is controlled by the union of visible Activity owners and foreground-service presence. It is not duplicated by UI, chart, history or overlay.
- Sampling interval changes apply at the next loop delay without cancelling a committed history write.
- A short accepted history transaction plus cadence checkpoint is non-cancellable. Stop blocks new acceptance; an already accepted transaction may finish.
- A history generation prevents an older query result from refilling the UI after clear. The database remains the source of durable records; diagrams do not fabricate missing points.
- The foreground UI's freshness clock ticks only while lifecycle STARTED. Wall time is for timestamps; freshness uses elapsed time.

## Settings and opt-in state

SharedPreferences decoding/writes run in an IO command queue. `loaded` distinguishes an actual persisted read from the initial default StateFlow value. Explicit in-process history intent remains authoritative over delayed preference events, including rapid false/true/false toggles. This is intentionally not treated as an acknowledgement protocol based on equality of Boolean values.

History is off by default. If the process was stopped, no boot receiver/watchdog restarts it. Deliberate app opening may restore a previously enabled history setting only after notification requirements pass. Notification/channel denial closes persistent functions; overlay denial closes only overlay if history can remain visibly active.

## Android service/permissions

Continuous monitoring uses a `specialUse` foreground service with an explicit on-device monitoring subtype and a persistent Stop action. It does not hold a wake lock. Notifications are checked centrally and while running; overlays need the separate system grant. Permissions and pending requested actions survive Activity recreation using saveable enum/flags rather than captured lambdas. Multiple started Activity instances cannot incorrectly clear another owner's visibility.

No internet, phone-state, location, broad storage, package enumeration or root permission is requested. No arbitrary shell endpoint is exposed. CSV uses the system document picker and serializes opening/truncating the selected URI as well as database export.

## Storage

SQLite v1, nullable measurements, chronological queries with stable row IDs, timestamp index, time-based seven-day cleanup and a50,000row cap. UI uses at most360recent rows. CSV streams rows with bounded memory, UTF-8, units in headers, missing values empty, and formula-sensitive strings protected. Clear requires UI confirmation. No destructive schema upgrade path is provided; later changes require explicit migrations.

## Extension strategy

Shizuku and Root are currently unavailable capability entries, not active backends. Integrating Shizuku API/libsu requires a new narrow provider interface, per-metric capability probing, versioned IPC/allowlisted operations, user authorization, and actual device tests. Cross-app FPS, GPU and modem APIs must not be added as if public APIs universally expose them. Future modules should not make the basic offline build depend on vendor binaries or accounts.
