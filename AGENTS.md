# LibreMonitor contributor rules

This is a new, independent Android monitoring app. Do not copy OpenMonitor code, branding, assets, activation logic, or proprietary/vendor binaries. Only reuse components with verified compatible licenses and record sources in docs/REUSE.md.

## Scope
- v0.2: compact data-first dashboard without explanatory fine print on primary cards; move provenance, permission and measurement caveats into explicit details.
- Add genuine CPU utilization/temperature and battery capacity/cycles/health data where a validated public or read-only privileged source exists. Voltage/current/power must be prominent, not buried.
- A user-authorized Root/Shizuku backend may use fixed, bounded, allowlisted reads. After a separate confirmation it may also run fixed app-scoped commands to grant this app's notification permission, overlay app-op and battery-optimization whitelist. Never expose an arbitrary command terminal, write kernel nodes, change SELinux, affect other apps, terminate processes, capture modem traffic, add analytics/internet, or auto-start at boot.
- A backend is unavailable until actually implemented and probed. Do not infer privilege from another app or from the development shell.

## Data correctness
- Missing, denied, stale or invalid readings must not become zero.
- Rates use monotonic elapsed time and reject counter resets.
- Battery power is an estimate at the battery terminal, not charger/SoC power. Do not advertise battery health without a validated measurement path.
- One shared sampling engine feeds UI, recording and overlay; avoid duplicate loops.
- Continuous recording/overlay must be opt-in, visible, and stoppable.

## Coordination
Lead owns root build files, core models/parsers/tests and app orchestration.
Delegated write scopes will be recorded on the shared task board. Do not edit outside your assigned scope. Read shared interfaces first and report required contract changes to lead rather than editing their files.
Prefer read/edit/write; use glob/grep for discovery/search. Do not modify existing sibling projects or host credentials/configuration.

## Validation
Run pure JVM unit tests and Android debug compilation when the toolchain is available. Report precisely what was and was not tested. Never describe a mock reading as a real device measurement.
Only debug builds may use the debug signing key. No production keys or secrets in the repository.

## Working model
The user has restored child-model selection and requests heavy use of DeepSeek V4.1 Flash Max and GPT-6.1 Terra High, with Sol High for lead/integration if needed. Verify the configured provider/model identifiers and request explicit reasoning effort through the supported delegation route. Never silently replace the selected model or effort; report a failed dispatch accurately.

## v0.2 build handoff
The lead's independent preflight has finished and released Gradle. Before final build/fixes, read `docs/V2_PREFLIGHT.md` and the two V2 review reports. Preflight compiled Kotlin/Compose but failed one of 32 app unit tests (`fixedAllowlistAndLimits`: expected478, actual479); inspect the full fixed allowlist, not just the expected number. Final validation must use newly rebuilt v0.2 artifacts, not a retained v0.1 APK.

Final integration must also read `docs/V2_PIPE_HANDOFF.md`: the current large fixed script is fully written before stdout parsing, so a small bidirectional pipe can deadlock before reading starts. Fix this safely (for example, a complete compound-command script or bounded concurrent exchange), keep timeout/limits, and add a regression before final validation.

## Provisional license
New independent code is Apache-2.0. Preserve third-party notices. A change requiring copyleft dependencies needs a lead review first.
