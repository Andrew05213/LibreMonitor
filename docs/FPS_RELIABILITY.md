# FPS reliability and one-action diagnostics — unreleased

Branch: `fix/fps-reliability`, based on the sampling optimization commit `27138c4`.

## Failure evidence and scope

The user's screenshots show a 42:26 session with 194 saved points: memory 194/194 valid, CPU 184/194 and FPS 1/194. Later screen-off gaps were intentional pauses. The old final reason was “此图层没有可用帧时间”; this identifies the last response as lacking parsed actual-present timestamps, not every earlier failure. A 120.8 FPS sample by itself cannot describe the whole game or identify which application/layer produced it.

A synthetic shell fixture reproduced a concrete selection defect: four empty matching layers permanently excluded an active fifth layer. This is code-level evidence, not a confirmed diagnosis of the phone's compositor.

## Query behavior

- Discovery now orders SurfaceView, then BLAST, then other matching layers and rotates through bounded pages of four, at most 64 matching layers. Empty/stale candidates no longer permanently pin discovery to the same first four entries. Discovery resets its cursor when the focused package changes.
- A successful, validated layer may be reused for up to five seconds. Each query still checks the current foreground package; a different application triggers discovery instead of querying the old application's layer. Empty, denied, timed-out or non-advancing cached results trigger rediscovery on the next poll. A periodic full selection check also expires the cache.
- The supported dumpsys timeout flag is cached within the Root session; authorization, explicit Root close and failed exchanges clear query state. Timeouts remain 250ms per modern dumpsys service call or one second on legacy systems; the existing eight-second process wait bound remains.
- Layer names are bounded, validated against the focused package and encoded/quoted as literal data. No new command service, arbitrary path API, global compositor counter, statistics reset or privilege grant is added. The refresh-period header is never converted into FPS.
- Empty response, header only, zero/pending frames, unrecognized output, permission denial and timeout have distinct explanations/codes. Denied or timed-out partial data cannot become an available sample. An empty container no longer masks a non-advancing actual surface's reason.

## Diagnostics and interpretation

The Performance page has “导出采集诊断”. The system document picker saves a JSON file at the user's chosen location. The app neither uploads nor sends it. Export performs no additional Root reads and needs no new permissions.

The JSON includes bounded in-memory run counters, failure frequencies and the latest 120 successful snapshot cycles. It records target versus actual start intervals, foreground mode, Root/query/other-collector timing, queue wait and successful batch-write durations (including compression and DB-lock wait). Interval aggregates exclude screen-off and demand-plan changes; mixed target intervals are visible in recent traces. Background batch writes have their own totals rather than being counted as synchronous sampler IO. General collection and write failures are explicit.

FPS attempts include the focused package, candidate page, cached-query flag, selected/candidate layer names, timestamp count/newest actual-present timestamp, the matching CLOCK_MONOTONIC sample time, response size and at most 1,536 characters of each of four `--latency` responses. The ring retains counts and newest timestamps, not full repeated frame histories. The trace ring, failure/target maps and candidate counts are bounded. No general window/activity/Wi-Fi dump, Bluetooth record, network packet, phone number or hardware identifier is exported. Package/layer names can identify games and other foreground apps; the UI discloses this before export.

Run diagnostics reset after process exit. Saved session payloads already contain reasons and target layers: review now aggregates FPS failures, all observed targets and the targets associated specifically with valid FPS samples from all saved points, including older version-1 records. Export includes these for the selected session, so a lone 120.8 FPS sample can be traced to its recorded target rather than assumed to belong to the game. No database or payload-schema migration is needed. Bounded maps use an “other” bucket after 64 distinct keys.

The headline is labeled “FPS 样本均值”. With fewer than ten valid FPS samples or under 50% sample coverage it displays “样本不足”; raw valid-sample statistics remain available below. This is a display heuristic, not a confidence interval or a game-identification guarantee. Even high coverage still summarizes overlapping compositor windows, not a continuous or time-weighted whole-game benchmark.

## Phone follow-up

1. Use the test build with the existing opt-in Root/recording controls, and play normally until the issue appears. No command-line work is needed.
2. End and save the session, open its review, and tap “导出采集诊断” on the Performance page.
3. Share that JSON manually for analysis. The file distinguishes candidate/interface failures, stale results, slow Root reads and write/backpressure problems without treating deliberate screen-off gaps as failed sampling.

Automated fixtures are not phone measurements. The current implementation remains a best-effort focused-layer SurfaceFlinger reader; a ROM may expose no usable per-layer presentation timestamps. Do not claim the user's game is fixed until its exported device evidence confirms it.
