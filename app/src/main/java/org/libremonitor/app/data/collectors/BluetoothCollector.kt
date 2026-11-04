/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.data.collectors

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import org.libremonitor.core.BluetoothDeviceInfo
import org.libremonitor.core.BluetoothSample
import org.libremonitor.core.Reading
import org.libremonitor.core.ReadingStatus

/**
 * Public Bluetooth API only. Adapter/device details are best-effort and permission-gated;
 * core Bluetooth version and bonded-device RSSI are not exposed by Android, so they stay unavailable.
 */
internal object BluetoothCollector {
    private const val SOURCE = "Android BluetoothManager/BluetoothAdapter（公共 API）"

    fun sample(context: Context): BluetoothSample {
        val manager = context.getSystemService(BluetoothManager::class.java)
            ?: return unavailable("设备未提供 BluetoothManager")
        val adapter = manager.adapter ?: return unavailable("设备未提供蓝牙适配器")
        val leSupported = Reading(
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE),
            "PackageManager.FEATURE_BLUETOOTH_LE",
        )
        if (Build.VERSION.SDK_INT >= 31 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            val deniedMessage = "需要 BLUETOOTH_CONNECT 权限"
            return BluetoothSample(
                supported = Reading(true, SOURCE),
                enabled = Reading.unavailable(SOURCE, deniedMessage, ReadingStatus.PERMISSION_DENIED),
                name = Reading.unavailable(SOURCE, deniedMessage, ReadingStatus.PERMISSION_DENIED),
                address = Reading.unavailable(SOURCE, deniedMessage, ReadingStatus.PERMISSION_DENIED),
                state = Reading.unavailable(SOURCE, deniedMessage, ReadingStatus.PERMISSION_DENIED),
                leSupported = leSupported,
                connectedCount = Reading.unavailable(SOURCE, deniedMessage, ReadingStatus.PERMISSION_DENIED),
                version = BluetoothVersion(),
            )
        }
        val enabled = safe { adapter.isEnabled }
            ?: return unavailable("读取蓝牙开关状态失败")
        val state: Reading<String> = safe { adapter.state }?.let { Reading(adapterState(it), SOURCE) }
            ?: Reading.unavailable(SOURCE, "状态不可用")
        val name = safe { adapter.name }?.takeIf { it.isNotBlank() }
            ?.let { Reading(it, "$SOURCE:name") }
            ?: Reading.unavailable("$SOURCE:name", "系统未提供蓝牙名称")
        val address = safe { adapter.address }?.takeIf { it.isNotBlank() }
            ?.let { Reading(it, "$SOURCE:address") }
            ?: Reading.unavailable("$SOURCE:address", "系统未公开蓝牙地址")
        val bonded = if (!enabled) {
            Reading(emptySet<BluetoothDevice>(), SOURCE)
        } else {
            safe { adapter.bondedDevices.orEmpty() }
                ?.let { Reading(it, SOURCE) }
                ?: Reading.unavailable(SOURCE, "无法读取已配对设备", ReadingStatus.PERMISSION_DENIED)
        }
        val devices = bonded.value.orEmpty()
            .sortedWith(compareBy<BluetoothDevice>({ safe { it.name }.orEmpty() }, { safe { it.address }.orEmpty() }))
            .mapNotNull { device -> try { deviceInfo(device) } catch (_: Exception) { null } }
        val connectedProfiles = listOf(
            BluetoothProfile.A2DP,
            BluetoothProfile.HEADSET,
            BluetoothProfile.GATT,
        ).count { profile ->
            safe { adapter.getProfileConnectionState(profile) } == BluetoothProfile.STATE_CONNECTED
        }
        return BluetoothSample(
            supported = Reading(true, SOURCE),
            enabled = Reading(enabled, SOURCE),
            name = name,
            address = address,
            state = state,
            leSupported = leSupported,
            connectedCount = Reading(connectedProfiles, "$SOURCE:已连接配置文件数"),
            version = BluetoothVersion(),
            devices = devices,
        )
    }

    private fun unavailable(reason: String) = BluetoothSample(
        supported = Reading.unavailable(SOURCE, reason, ReadingStatus.UNSUPPORTED),
        enabled = Reading.unavailable(SOURCE, reason),
        name = Reading.unavailable(SOURCE, reason),
        address = Reading.unavailable(SOURCE, reason),
        state = Reading.unavailable(SOURCE, reason),
        leSupported = Reading.unavailable(SOURCE, reason),
        connectedCount = Reading.unavailable(SOURCE, reason),
        version = BluetoothVersion(),
    )

    private fun BluetoothVersion(): Reading<String> = Reading.unavailable(
        "Android BluetoothAdapter",
        "Android 公共 API 不公开蓝牙核心版本；需要厂商或诊断接口",
        ReadingStatus.UNSUPPORTED,
    )

    // Caller performs the BLUETOOTH_CONNECT check on API31+; each field is also wrapped in safe()
    // to handle SecurityException/race and vendor restrictions.
    @SuppressLint("MissingPermission")
    private fun deviceInfo(device: BluetoothDevice): BluetoothDeviceInfo? {
        val name = safe { device.name }?.takeIf { it.isNotBlank() } ?: "未命名设备"
        val address = safe { device.address }?.takeIf { it.isNotBlank() }
        val type = safe { device.type }?.let(::deviceType) ?: "类型未知"
        val bond = safe { device.bondState }?.let(::bondState) ?: "配对状态未知"
        val bluetoothClass = safe { device.bluetoothClass }
        val major = safe { bluetoothClass?.majorDeviceClass }?.let(::majorClass) ?: "未知"
        val classCode = safe { bluetoothClass?.deviceClass }?.let { "0x%04X".format(it) } ?: "不可用"
        val services = serviceNames(bluetoothClass)
        val uuids = safe { device.uuids?.map { it.uuid.toString() }.orEmpty() }.orEmpty()
        val protocols = (services + uuids.mapNotNull(::uuidProtocol)).distinct()
        val protocol = if (protocols.isEmpty()) type else protocols.joinToString(" / ")
        // BluetoothDevice battery level is not a public, reliable API across current Android
        // versions; do not fabricate it.
        val battery: Reading<Int> = Reading.unavailable(
            "BluetoothDevice",
            "Android 公共 API 不提供已配对设备的可靠电量",
            ReadingStatus.UNSUPPORTED,
        )
        return BluetoothDeviceInfo(
            name = name,
            address = address,
            type = type,
            bondState = bond,
            deviceClass = classCode,
            majorClass = major,
            services = services,
            protocol = protocol,
            batteryPercent = battery,
            rssiDbm = Reading.unavailable(
                "BluetoothDevice",
                "Android 公共 API 不提供已配对设备的实时 RSSI；需要 BLE 扫描或 GATT 连接",
                ReadingStatus.UNSUPPORTED,
            ),
        )
    }

    private fun adapterState(state: Int): String = when (state) {
        BluetoothAdapter.STATE_ON -> "已开启"
        BluetoothAdapter.STATE_OFF -> "已关闭"
        BluetoothAdapter.STATE_TURNING_ON -> "正在开启"
        BluetoothAdapter.STATE_TURNING_OFF -> "正在关闭"
        else -> "状态未知"
    }

    private fun deviceType(type: Int): String = when (type) {
        BluetoothDevice.DEVICE_TYPE_CLASSIC -> "经典蓝牙"
        BluetoothDevice.DEVICE_TYPE_LE -> "低功耗蓝牙"
        BluetoothDevice.DEVICE_TYPE_DUAL -> "双模"
        else -> "未知类型"
    }

    private fun bondState(state: Int): String = when (state) {
        BluetoothDevice.BOND_BONDED -> "已配对"
        BluetoothDevice.BOND_BONDING -> "配对中"
        BluetoothDevice.BOND_NONE -> "未配对"
        else -> "未知"
    }

    private fun majorClass(value: Int): String = when (value) {
        BluetoothClass.Device.Major.AUDIO_VIDEO -> "音频/视频"
        BluetoothClass.Device.Major.COMPUTER -> "电脑"
        BluetoothClass.Device.Major.PHONE -> "手机"
        BluetoothClass.Device.Major.NETWORKING -> "网络"
        BluetoothClass.Device.Major.PERIPHERAL -> "外设"
        BluetoothClass.Device.Major.IMAGING -> "影像"
        BluetoothClass.Device.Major.WEARABLE -> "穿戴"
        BluetoothClass.Device.Major.HEALTH -> "健康"
        BluetoothClass.Device.Major.TOY -> "玩具"
        BluetoothClass.Device.Major.UNCATEGORIZED -> "未分类"
        else -> "其他"
    }

    private fun serviceNames(bluetoothClass: BluetoothClass?): List<String> = buildList {
        fun has(service: Int) = bluetoothClass?.hasService(service) == true
        if (has(BluetoothClass.Service.AUDIO)) add("音频")
        if (has(BluetoothClass.Service.CAPTURE)) add("拍摄")
        if (has(BluetoothClass.Service.INFORMATION)) add("信息")
        if (has(BluetoothClass.Service.NETWORKING)) add("网络")
        if (has(BluetoothClass.Service.OBJECT_TRANSFER)) add("对象传输")
        if (has(BluetoothClass.Service.POSITIONING)) add("定位")
        if (has(BluetoothClass.Service.TELEPHONY)) add("电话")
    }

    private fun uuidProtocol(value: String): String? = when (value.lowercase()) {
        "0000110a-0000-1000-8000-00805f9b34fb", "0000110b-0000-1000-8000-00805f9b34fb" -> "A2DP"
        "0000111e-0000-1000-8000-00805f9b34fb", "0000111f-0000-1000-8000-00805f9b34fb" -> "HFP"
        "0000110e-0000-1000-8000-00805f9b34fb", "0000110f-0000-1000-8000-00805f9b34fb" -> "AVRCP"
        "00001124-0000-1000-8000-00805f9b34fb" -> "HID"
        "00001812-0000-1000-8000-00805f9b34fb" -> "HOGP"
        "00001101-0000-1000-8000-00805f9b34fb" -> "SPP"
        "00001130-0000-1000-8000-00805f9b34fb" -> "PBAP"
        "00001132-0000-1000-8000-00805f9b34fb" -> "MAP"
        "00001115-0000-1000-8000-00805f9b34fb", "00001116-0000-1000-8000-00805f9b34fb" -> "PAN"
        else -> null
    }

    private fun <T> safe(block: () -> T): T? = try {
        block()
    } catch (_: SecurityException) {
        null
    } catch (_: Exception) {
        null
    }
}
