# Building LibreMonitor

## Standard SDK host / CI

Requirements: JDK17, Android SDK platform35 + build-tools35.0.0, internet for initial dependency resolution. Gradle Wrapper is generated for8.9 and pins the official distribution SHA256. Run:

```sh
./gradlew --no-daemon --max-workers=2 :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

The public GitHub Actions workflow has passed against the current 0.3.1 development tree. See the [actions run](https://github.com/Andrew05213/LibreMonitor/actions/runs/41625409166). A green build is not a device-accuracy guarantee.

## This phone: Ubuntu24.04 proot, aarch64

The Android SDK's Linux native binaries are not automatically ARM64. Java/Gradle/Kotlin/D8 can run on ARM64, but the resource compiler must match the CPU. This project-local helper uses a disclosed third-party static AOSP aapt2. It does not install an emulator, NDK, root framework or adb.

1. Install JDK17, Python3, unzip, file and CA certificates using the host's package manager.
2. Review https://developer.android.com/studio/terms . Only after agreeing:

```sh
python3 scripts/prepare-toolchain.py --accept-android-sdk-license
```

3. Inspect the ignored `.toolchain/toolchain-provenance.json`. Verify ARM64/static ELF and run the downloaded aapt2's version command. A version check is not a resource-build test.
4. Run:

```sh
bash scripts/build-local.sh
```

The local script uses the prepared Gradle distribution directly, avoiding a second copy downloaded by Wrapper. Android Studio and CI should use the normal Wrapper instead. Project/cache files remain in the Ubuntu private filesystem, not `/sdcard`, because Android shared storage has permission/symlink/noexec limitations.

The local SDK license hash and `local.properties` are generated only after explicit consent and are ignored by git. Do not copy local SDK paths into checked-in Gradle files. No production signing key is created. Gradle/SDK/dependency downloads are significant; exact bytes of tool archives are recorded after download, not estimated in advance.

### Minimal-rootfs issues encountered

- Ubuntu JDK package post-installation can fail when `/usr/share/man/man1` is absent. Creating that directory and completing `dpkg --configure -a` repairs the package state.
- The Java cacerts symlink can point to a missing trust store. Reconfigure `ca-certificates-java` and regenerate system certificates using the normal package mechanism. **Do not disable HTTPS/TLS certificate verification to make Gradle downloads work.**
- After a JDK reinstall, regenerate the Java trust store before resolving dependencies; otherwise Gradle reports `trustAnchors parameter must be non-empty`.

The helper's third-party aapt2 is not a Google official ARM64 release. Its checksum baseline and provenance must be reviewed if upstream assets change. If it cannot run or pass a full resource build, use the standard Linux CI path rather than silently substituting an untrusted binary or claiming the phone build passed.

## Output and installation

Development output is `app/build/outputs/apk/debug/app-debug.apk`. The current debug APK is `0.3.1-dev`; its SHA-256 is recorded in [STATUS.md](STATUS.md). Compilation does not install an app. Install, notification/overlay permission, Root authorization and device smoke tests require a separate user action.

No phone tests have been run unless explicitly listed in [STATUS.md](STATUS.md). CPU/GPU/temperature support must be tested per device, Android/ROM and permission mode, not inferred from build success.
