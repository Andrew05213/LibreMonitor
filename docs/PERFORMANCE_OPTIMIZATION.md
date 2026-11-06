# Sampling pipeline optimization — unreleased

These changes preserve the single `MonitorRuntime` sampling engine and the existing database/CSV schemas. FPS layer selection and device diagnostics are tracked separately on `fix/fps-reliability`.

- Sampling demand follows the current page and expanded overlay fields. Settings and historical performance review do not request live hardware reads by themselves. A collapsed edge arrow pauses overlay-only sampling; explicit performance recording or battery history continues according to its existing screen policy.
- FPS is skipped when neither the overview, an expanded FPS field nor performance recording needs it. Appearance changes no longer cancel sampling or reset rate baselines.
- One-shot Root uses subsets of the existing constant allowlist. Discovery reads thermal labels and battery metadata at most every 30 seconds or after a Root session change. Steady frames retain current CPU counters/frequencies, GPU readings, optional network counters and discovered temperatures. For example, a hidden recording with two discovered thermal zones reads 14 nodes instead of 103. Actual counts depend on the device; this is not a timing or battery-life measurement.
- Public thermal discovery and battery capacity/cycle metadata use the same bounded 30-second policy. Cached metadata is annotated; current, voltage, power, charge level, temperature, frequencies and utilization are never reused as fresh values.
- Root Wi-Fi diagnostics pause during performance recording and sub-second sampling. The overview still refreshes public system/network fields on its five-second cache, and explicitly explains why Root hotspot fields are unavailable.
- A single IO writer commits at most ten points per transaction or targets a one-second flush after the first pending point. Its queue holds 20 points, with at most ten more in the current batch. A slow disk applies backpressure; no unbounded queue or silent dropping is used. Normal Stop drains accepted points before ending a session. A process kill can lose uncommitted points; failures stop recording and explicitly report that risk. The active counter includes accepted, possibly not-yet-committed points.
- Review reads a fixed session snapshot in chunks of 128 payloads, releasing the writer mutex before decompression and aggregation. Summary statistics still cover all persisted points; timeline gaps and preview bounds remain unchanged.
- Overlay opacity updates in place. Sliders save on release, collapsed panels skip metric formatting, unchanged snapshots are not reformatted, and expanded panels calculate only selected fields.

## Validation

Automated checks cover demand/field changes, screen-off and collapsed overlays, fixed Root profiles, metadata invalidation, live-temperature/electrical freshness, ordered batch draining, sparse-sample flushing, bounded backpressure and write failure reporting. Android-only SQLite tests cover multi-batch reviews and post-stop rejection; compilation is not device execution.

No phone performance measurement is available. A 100ms setting remains a target, not a throughput guarantee. Device acceptance must measure actual active-screen point intervals, Root/query time, disk time and CPU/battery cost without counting intentional screen-off gaps as failed samples.
