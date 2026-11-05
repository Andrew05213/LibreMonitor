/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.core

data class EarbudBattery(val model: String, val left: Int?, val right: Int?, val case: Int?,
    val leftCharging: Boolean, val rightCharging: Boolean, val caseCharging: Boolean)
data class EarbudObservation(val address: String, val name: String, val battery: EarbudBattery,
    val rssi: Int, val sampledElapsedMillis: Long)

/** Apple proximity interface facts; independent parser, no private APIs, pairing or control commands. */
object EarbudProtocol {
    fun appleManufacturer(data: ByteArray): EarbudBattery? {
        fun u(i: Int) = data[i].toInt() and 255
        if (data.size != 27 || u(0) != 7 || u(1) != 25 || u(2) != 1) return null
        val model = when ((u(3) shl 8) or u(4)) {
            0x0220 -> "AirPods 1"
            0x0f20 -> "AirPods 2"
            0x1320 -> "AirPods 3"
            0x0e20 -> "AirPods Pro"
            0x1420, 0x2420 -> "AirPods Pro 2"
            else -> return null
        }
        // Battery is a coarse ten-percent bin. Keep the advertised bin, never invent 1% precision.
        fun level(n: Int): Int? = n.takeIf { it in 0..10 }?.times(10)
        val firstIsLeft = u(5) and 0x20 != 0
        val low = level(u(6) and 15)
        val high = level(u(6) ushr 4)
        val flags = u(7) ushr 4
        return EarbudBattery(model, if (firstIsLeft) low else high, if (firstIsLeft) high else low,
            level(u(7) and 15), flags and (if (firstIsLeft) 2 else 1) != 0,
            flags and (if (firstIsLeft) 1 else 2) != 0, flags and 4 != 0)
    }

    /** Walk every AD structure; multiple Apple entries may coexist in one advertisement. */
    fun advertisement(bytes: ByteArray): EarbudBattery? {
        var offset = 0
        while (offset < bytes.size) {
            val length = bytes[offset].toInt() and 255
            if (length == 0) break
            if (offset + length >= bytes.size) return null
            if (length >= 3 && bytes[offset + 1].toInt() and 255 == 0xff &&
                bytes[offset + 2].toInt() and 255 == 0x4c && bytes[offset + 3].toInt() and 255 == 0) {
                appleManufacturer(bytes.copyOfRange(offset + 4, offset + length + 1))?.let { return it }
            }
            offset += length + 1
        }
        return null
    }
}

data class InterfaceCounters(val received: Long, val sent: Long)
object NetworkInterfaceCounters {
    fun parse(text: String): Map<String, InterfaceCounters> = buildMap {
        text.lineSequence().forEach { line ->
            if (':' !in line) return@forEach
            val name = line.substringBefore(':').trim()
            val fields = line.substringAfter(':').trim().split(Regex("\\s+"))
            if (name.matches(Regex("[A-Za-z0-9_.-]{1,32}")) && fields.size >= 16) {
                val rx = fields[0].toLongOrNull()
                val tx = fields[8].toLongOrNull()
                if (rx != null && tx != null && rx >= 0 && tx >= 0) put(name, InterfaceCounters(rx, tx))
            }
        }
    }
    fun isBluetoothPan(name: String): Boolean = name.matches(Regex("(?:bnep[0-9]+|bt-pan[0-9]*)"))
}

data class EnvironmentSample(
    val sampledElapsedMillis: Long = 0,
    val connectivity: Map<String, Reading<String>> = emptyMap(),
    val system: Map<String, Reading<String>> = emptyMap(),
)
