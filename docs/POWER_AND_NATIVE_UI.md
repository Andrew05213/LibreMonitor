# 0.7.0-dev: native navigation, power units and fast overview

Branch `fix/power-and-overlay-settings` builds on `fix/fps-reliability` (`4fab534`). Earlier performance/FPS branches remain unchanged.

## Battery-terminal power

The screenshots show approximately 3.93V and -2mA/-0.01W in LibreMonitor versus 3.935V, 1721mA and 6.8W in another app. A unit mismatch is consistent with this discrepancy; screenshots alone do not establish the actual phone ROM's raw interfaces or current direction.

Live electrical readings now prefer a complete voltage/current pair from the same fixed `battery` or `bms` supply. The supply must identify as Battery; explicitly device-scoped, absent or malformed-presence supplies are rejected. The collector never combines one supply's voltage with another's current. Root results must be within two seconds and the sampler revalidates the Root session before publication. Current, voltage and presence are read every detailed cycle; none use the 30-second metadata cache. Battery-only history uses bounded ordinary reads and never starts a Root/FPS process.

Automatic current units follow the standard µA convention except for the exact `battery/model_name = oplus battery` interface marker. The referenced Oplus GKI battery getter forwards the gauge's mA value unchanged; its voltage getter uses µV. The independently implemented marker profile uses mA without deciding units from the numeric magnitude. Unknown interfaces remain standard µA; a persisted Settings → 电流单位 choice permits explicit µA or mA interpretation. No brand-wide, SoC, small-number or dual-cell multiplier heuristic is used.

For a synthetic identified Oplus response of 3,935,000µV and -1721mA, power is -6.772135W. Zero remains valid; invalid/denied/missing pairs never become zero. Signed current is preserved, so a negative value is not automatically labeled discharge. Power remains an uncalibrated reading of the declared battery interface, not charger input, CPU/SoC consumption or a verified full-pack mapping.

The only new fixed Root paths are `battery/model_name` and `bms/model_name`. Raw Android current, supply nodes, selected units and source reasons are included in explicit local JSON diagnostics. No serial number, external upload, general filesystem API or new permission is introduced. Database schemas and the seven-column battery CSV are unchanged.

## Actual refresh cadence

The old serial path waited for SurfaceFlinger before publishing every CPU/GPU/memory snapshot. A selected 100ms interval could therefore still produce roughly two-second updates.

At target intervals of one second or less, the shared engine now starts at most one pending FPS query, with no independent FPS polling loop. One fixed FPS process can overlap one fixed fast node process; separate bounded locks prevent the slow FPS process from occupying the fast-frame mutex. At 2/5/10-second targets it retains synchronous fresh FPS reads, rather than consuming results that have already expired while waiting for the next slow tick. Authorization, session validation, nonce framing and cancellation remain in force. Pausing demand, switching modes or disabling Root cancels the pending query and rejects late results.

The overview requests its hardware/network data rather than Bluetooth inventories, storage or system/hotspot probes. Those remain on their dedicated function pages. The header shows actual successive sample-start intervals, not the selected target. Node/process/driver cost can still exceed 100ms; no measured phone throughput or battery-life claim is made.

FPS can display a recent result for the existing two-second freshness window. Its original acquisition time and reused status remain visible in details/diagnostics. Reused values are saved as explicitly not resampled, excluded from valid FPS counts and failure counts, and never inflate the headline. FPS success denominators count actual completed queries, while total CPU/memory recording points keep their own counts. Old version-1 session payloads still decode without migration.

## Native UI and overlay controls

The main navigation is 功能 / 概览 / 设置. Function rows lead to hardware, battery, performance recording, overlay, network, Bluetooth, system and diagnostics pages; child pages use a Back action. The independent Material3 layout uses green accents and system dark/light mode, with no imported OpenMonitor code, logos or assets.

Explanatory paragraphs are removed from main screens and retained in data details where needed; data labels, units and meaningful missing/interrupted states remain. Overlay settings use compact rows for fields, order, appearance and edge snapping. Appearance controls are grouped in a sheet with continuous, zero-step sliders for normal/docked background opacity and font size. Values persist as Float, and legacy Int preferences migrate on load. Appearance previews update the existing window in place; disk writes occur on release/dismissal, not every drag event.

## Validation boundary

Automated tests exercise known versus unknown current units, explicit overrides, signed/zero/malformed values, same-supply pairing, accessories/presence, Root-frame freshness, independent live reads, bounded FPS jobs, late cancellation, concurrent fast Root reads during blocked FPS, unique FPS recording, and fractional/legacy overlay settings. Android-only SQLite/JSON tests are compiled separately. No phone installation, emulator/UI screenshot test or actual-device calibration has been performed for this branch.
