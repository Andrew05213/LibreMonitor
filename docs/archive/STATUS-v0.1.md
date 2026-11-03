# Validation status — 0.1.0-dev

Validated on2026-11-03 using an ARM64 Android phone running Ubuntu/proot. This is an independent development prototype, not a production or cross-device validated release.

## Actual results

The final command was:

```sh
bash scripts/build-local.sh :core:test :app:assembleDebug :app:lintDebug --stacktrace
```

- **BUILD SUCCESSFUL**, exit0. The final incremental build executed22tasks with29up-to-date; a previous full pass actually executed the core test suite.
- **24core JVM tests,0failures,0errors,0skipped**. CPU accounting/reset handling, memory units, missing values, signed/nullable CSV and formula guards were exercised.
- Android Kotlin/Compose compilation, ARM64 aapt2 resource/daemon pipeline, D8 and debug APK packaging completed.
- **Android Lint:0errors,7warnings**. All seven are newer-version suggestions for deliberately pinned dependencies. No baseline or suppression was added to hide errors.
- Final APK verified by apksigner using APK Signature Scheme v2. Debug signing is for development only.
- Final APK size:9,322,845bytes. SHA256:`e9e54ccc252f2290cf1c3bd9d0c13b6664e2915c788ce5c796e9096f46fbf48c`.
- Manifest audit found no INTERNET, location, phone-state, broad storage, package-query or boot-autostart permission. Expected permissions are overlay, notifications, foreground-service/special-use and AndroidX's app-specific non-exported-receiver signature permission.
- APK ZIP integrity and bundled LICENSE/NOTICE/REUSE assets were checked.
- Gradle Wrapper distribution SHA256 is pinned. Python/shell helper syntax checks passed. Staged source diff passed whitespace validation after declaring normal Windows Wrapper CRLF attributes.

Machine-readable evidence is in `validation-report.json`. Full local generated reports remain under ignored `core/build` and `app/build` directories.

## Implemented

Compose overview/battery/settings UI; public-API and fixed-readable-node collectors; private nullable SQLite battery history and queued preferences; one shared sampling runtime; explicit notification/overlay foreground service; draggable overlay; system-document-picker streaming CSV export; source/reason and stale-data handling; seven-day/50k storage limits; confirmed clear action; backup/transfer exclusions.

Parallel implementation and cross-review addressed delayed preferences overriding newer user intent, multiple Activity visibility, permission-result recreation, notification/channel denial, cadence cancellation, stale history query publication and foreground-only freshness ticks.

## Environment notes and resolved build issues

- JDK17 and Gradle8.9 run on this ARM64 host. SDK35/build-tools35.0.0 archives were checked against Google metadata; downloads and SDK terms acceptance were explicitly approved by the user.
- A third-party static ARM64 aapt2 was checked with file/readelf/version and then exercised by the full resource build. Its reviewed archive SHA256 is pinned. It is not an official Google ARM64 binary; provenance is recorded in ignored local toolchain metadata.
- Minimal-rootfs JDK package setup required a missing man directory; normal dpkg configuration repaired it.
- Missing Java cacerts was repaired through system certificate tooling. TLS verification was never disabled.
- AGP's default build-tools34 mismatch was fixed by explicitly selecting installed35.0.0.
- A Compose Lint false-positive around nested produceState assignment was resolved by using remembered state plus a lifecycle-bound effect, not by suppression.
- Optional platform-tools remains uninstalled, so AGP emits an optional-package/license warning. It was not needed for compilation. No adb command/device connection was used.
- Native AndroidX path library stripping could not run with absent cross-strip tools; the debug APK packages the original Maven library. This does not invalidate debug signing, but no release-size/optimization claim is made.

## Explicitly not verified / not implemented

- **No APK installation or launch**, no runtime notification/overlay authorization, no device history/export/rotation/TalkBack/large-font smoke test, no quantified monitoring overhead or OEM background-survival test.
- No battery health, universal CPU/GPU/temperature availability or cross-device accuracy claim. CPU temperature is unavailable without a validated mapping; battery power is an uncalibrated battery-terminal estimate.
- Shizuku/root backends, cross-app FPS, GPU utilization, modem capture and widgets are not implemented and are labelled unavailable.
- GitHub Actions is configured but no remote repository, CI run, publication, or production signing was performed.

See TESTING.md for a separately approved device-test plan. Stop prevents new history acceptance; an already accepted transaction may complete. No wake lock, boot receiver or silent watchdog is present.
