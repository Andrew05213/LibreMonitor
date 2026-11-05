/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.core

object HotspotDiagnostics {
    fun parse(text: String): Map<String, Reading<String>> {
        val states = mutableListOf<String>()
        val counts = mutableListOf<Int>()
        var manager = false
        text.lineSequence().forEach { line ->
            when {
                line == "AP_BEGIN" -> manager = true
                manager && line == "STATE=enabled" -> states.add("已开启")
                manager && line == "STATE=disabled" -> states.add("未开启")
                manager && line.startsWith("CLIENTS=") -> line.substringAfter('=').toIntOrNull()?.takeIf { it in 0..10_000 }?.let(counts::add)
            }
        }
        val source = "Root dumpsys wifi：仅过滤的 SoftApManager 状态与客户端数量"
        return mapOf(
            "SoftAP 状态" to if (states.isEmpty()) Reading.unavailable(source, "ROM 未输出已适配的 SoftApManager 状态；无法据此判断热点开关")
                else Reading(if ("已开启" in states) "已开启" else "未开启", source, reason = "SoftAP 可包含系统共享热点或本地热点"),
            "SoftAP 客户端数" to if (counts.isEmpty()) Reading.unavailable(source, "ROM 未输出可核实的客户端数量")
                else Reading(counts.sum().toString(), source, reason = "各已输出 SoftApManager 的客户端计数之和"),
        )
    }
}
