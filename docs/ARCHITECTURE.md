# Architecture

Current as of 0.4.2-dev.

## Data path

```
public Android APIs + fixed readable proc/sysfs nodes
                    |
        OneShotRootReader (optional, user-initiated)
                    |
           AndroidSampler (serial IO)
                    |
     MonitorRuntime (process-owned single loop)
           /             |             \
 Compose dashboard   SQLite history   overlay TextView
                           |
                   system-picker CSV
```

`core` is pure Kotlin. A `Reading<T>` carries source, availability state, nullable value and reason. A zero is valid only when actually observed. A missing source is not simulated with app CPU, screen refresh rate, an arbitrary thermal zone or another unrelated proxy.

## UI

Bluetooth uses Android public APIs only: `BluetoothManager`/`BluetoothAdapter` for adapter state, `bondedDevices` for paired devices, `BluetoothClass`/`BluetoothDevice` for type/Class/services and known UUID-to-profile mapping. `BLUETOOTH_CONNECT` is a runtime permission; without it the card offers an explicit authorization action. Core Bluetooth version, per-device live RSSI and per-device battery are not exposed reliably by the public API and remain `—`.

The Compose UI is an icon-led dashboard. Primary cards show a label, a large value and its unit; source, status, failure reason and sample time are shown in a details bottom sheet. CPU has a circular gauge and per-core bars; battery has a circular gauge and metric cards. Material Rounded icons come from `androidx.compose.material:material-icons-extended`.

The foreground freshness clock ticks only while the lifecycle is STARTED. Wall time is used for display; freshness uses monotonic elapsed time.

## Root backend

`OneShotRootReader` is the current backend for the tested KernelSU device. Each operation runs one fixed `su -c` command; there is no interactive root shell. The user must explicitly enable Root in the foreground, and a separate confirmation is required before fixed app-scoped permission grants.

- The fixed frame is defined by `RootProtocol.compactNodes()`: `/proc/stat`, eight cpufreq nodes, 32 thermal zones, battery/bms fields and KGSL nodes. This is a compact frame, not a promise that every device has the same cores/sensors.
- Identity verification requires actual UID 0 plus capability bits. It deliberately does not require a populated `uid_map`; the tested Android 17/KernelSU environment exposes an empty one.
- A frame has a bounded size, a completion marker and a monotonic read deadline. Timeout, truncation or a missing marker produces no frame rather than partial data.
- Root permissions are limited to four fixed app-scoped commands after confirmation: notification grant, overlay app-op, battery-optimization whitelist and Bluetooth-connect permission. No arbitrary command/path API exists.
- `RootReader` (persistent-shell implementation) remains for protocol tests and experiments, but `MonitorRuntime` uses `OneShotRootReader`.

FPS uses the same operation mutex and sampling loop. `FpsProtocol` executes a fixed read-only discovery/probe script, normalizes Android 16+ debug wrappers, probes at most four foreground-package candidates, and decodes a nonce-delimited, bounded response. `FpsTracker` uses `System.nanoTime()` / CLOCK_MONOTONIC for SurfaceFlinger timestamps (not elapsedRealtime/boottime), rejects old or non-advancing history, and resets at Root-session changes. Long intervals are retained. Statistics cover only the recent available history, not a continuous benchmark; see [FPS.md](FPS.md). Available FPS samples carry a Root-session ID, revalidated in the sampler and on Main immediately before publication; manual Root stop clears the displayed FPS. The script uses advertised 250ms service timeouts or legacy one-second timeouts; its eight-second whole-process wait budget covers the worst-case seven legacy calls plus overhead, excluding possible stream-join/cleanup delay. Slow FPS reads can trigger the existing basic fallback for node frames older than two seconds.

## Time and cancellation

- CPU uses cumulative counters and interval deltas. Guest counters are not added twice; iowait is treated as idle and documented.
- Network rates use monotonic elapsed time, reject counter resets and missing baselines, and do not infer billing or per-app usage.
- The main sampler is controlled by the union of visible Activity owners and foreground-service presence. It is not duplicated by UI, chart, history or overlay.
- Sampling interval changes apply at the next loop delay without cancelling a committed history write.
- A short accepted history transaction plus cadence checkpoint is non-cancellable. Stop blocks new acceptance; an already accepted transaction may finish.
- A history generation prevents an older query result from refilling the UI after clear.
- The foreground UI's freshness clock stops when the lifecycle drops below STARTED.

## Settings and opt-in state

SharedPreferences decoding/writes run in an IO command queue. `loaded` distinguishes an actual persisted read from the initial default StateFlow value. Explicit in-process history intent remains authoritative over delayed preference events, including rapid false/true/false toggles. This is intentionally not an acknowledgement protocol based on Boolean equality.

History is off by default. If the process is stopped, no boot receiver/watchdog restarts it. Deliberate app opening may restore a previously enabled history setting only after notification requirements pass. Notification/channel denial closes persistent functions; overlay denial closes only overlay if history can remain visibly active.

## Android service and permissions

Continuous monitoring uses a `specialUse` foreground service with an explicit on-device monitoring subtype and a persistent Stop action. It does not hold a wake lock. Notifications are checked centrally and while running; overlays need a separate system grant. Pending permission actions survive Activity recreation using saveable enums/flags rather than captured lambdas.

No internet, phone-state, location, broad storage, package-query or boot-autostart permission is requested. Root is separately authorized and is not counted as an ordinary app permission. CSV uses the system document picker and serializes opening/truncating the selected URI as well as database export.

## Storage

SQLite v1, nullable measurements, chronological queries with stable row IDs, timestamp index, time-based seven-day cleanup and a 50,000-row cap. UI uses at most 360 recent rows. CSV streams rows with bounded memory, UTF-8, units in headers, missing values empty and formula-sensitive strings protected. Clear requires UI confirmation. No destructive schema upgrade path is provided; later changes require explicit migrations.

## Extension strategy

Shizuku is not implemented. Adding it later requires a narrow provider interface, per-metric capability probing, versioned IPC, allowlisted operations, explicit authorization and device tests. Cross-app FPS, GPU and modem APIs must not be added as if public APIs universally expose them. Future modules should not make the basic offline build depend on vendor binaries or accounts.
