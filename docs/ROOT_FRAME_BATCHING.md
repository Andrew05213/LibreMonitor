# 0.7.1-dev: batch Root transport after device diagnostics

Branch `perf/root-frame-batching` builds on `fix/power-and-overlay-settings` (`99eb3d4`). Prior branches remain intact.

## Evidence and limits

The user supplied an explicit local diagnostic export from **0.6.0-dev**, not 0.7.0. During the selected 65-point game session, only two saved FPS readings were valid and 57 were rejected because subsequent hardware collection exceeded the two-second freshness window. The game layer itself did return usable frame timestamps. In the recent recording cycles, median Root node collection was **3044ms**, median compositor query time **915ms**, and other collectors approximately **53ms**. Recorded batch writes averaged approximately **10ms** across this export, with no write failures. This implicates the serialized collection path and costly node transport; it does not establish universal SurfaceFlinger incompatibility or slow storage.

Those measurements are historical 0.6.0 evidence. They neither measure the later nonblocking 0.7.0 path nor prove the throughput of this change. There are no raw battery electrical diagnostics in that export, so it cannot validate the 0.7.0 Oplus current-unit correction. The original export, application/package targets, surface names and other private contents are not committed or uploaded.

## Changes

The one-shot Root backend previously ran a subshell, bounded `head`, `base64` and `tr` separately for each node. It now retains one bounded `head` per fixed node, uses shell builtin markers, and encodes the entire packet through one `base64` and one `tr`. This removes repeated encoding processes without adding a persistent privileged daemon, executable, dependency, general shell interface or new node path. No claim about a numerical phone speedup is made from command counts.

The new `batched-v1` packet contains a UID record, ordered nonce/index-prefixed NUL-separated node records and an inner end marker. Its outer Base64 line and end marker carry the same random session operation nonce. The decoder checks UID 0, exact record order/count, delimiters, both end markers, no trailing data, valid Base64/UTF-8, the existing 512KiB frame budget and each node's existing limit plus one truncation sentinel. Empty/denied/malformed/truncated values stay unavailable; they never become partial readings or zero. Unexpected embedded NUL bytes invalidate the entire packet. The legacy per-node protocol remains for the experimental persistent reader and its regression fixtures.

Steady detailed frames request temperatures only for sensor types already recognized by the existing narrow CPU/CPUSS/cluster whitelist. Discovery still checks the same 32 fixed zones every 30 seconds. Unknown sensor types are not relabeled or guessed. Frequency, GPU and same-supply live voltage/current/presence stay fresh each detailed cycle. Cached metadata is still explicitly annotated.

At targets of one second or less, the shared engine also consumes any FPS query completed during node collection before publishing the snapshot. A second poll preserves a fresh result consumed before node collection when no newer query has completed. Original acquisition times, unavailable outcomes, Root-session validation and unique-recorded-reading flags are preserved. At most one query remains pending, driven only by the shared engine; there is no new polling loop. Slow target intervals retain their synchronous path.

Explicit JSON exports now include `root_node_count` and `root_transport`. The node count describes actual commands rather than merged cached metadata. These additions do not start queries or change the database/CSV schema.

## Validation

See [validation-report-v071.json](validation-report-v071.json) and [STATUS.md](STATUS.md) for completed build results. Host tests cover generated shell/Base64 round trips, multiline/UTF-8 data, limits, identity, nonce/order/count/delimiter failures, modified command rejection, discovered CPU-only steady profiles, concurrent FPS/node operations, and result consumption around node I/O. Host shell fixtures substitute only executable lookup and identity output; they do not run Root or measure a phone.

Actual 100ms throughput, power accuracy and device compatibility still require the new APK on the phone. The app reports observed intervals; 100ms remains a target. No benchmark, battery calibration, emulator/UI run or Android instrumentation execution is claimed.
