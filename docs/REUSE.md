# Reuse and license decisions

Research date: 2026-11-03. Research was done against original repository sources/license files and public Maven metadata. Research is not a claim of runtime compatibility. No OpenMonitor or other monitoring app source was copied into LibreMonitor.

## v0.2 implementation

The optional Root reader is an independent Apache-2.0 implementation using platform Java/Android and the existing coroutines dependency. It reads only a fixed bounded allowlist after explicit user authorization; no libsu, Shizuku, or other new dependency was added in v0.2. Availability and sensor semantics require device-specific validation.

## Adopted in the MVP

| Component | Pinned version | License / provenance | Decision |
|---|---|---|---|
| Android platform APIs (BatteryManager, ActivityManager, TrafficStats, StatFs, SQLiteOpenHelper, SharedPreferences, WindowManager) | SDK35, min26 | Android SDK terms for build tools; AOSP API implementation provenance | Use public APIs, app-private data, and only readable fixed proc/sysfs nodes. |
| AndroidX Compose BOM | 2024.12.01 | Apache-2.0, https://github.com/androidx/androidx/blob/androidx-main/LICENSE.txt | Curated stable baseline, not a claim of latest; UI/foundation/Material3 and Canvas only. |
| AndroidX Material Icons Extended | Compose BOM 2024.12.01 / 1.7.6 | Apache-2.0, AndroidX above | Standard Material Rounded glyphs for the icon dashboard; no proprietary brand assets. |
| AndroidX Core | 1.15.0 | Apache-2.0, AndroidX above | Foreground service and compatibility helpers. |
| Activity Compose | 1.9.3 | Apache-2.0, AndroidX above | Native Compose entry point and system document/permission contracts. |
| Lifecycle Runtime Compose | 2.8.7 | Apache-2.0, AndroidX above | Lifecycle-aware StateFlow collection. |
| Kotlin and Compose compiler plugin | 2.1.21 | Apache-2.0, https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt | Compiler/plugin versions match. |
| kotlinx.coroutines | 1.9.0 | Apache-2.0, https://github.com/Kotlin/kotlinx.coroutines/blob/master/LICENSE.txt | Shared serial sampling, IO, cancellation, flows. |
| JUnit4 | 4.13.2 | EPL-1.0, https://github.com/junit-team/junit4/blob/main/LICENSE-junit.txt | Test-only dependency, not app runtime. |

AGP8.7.2 / Gradle8.9 / JDK17 / Kotlin2.1.21 / compileSdk35 is a conservative build baseline. Kotlin's official compatibility table lists this AGP/Gradle combination: https://kotlinlang.org/docs/gradle-configure-project.html . Compose compiler guidance: https://kotlinlang.org/docs/compose-compiler-migration-guide.html . We intentionally did not pin all libraries to the latest available release without checking their toolchain constraints.

## Evaluated, not included

- **Shizuku API 13.1.5 — MIT**: https://github.com/RikkaApps/Shizuku-API/blob/master/LICENSE . API license is MIT, unlike the Shizuku application itself (Apache-2.0). Future optional shell backend; requires separately installed and started Shizuku. No dependency, authorization prompt, or fake working Shizuku mode in this MVP.
- **libsu 6.0.0 — Apache-2.0**: https://github.com/topjohnwu/libsu/blob/6.0.0/LICENSE . Optional future root backend, not an escalation mechanism. Not included: the current one-shot backend uses a fixed `ProcessBuilder("su", "-c", ...)` command and contains no library dependency. A library may still be preferable for shell lifecycle/authorization handling in a broader backend.
- **Room / Preferences DataStore — Apache-2.0**: verified latest metadata listed 2.8.5 / 1.3.0 at research time. Deferred to avoid KSP/codegen and newer toolchain constraints for the small MVP. Platform SQLite/SharedPreferences meets current scope; migration needs versioned non-destructive paths.
- **Vico 3.3.1 — Apache-2.0**: https://github.com/patrykandpatrick/vico/blob/v3.3.1/LICENSE . More chart functionality than needed. Canvas keeps the small trend chart dependency-free.
- **CPU Info — Apache-2.0**: https://github.com/kamgurgul/cpu-info/blob/master/LICENSE . Useful reference for public device metadata/provider boundaries. No code copied; do not copy unavailable=-1 conventions or infer CPU temperature from arbitrary thermal nodes.
- **SoloPi — Apache-2.0**: https://github.com/alipay/SoloPi/blob/master/LICENSE . Performance capture architecture reference; older HWUI/FPS parsers are not universal for modern SurfaceView/Vulkan games. No source copied.
- **AppManager — GPL-3.0-or-later with per-file review**: https://github.com/MuntashirAkon/AppManager/blob/master/COPYING . No code or dependency imported into this Apache-2.0 project. Any future reuse requires a compatibility and distribution-obligations review.
- **Perfetto — Apache-2.0 plus third-party terms**: https://github.com/google/perfetto/blob/main/LICENSE . Future external trace analysis, not an embedded MVP dependency. FrameTimeline coverage is not all games/surfaces: https://perfetto.dev/docs/data-sources/frametimeline .

## GPU interface research

