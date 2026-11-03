# v0.2 integration status — 0.2.0-dev

The final debug build passed local JVM tests and Android compilation/lint. This is an independent development prototype, **not a device-validated or production release**. No APK was installed or launched by the build agent; no Root authorization or device sensor test was attempted.

## 0.2.1 crash fix (post-delivery)

A user-provided device crash log showed `java.util.regex.PatternSyntaxException` at `AdvancedCpuCollector.<clinit>` during `CpuCollector.sample`. The HAL temperature regex ended with a literal `}` that Android ICU rejects (the JVM test suite did not expose the difference). The pattern now escapes the closing brace and uses `\z`; app version is `0.2.1-dev` (code 3).

Crash-fixed build: 77 core + 35 app JVM tests passed, Lint 0 errors / 7 dependency warnings, APK SHA-256 `bc380ebb6c6dc05c7aeb75e51ae4a75ea493b61d5e099b78e7a05943c30f5de8`, same debug signer as prior builds. **This 0.2.1 APK has not yet been installed/launched on a device**, so the crash fix itself still needs on-device confirmation.

## Build and test evidence

```
bash scripts/build-local.sh :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --stacktrace
```

Final build: **exit 0, BUILD SUCCESSFUL**, 1m 38s, 15 tasks executed and 41 up-to-date. The earlier build before the pipe handoff also passed; the subsequent interim rebuild was cancelled when the handoff arrived. The final build after that fix is the result above. Full output is in [`v02-build.log`](../v02-build.log).

| XML test results | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| core JVM | 77 | 0 | 0 | 0 |
| app debug unit | 35 | 0 | 0 | 0 |
| Total | 112 | 0 | 0 | 0 |

Counts come from the generated `core/build/test-results/test/TEST-*.xml` and `app/build/test-results/testDebugUnitTest/TEST-*.xml`. The core test task was up-to-date, while app tests executed in the final pass. The root protocol tests include a POSIX-shell, 256-byte bounded duplex-output pipe regression using synthetic unprivileged commands, not `su` or real device data.

Android Lint XML: **0 errors, 7 warnings**, all `GradleDependency` (category `Correctness`, newer-version suggestions). No lint suppression was added. Lint's report task was up-to-date; the generated XML was parsed. AGP also warns about absent optional platform-tools and the experimental aapt2 override; Kotlin reports a deprecated coroutine `resume` overload.

## APK evidence

- [`app-debug.apk`](../app/build/outputs/apk/debug/app-debug.apk): **9,101,242 bytes**; SHA-256 `f4425347e9fddd5c06e4817addb47756b4c1315742563639871086b75473c135`.
- `aapt2 dump badging` exit 0: `org.libremonitor.app`, versionCode **2**, versionName **0.2.0-dev**, minSdk 26, targetSdk 35. Prior v0.1 package is the same application ID with versionCode 1.
- SDK `apksigner.jar verify --verbose --print-certs` exit 0 for both final APK and the specified v0.1 APK. Both use verified APK Signature Scheme v2; certificate SHA-256 **b1c8e0d740bb3a67e45fd0c810c0594bd7269b5926021d4a12b90283243ab491** matches. This is a debug certificate, not production signing.
- `aapt2 dump permissions` exit 0: `SYSTEM_ALERT_WINDOW`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, and `org.libremonitor.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. No INTERNET, location, phone-state, broad-storage or boot permission is present.
- `unzip -t` exit 0; `assets/licenses/LICENSE`, `NOTICE`, and `REUSE.md` are bundled. Hash and zip/list commands exited 0. Full verification output and individual exit codes: [`v02-apk-validation.log`](../v02-apk-validation.log).
- `git diff --check` exit 0. SQLite schema remains version 1 and CSV export retains seven columns.

## Integration fixes

1. Accept complete, bounded `thermalservice` frames for the existing strict HAL parser instead of unconditionally discarding them.
2. Verify the complete fixed allowlist count (479), align node-length boundaries and set the shell PATH.
3. Wrap the fixed frame script in one complete POSIX compound command to avoid write-before-read pipe deadlock; add a bounded-pipe regression.
4. Enforce the 2-second battery frame window and only use same-battery/same-unit capacity ratios for health; do not infer it from vendor `state_of_health` or `GOOD`.
5. Show valid capacity health through 150% without clamping.
6. Correct CPU temperature provenance, exercise synthetic HAL/sysfs fallback, and avoid an extra baseline reset after Root state transitions.
7. Preserve useful explanation text in available-reading details; primary cards remain compact and missing values remain unavailable rather than fabricated zeroes.

The shared sampler supplies the dashboard and associated views. Root defaults off and is requested only through explicit foreground user action and confirmation; the backend uses fixed read-only operations. These statements describe the implementation, not a device acceptance test.

## Remaining limitations

- No install, launch, UI/permission, history/export, overlay, accessibility, real sensor, OEM compatibility, performance or Root device validation. Root frame format and timing are device-dependent; a 2-second deadline may fail on slow devices.
- The 512 KiB encoded-frame cap fails closed even when a theoretical combination of individually valid maximum-sized inputs exceeds it. Root denial may be reported as unavailable, and a stuck cleanup worker may require an app restart.
- CPU thermal values are named regional sensors or recognized complete HAL output, not verified per-core junction temperatures. GPU, battery capacity and cycles depend on valid available sources. Estimated battery power is terminal power, not charger/SoC power.
- No production signing, remote CI or cross-device accuracy claim. Synthetic tests must not be presented as device measurements.

Machine-readable evidence and per-command results: [`validation-report-v02.json`](validation-report-v02.json).
