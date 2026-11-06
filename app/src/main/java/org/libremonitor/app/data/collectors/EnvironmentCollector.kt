/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.data.collectors

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.net.wifi.WifiInfo
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.telephony.TelephonyManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import org.libremonitor.app.data.privileged.RootSession
import org.libremonitor.core.*

/** Visible dashboard only; no independent loop, packet capture or active network probe. */
internal class EnvironmentCollector(private val context: Context) {
    private var cached = EnvironmentSample()
    private var rootIdentity = ""
    fun invalidate() { cached = EnvironmentSample() }

    suspend fun sample(reader: RootSession, includeRootDiagnostics: Boolean = true): EnvironmentSample {
        val now = SystemClock.elapsedRealtime()
        val identity = "${reader.state.value.access}:${reader.state.value.sessionId}:$includeRootDiagnostics"
        if (identity == rootIdentity && cached.sampledElapsedMillis > 0 && now - cached.sampledElapsedMillis in 0..4_999) return cached
        rootIdentity = identity
        fun read(source: String, block: () -> String?): Reading<String> = try {
            block()?.takeIf { it.isNotBlank() && !it.equals("unknown", true) }?.let { Reading(it, source) }
                ?: Reading.unavailable(source, "系统未提供此字段")
        } catch (_: SecurityException) { Reading.unavailable(source, "系统拒绝访问，需要相应权限或厂商支持", ReadingStatus.PERMISSION_DENIED) }
          catch (e: Exception) { Reading.unavailable(source, "读取失败：${e.javaClass.simpleName}", ReadingStatus.ERROR) }
        fun yes(value: Boolean) = if (value) "是" else "否"
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        val basePhone = context.getSystemService(TelephonyManager::class.java)
        val dataSubscription = runCatching { SubscriptionManager.getDefaultDataSubscriptionId() }.getOrDefault(SubscriptionManager.INVALID_SUBSCRIPTION_ID)
        val phone = if (dataSubscription >= 0) basePhone?.createForSubscriptionId(dataSubscription) else basePhone
        val power = context.getSystemService(PowerManager::class.java)
        val network = linkedMapOf<String, Reading<String>>()
        network["当前网络"] = read("ConnectivityManager / NetworkCapabilities") {
            val caps = cm?.getNetworkCapabilities(cm.activeNetwork) ?: return@read "未连接默认网络"
            buildList {
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) add("Wi-Fi")
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) add("移动网络")
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) add("以太网")
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) add("蓝牙 PAN")
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) add("VPN")
            }.joinToString(" + ").ifEmpty { "其他网络" }
        }
        network["互联网验证"] = read("系统网络验证状态；不主动联网") {
            cm?.getNetworkCapabilities(cm.activeNetwork)?.let { yes(it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) }
        }
        network["按流量计费"] = read("ConnectivityManager.isActiveNetworkMetered") { cm?.let { yes(it.isActiveNetworkMetered) } }
        network["运营商"] = read("TelephonyManager.networkOperatorName") { phone?.networkOperatorName }
        network["SIM 状态"] = read("TelephonyManager.simState") { phone?.simState?.let {
            when (it) { 1 -> "无 SIM"; 2 -> "需要 PIN"; 3 -> "需要 PUK"; 4 -> "网络锁定"; 5 -> "就绪"; 6 -> "不可用"; 8 -> "卡错误"; 10 -> "已加载"; else -> "状态代码 $it" }
        } }
        network["漫游"] = read("TelephonyManager.isNetworkRoaming") { phone?.let { yes(it.isNetworkRoaming) } }
        val phoneAllowed = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        fun phoneRead(block: () -> String?): Reading<String> = if (!phoneAllowed) Reading.unavailable("TelephonyManager", "需要手动授予电话状态权限；不读取号码、IMEI、基站或通话内容", ReadingStatus.PERMISSION_DENIED)
            else read(if (dataSubscription >= 0) "TelephonyManager（默认数据订阅）" else "TelephonyManager（系统默认电话服务，未确定数据订阅）", block)
        network["移动制式"] = phoneRead { phone?.dataNetworkType?.let { when (it) {
            TelephonyManager.NETWORK_TYPE_NR -> "5G NR"
            TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE（5G NSA 可能仍报告 LTE）"
            TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_HSPA -> "3G HSPA"
            TelephonyManager.NETWORK_TYPE_UMTS -> "3G UMTS"
            TelephonyManager.NETWORK_TYPE_EDGE -> "2G EDGE"
            TelephonyManager.NETWORK_TYPE_GPRS -> "2G GPRS"
            0 -> "未知 / 当前未注册数据网络"
            else -> "制式代码 $it"
        } } }
        network["移动数据开关"] = phoneRead { phone?.let { yes(it.isDataEnabled) } }
        network["信号等级"] = phoneRead { if (Build.VERSION.SDK_INT >= 28) phone?.signalStrength?.level?.takeIf { it in 0..4 }?.let { "$it / 4（系统综合等级）" } else null }
        network["Wi-Fi 开关"] = read("WifiManager.isWifiEnabled") { wifi?.let { yes(it.isWifiEnabled) } }
        fun wifiInfo(): WifiInfo? {
            val caps = cm?.getNetworkCapabilities(cm.activeNetwork) ?: return null
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null
            return if (Build.VERSION.SDK_INT >= 29) caps.transportInfo as? WifiInfo else wifi?.connectionInfo
        }
        network["Wi-Fi 链路速率"] = read("WifiInfo.linkSpeed；协商链路值，不代表实际吞吐") { wifiInfo()?.linkSpeed?.takeIf { it > 0 }?.let { "$it Mbps" } }
        network["Wi-Fi 频率"] = read("WifiInfo.frequency") { wifiInfo()?.frequency?.takeIf { it > 0 }?.let { "$it MHz" } }
        network["Wi-Fi RSSI"] = read("WifiInfo.rssi") { wifiInfo()?.rssi?.takeIf { it in -126..-1 }?.let { "$it dBm" } }
        network["Wi-Fi 与热点并发能力"] = read("WifiManager.isStaApConcurrencySupported") { if (Build.VERSION.SDK_INT >= 30) wifi?.let { yes(it.isStaApConcurrencySupported) } else null }
        network["桥接双热点能力"] = read("WifiManager.isBridgedApConcurrencySupported") { if (Build.VERSION.SDK_INT >= 31) wifi?.let { yes(it.isBridgedApConcurrencySupported) } else null }
        val root = reader.state.value
        val diagnostics = if (includeRootDiagnostics && root.access == RootAccess.GRANTED) try { reader.readDiagnosticsFrame() } catch (e: CancellationException) { throw e } catch (_: Exception) { null } else null
        val stillRoot = reader.state.value.access == RootAccess.GRANTED && reader.state.value.sessionId == root.sessionId
        val diagnostic = diagnostics?.takeIf { stillRoot && it.sessionId == root.sessionId }?.readings?.get("wifi_status")
        if (diagnostic?.available == true) network.putAll(HotspotDiagnostics.parse(diagnostic.value!!)) else {
            val reason = if (!includeRootDiagnostics) "快速采样或性能记录期间暂停慢速热点诊断" else if (root.access != RootAccess.GRANTED) "普通应用没有可靠的热点状态 API；可在前台手动启用 Root 后尝试只读状态诊断" else "Root 诊断未返回已适配的热点状态，可能超时或受 ROM 限制"
            network["SoftAP 状态"] = Reading.unavailable("Android Wi-Fi / Root 过滤诊断", reason)
            network["SoftAP 客户端数"] = Reading.unavailable("Android Wi-Fi / Root 过滤诊断", reason)
        }
        val system = linkedMapOf<String, Reading<String>>()
        system["Android / API"] = Reading("${Build.VERSION.RELEASE} / ${Build.VERSION.SDK_INT}", "Android Build")
        system["安全补丁"] = read("Build.VERSION.SECURITY_PATCH") { Build.VERSION.SECURITY_PATCH }
        system["内核"] = read("os.version") { System.getProperty("os.version") }
        system["启动时长（含休眠）"] = Reading("${SystemClock.elapsedRealtime() / 1_000} 秒", "SystemClock.elapsedRealtime")
        system["省电模式"] = read("PowerManager.isPowerSaveMode") { power?.let { yes(it.isPowerSaveMode) } }
        system["设备空闲模式"] = read("PowerManager.isDeviceIdleMode") { power?.let { yes(it.isDeviceIdleMode) } }
        system["屏幕交互"] = read("PowerManager.isInteractive") { power?.let { yes(it.isInteractive) } }
        system["系统热状态"] = read("PowerManager.currentThermalStatus") { if (Build.VERSION.SDK_INT >= 29) power?.currentThermalStatus?.let {
            listOf("正常", "轻微", "中等", "严重", "临界", "紧急", "即将关机").getOrNull(it)
        } else null }
        system["飞行模式"] = read("Settings.Global.AIRPLANE_MODE_ON") { yes(Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0) }
        cached = EnvironmentSample(now, network, system)
        return cached
    }
}