GPU interface research (2026-11-04): OnePlus's GPL kernel [kgsl_pwrctrl.c at 13ca08f](https://github.com/OnePlusOSS/android_kernel_oneplus_sm8350/blob/13ca08fdf0979fdd61d5e8991661874bb2d19150/drivers/gpu/msm/kgsl_pwrctrl.c) was inspected to verify the existing KGSL percentage suffix and busy/total ABI. No kernel implementation, monitoring-app code or binary was copied, linked or bundled. The parser uses independently written Kotlin and arithmetic; no GPL dependency was introduced. See [GPU.md](GPU.md).

## Device-interface research — 0.6.0

The following were read for interface facts only. No implementation or binary was copied or linked; all parsers, filters and catalog rows are independently written Apache-2.0 code/data:

- [Linux ARM64 CPU part definitions](https://github.com/torvalds/linux/blob/master/arch/arm64/include/asm/cputype.h) — GPL kernel header consulted only for factual implementer/part identifiers (including A715 `0xD4D`). No GPL source or dependency is imported.
- Official Qualcomm/MediaTek product references are recorded per row in `app/src/main/assets/soc_catalog.tsv`; the table stores exact model aliases, product names and reference tier counts, not vendor code/assets. Core assignment still requires runtime identity evidence.
- [Discontinued Privacy: Personal Data Leaks in Apple Bluetooth-Low-Energy Continuity Protocols](https://petsymposium.org/2020/files/papers/issue1/popets-2020-0003.pdf), [proximity research notes](https://github.com/fischejo/airpods-notify/blob/master/doc/proximity_protocol.md), and [podpower protocol description](https://github.com/t4t5/podpower#how-it-works) (MIT project) — manufacturer/type/length/model identifiers, battery nibbles and orientation/charging interface facts. No AAP control connection or project code is incorporated. Advertised ten-percent bins remain approximate rather than invented fine precision.
- [AOSP SoftApManager](https://android.googlesource.com/platform/packages/modules/Wifi/+/refs/heads/main/service/java/com/android/server/wifi/SoftApManager.java) — Apache-2.0 source inspected to verify dump field names and state names. Only a separately written fixed output filter and parser are included.
- Android public [WifiManager](https://developer.android.com/reference/android/net/wifi/WifiManager), [TelephonyManager](https://developer.android.com/reference/android/telephony/TelephonyManager), [BLE scanning](https://developer.android.com/develop/connectivity/bluetooth/ble/find-ble-devices) and [Bluetooth permissions](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions) documentation. No hidden API or manufacturer SDK is added.

## OpenMonitor is not a source base

https://api.github.com/repos/1orz/OpenMonitor/contents/ at research time listed documentation, icons, LICENSE, and .github but no application/daemon source or build project. README's OpenMonitor-src repository returned anonymous HTTP404. Root LICENSE is GPLv3, but this alone does not establish complete corresponding source for the observed APK or its paid features. 404 may indicate private/moved/unavailable; charging money itself does not violate the GPL. We use a feature-level comparison only, with no copied name, graphics, code, activation bypass, or proprietary binaries.

## Build-tool provenance

Google Linux SDK native binaries are commonly x86_64. The local ARM64 helper uses https://github.com/lzhiyong/android-sdk-tools/releases/tag/35.0.2 (third-party static AOSP/NDK build, not official Google ARM64). Downloads/provenance live in ignored `.toolchain/toolchain-provenance.json`. Google SDK package checksums and Gradle's official SHA256 are verified. When the third-party release has no supplied digest, a locally computed hash records bytes only; it is not independent authenticity verification. Run an actual resource build as well as `aapt2 version`.

Licenses are per component and version. Preserve applicable copyright, license and NOTICE files when redistributing; this document is an engineering inventory, not a substitute for a full release legal review or an automated transitive-dependency SBOM.


## Oplus battery interface research (0.7.0-dev)

OnePlusOSS public kernel interface facts were inspected to distinguish nonstandard mA exports from the normal Linux µA ABI. No GPL implementation code, function bodies or binaries were copied. The application implements its own bounded collector, explicit marker selection, source checks and unit conversions.

- [Oplus GKI battery interface](https://github.com/OnePlusOSS/android_kernel_modules_and_devicetree_oneplus_sm8550/blob/oneplus/sm8550_v_15.0.0_oneplus11/vendor/oplus/kernel/charger/v2/oplus_chg_gki.c), Git blob `9e865246f646f453f5eb98210f7c0c9060b1d5a3`: CURRENT_NOW forwards gauge data without µA scaling; MODEL_NAME identifies `oplus battery`.
- [MMS gauge current interface](https://github.com/OnePlusOSS/android_kernel_modules_and_devicetree_oneplus_sm8550/blob/oneplus/sm8550_v_15.0.0_oneplus11/vendor/oplus/kernel/charger/v2/mms/oplus_mms_gauge.c), blob `cbd4ee5df086e5fe732e517ff9b239c6678e9b21`: gauge current is forwarded without a unit conversion.
- [Gauge IC interface](https://github.com/OnePlusOSS/android_kernel_modules_and_devicetree_oneplus_sm8550/blob/oneplus/sm8550_v_15.0.0_oneplus11/vendor/oplus/kernel/charger/v2/gauge_ic/oplus_hal_bq27541.c), blob `73887d9b700e9196b74465ba824f4d2ebb862589`: current output is explicitly named `curr_ma`. These GPL-2.0-only sources document an interface profile, not proof that every OnePlus ROM uses it.
