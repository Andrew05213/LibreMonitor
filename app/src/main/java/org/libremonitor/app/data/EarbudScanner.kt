/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.data

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import org.libremonitor.core.EarbudObservation
import org.libremonitor.core.EarbudProtocol

/** Explicit, bounded foreground scan. No raw packet, address or observation is persisted. */
internal class EarbudScanner(private val context: Context,
    private val update: (List<EarbudObservation>, Boolean, String) -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private val observations = linkedMapOf<String, EarbudObservation>()
    private var callback: ScanCallback? = null
    private var scanner: android.bluetooth.le.BluetoothLeScanner? = null
    private var generation = 0L

    fun start() {
        if (callback != null) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
            Build.VERSION.SDK_INT >= 31 && (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)) {
            update(emptyList(), false, "扫描需要附近设备和系统要求的位置权限；不会读取或记录位置。")
            return
        }
        val token = ++generation
        observations.clear()
        try {
            val le = context.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner
                ?: error("蓝牙未开启或不支持 BLE 扫描")
            scanner = le
            val cb = object : ScanCallback() {
                override fun onScanResult(type: Int, result: ScanResult) { accept(result, token) }
                override fun onBatchScanResults(results: MutableList<ScanResult>) { results.forEach { accept(it, token) } }
                override fun onScanFailed(errorCode: Int) { handler.post { if (generation == token) stop("扫描失败（$errorCode），请稍后重试。") } }
            }
            callback = cb
            le.startScan(listOf(ScanFilter.Builder().setManufacturerData(0x004c, byteArrayOf()).build()),
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build(), cb)
            update(emptyList(), true, "扫描 30 秒。请靠近耳机并打开充电盒；不会自动关联随机地址。")
            handler.postDelayed({ if (generation == token) stop("扫描已结束，读数最多保留 60 秒。没有结果时请检查系统位置开关及耳机广播状态。") }, 30_000)
        } catch (e: Exception) { stop("无法扫描：${e.message ?: e.javaClass.simpleName}") }
    }

    private fun accept(result: ScanResult, token: Long) {
        val record = result.scanRecord ?: return
        val battery = record.bytes?.let(EarbudProtocol::advertisement) ?: return
        val address = try { result.device.address } catch (_: SecurityException) { return }
        val observation = EarbudObservation(address, record.deviceName ?: battery.model, battery,
            result.rssi, SystemClock.elapsedRealtime())
        handler.post {
            if (generation != token || callback == null) return@post
            observations[address] = observation
            while (observations.size > 24) observations.remove(observations.keys.first())
            update(observations.values.toList(), true, "识别到 ${observations.size} 条耳机广播；电量为 10% 档位的近似值。")
        }
    }

    fun stop(message: String = "扫描已停止；读数会在 60 秒后过期。") {
        generation++
        val cb = callback
        callback = null
        if (cb != null) try {
            if (Build.VERSION.SDK_INT < 31 || ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) scanner?.stopScan(cb)
        } catch (_: Exception) { }
        scanner = null
        update(observations.values.toList(), false, message)
    }
}
