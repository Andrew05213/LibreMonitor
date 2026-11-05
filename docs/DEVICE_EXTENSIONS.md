# Device fields and overlay — 0.6.0-dev

## Overlay

Settings → 悬浮窗 selects and orders CPU/GPU/memory/FPS, battery-terminal power, CPU/GPU frequency, CPU/battery temperature, battery level and aggregate network download/upload. At least one metric is retained. One/two columns, 12–24sp numbers, 30–100% background opacity and edge snap are persisted through the existing preferences IO queue. Rapid setting changes keep the latest in-process overlay intent authoritative over older queued persistence events.

Drag the handle to a physical left/right edge and release to collapse into a translucent semicircular arrow. Tap the arrow to expand; drag it to move along or across edges. Long-press the expanded handle to collapse to the nearest edge; ordinary handle taps return to the app. Close hides the overlay without ending an explicitly started performance session. Rotation and safe insets constrain the window, and a scroll container handles many fields or large fonts. Dock position survives hide/show within the service lifetime; geometry is not persisted across service restarts.

One shared runtime still owns sampling. Selected hardware details enable existing compact reads; selected network rates enable the existing counter collector. Default overlay reads remain the fixed four CPU/KGSL nodes. This does not establish measured 10Hz throughput or phone visual/gesture acceptance.

## CPU identity and maintained catalog

`app/src/main/assets/soc_catalog.tsv` is an independently maintained, seven-column data table: comma-separated exact aliases, display name, vendor, prime count, performance count, efficiency count and reference URL. Add a unique exact SoC identifier plus its product reference; invalid rows or duplicate aliases are rejected by the parser. Board codenames are not used as ambiguous marketing-name guesses. The current seven families are SM8350, SM8450, SM8475, SM8550, SM8650, MT6985 and MT6989.

Android 12+ `Build.SOC_MODEL` is preferred. Unrecognized systems retain a raw hardware identifier. Per-core `/proc/cpuinfo` ARM implementer/part IDs determine architectures and tiers, with explicit logical IDs. If architecture is unknown, three distinct positive `cpu_capacity` levels may provide **labeled capacity inference**. Uniform or insufficient evidence remains unknown. The catalog's reference core counts are shown separately and never assigned blindly to logical IDs. Snapdragon 8 Gen 2's reference layout is 1 prime / 4 performance / 3 efficiency cores, with Cortex-X3/A715/A710/A510 identities when the kernel exposes them.

CPU identity is static metadata loaded with DeviceInfo; it does not create another sampling loop or imply calibrated clock/temperature measurements.

## Bluetooth

| Field | Actual scope |
|---|---|
| PAN download/upload | Difference of bounded `/proc/net/dev` bnepN / bt-pan[N] counters against monotonic time. Optional fixed Root read in the full frame; no interface or denied counters stay unavailable. These are PAN network rates, not A2DP/HFP audio rates. |
| Protocol labels | BluetoothClass and recognized service UUID capabilities (A2DP/HFP/HSP/AVRCP/HID/PAN/BAS and known Apple service UUID). They do not prove a negotiated codec/version. |
| LE 2M/Coded PHY | Public adapter capabilities, not a measured current data rate. |
| Current audio codec / core version | Remain explicitly unavailable through ordinary public Android APIs. No hidden API or reflection is used. |
| AirPods / Pro battery | Public BLE advertisements, known Apple 0x004C / proximity 0x07 paired-format records. Supported IDs cover AirPods 1/2/3, AirPods Pro and Pro 2. Values are coarse ten-percent bins and labeled approximate; 0xF/invalid bins stay unknown. |

Use the explicit scan button while the app is visible. On Android 12+ it requests nearby-device permissions; location permission is also requested for beacon visibility, and older Android may require the system location switch. The app does not query location coordinates. The scan stops after 30 seconds or when leaving the foreground, and observations expire after 60 seconds. Opening the charging case may be necessary. Walk all advertisement AD structures so duplicate Apple entries do not overwrite proximity records. Reject unknown/truncated frames rather than inferring a model or battery.

An observation can populate a paired row only for the same Bluetooth address. Rotating/private addresses are shown separately as nearby broadcasts, never matched by name or RSSI. No raw advertisements, observed addresses or scan history are persisted. No GATT control, pairing, AAP connection, audio capture or automatic background scan is performed. Phone verification with the user's AirPods firmware is pending.

## Network, hotspot and system state

The visible dashboard samples metadata in the shared loop with a five-second cache and explicit source timestamps. It includes default transports, system internet validation, metering, operator, SIM state, roaming, data radio type, data-enabled state, aggregate signal level, Wi-Fi state/link rate/frequency/RSSI/concurrency capabilities, Android/API/security patch, kernel, uptime, power-save/idle/interactive/thermal state and airplane mode. Phone data uses the default data subscription when available. LTE can still describe a 5G NSA connection; the app does not label this as measured standalone NR. A Wi-Fi negotiated link rate is not measured transfer throughput. No active internet probe, cell scan, phone number, IMEI or location query is used.

`ACCESS_NETWORK_STATE` and `ACCESS_WIFI_STATE` are normal read permissions. `READ_PHONE_STATE` is requested only by its manual button; denial keeps affected fields unavailable. Bluetooth scans separately request scan/connect/location permissions; no background location permission is declared, and the existing four Root permission-grant commands are unchanged.

Ordinary Android apps cannot reliably inspect the shared-hotspot switch/client list across ROMs. With an already user-enabled Root session, the visible dashboard may run a **fixed** `dumpsys wifi` filter at the five-second metadata cadence, with a three-second deadline. The filter returns only `AP_BEGIN`, known `STATE` values and numeric `CLIENTS` derived from SoftApManager output. It excludes unrelated managers and never returns SSIDs, configurations, credentials, MACs or client identities. Unsupported output remains unknown, including an absent manager; absence is not interpreted as hotspot-off or zero clients. SoftAP may include local-only hotspots, so the UI does not equate every SoftAP with internet tethering.

The high-frequency compact frame adds only the fixed bounded `/proc/net/dev` counter (103 total nodes); the reduced default overlay frame stays at four nodes. The separate diagnostic profile is never called for hidden performance recording, battery-only history or idle consumers. No modem traffic, packets, system settings or other apps are modified.

## Evidence and remaining phone checks

Synthetic regressions cover exact catalog matching and ARM IDs, real catalog validation, malformed settings/order/clamping, docking geometry, known/unknown/truncated/duplicate proximity payloads, valid-zero versus missing battery, PAN counter resets, unknown SoftAP states and a shell fixture proving SSID/key/address/unrelated-manager exclusion. Android instrumentation tests compile separately; compilation is not device execution.

Before claiming phone acceptance, verify edge drag/tap/long-press and rotation on portrait/landscape/cutout devices, all customization persistence and rapid changes, denied scan/phone permissions, real AirPods Pro advertisements, actual PAN transfers, hotspot/local-only differences across ROMs, and monitoring overhead during games. Existing performance/battery databases and the seven-column battery CSV are unchanged.
