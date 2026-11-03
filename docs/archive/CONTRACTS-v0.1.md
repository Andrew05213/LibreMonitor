# Implementation contracts

Read AGENTS.md and `core/src/main/kotlin/org/libremonitor/core/Models.kt` plus `Calculations.kt` before implementation. New code SPDX Apache-2.0, independent implementation only. Do not edit shared contracts; request changes from lead.

## UI scope
Write only `app/src/main/java/org/libremonitor/app/ui/**`.
Expose:
```kotlin
@Composable fun LibreMonitorScreen(state: MonitorUiState, actions: MonitorActions)
```
State/actions are in `org.libremonitor.app.MonitorUiState.kt` (lead-owned). Use Compose Material3 and standard Material icons only if available; prefer text/Canvas rather than an extra dependency. Tabs: Overview, Battery, Settings. Device summary, readable real readings including unavailable source/reason, battery trend drawn with Canvas (do not connect missing points), opt-in history switch, refresh interval choices 1/2/5/10 s, overlay enable/disable action, CSV export action, clear history with a confirmation dialog, source/license/about and honest Shizuku unavailable label. No fake demo data, no claim of tested device performance, no permissions requested directly in Composables. Chinese first, accessibility content descriptions and scrolling, support dark theme. Do not create Activity, ViewModel, Gradle, manifest, service, resources or persistence.

## Collector scope
Write only `app/src/main/java/org/libremonitor/app/data/AndroidSampler.kt` and extra helper files under `app/src/main/java/org/libremonitor/app/data/collectors/**`.
Expose:
```kotlin
class AndroidSampler(context: Context) {
    fun deviceInfo(): DeviceInfo
    suspend fun sample(): MonitorSnapshot
}
```
One caller, serial sample calls. IO work on Dispatchers.IO. Use ActivityManager.MemoryInfo for memory; read /proc/stat best effort (often denied), /proc/meminfo for Swap; sysfs readable per-core cpufreq; CPU temp only if sensor mapping can be safely identified (otherwise unsupported, not any thermal zone mislabeled CPU). Sticky BATTERY_CHANGED and BatteryManager properties; validate sentinels and bounds, raw current unit µA -> mA and charge µAh -> mAh; battery terminal estimated power annotated uncalibrated vendor sign. TrafficStats totals/deltas with monotonic clock. Initial rate/CPU delta unavailable. Storage StatFs. Device public Build/Display info. NO arbitrary commands, root, hidden APIs, hardware database, network calls or broad permissions. Use core CPU parsers and CounterRate where applicable. Bound read sizes. Sample errors should affect individual readings, not entire snapshot. No mock readings.

## Persistence scope
Write only `app/src/main/java/org/libremonitor/app/data/BatteryHistoryStore.kt` and `MonitorPreferences.kt` (and helpers under `data/storage/**`). Use Android platform SQLiteOpenHelper and SharedPreferences to avoid code generation/native dependencies in the MVP. Expose:
```kotlin
class MonitorPreferences(context: Context) {
    val settings: StateFlow<MonitorSettings>
    fun setHistoryEnabled(enabled: Boolean)
    fun setSampleIntervalMs(value: Long)
    fun close()
}
class BatteryHistoryStore(context: Context): AutoCloseable {
    suspend fun insert(record: BatteryRecord)
    suspend fun latest(limit: Int = 360): List<BatteryRecord> // oldest to newest, bounded
    suspend fun pruneOlderThan(cutoffEpochMillis: Long)
    suspend fun clear()
    suspend fun exportCsv(output: OutputStream) // streaming chronological export, UTF-8; flush but do not close caller stream
    override fun close()
}
```
SQLite version1, no destructive upgrade, nullable measurements preserved, timestamp index, storage cap 50,000 records plus time-based cleanup. serialize operations and use Dispatchers.IO; exported values use core BatteryCsv. Defaults history off, sample interval2000, history interval60000, retention7days; clamp damaged prefs. Never request broad storage permission or use external storage implicitly.

## Lead scope
Root build/Gradle, app manifest and resources, `core/**`, `MonitorUiState.kt`, `MainActivity.kt`, `MonitorApplication.kt`, `MonitorRuntime.kt`, service/overlay package, documentation and tests. Build/tests run by lead; child session commands may be sandbox-denied. Do not attempt escalation or alternative access after a denial. File tools can write assigned new workspace paths.

## Dependency baseline
Prefer curated stable compatible versions; not necessarily newest. Lead verifies BOM/Kotlin/AGP/SDK compatibility before pinning. Min SDK26. No INTERNET permission. Unit tests JUnit4.
