/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** Versioned private payload; no Android/JSON runtime required. */
object PerformanceCodec {
    fun encode(point: PerformancePoint): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(GZIPOutputStream(bytes)).use { out ->
            out.writeInt(1)
            out.writeLong(point.elapsedMillis)
            out.writeLong(point.timestampEpochMillis)
            out.writeInt(point.readings.size)
            point.readings.forEach { (metric, reading) ->
                out.writeUTF(metric.name)
                out.writeUTF(reading.status.name)
                out.writeUTF(reading.source.take(256))
                out.writeUTF(reading.reason.orEmpty().take(512))
                if (reading.available) out.writeDouble(reading.value!!)
            }
            out.writeUTF(point.target.status.name)
            out.writeUTF(point.target.source.take(256))
            out.writeUTF(point.target.reason.orEmpty().take(512))
            if (point.target.available) out.writeUTF(point.target.value!!.take(512))
        }
        return bytes.toByteArray()
    }

    fun decode(bytes: ByteArray): PerformancePoint {
        require(bytes.size <= 65_536)
        val raw = ByteArrayOutputStream()
        GZIPInputStream(ByteArrayInputStream(bytes)).use { input ->
            val buffer = ByteArray(4_096)
            while (true) {
                val size = input.read(buffer)
                if (size < 0) break
                require(raw.size() + size <= 65_536) { "Performance payload too large" }
                raw.write(buffer, 0, size)
            }
        }
        return DataInputStream(ByteArrayInputStream(raw.toByteArray())).use { input ->
            require(input.readInt() == 1) { "Unknown performance payload version" }
            val elapsed = input.readLong()
            val epoch = input.readLong()
            require(elapsed >= 0)
            val count = input.readInt()
            require(count in 0..PerformanceMetric.entries.size)
            val readings = LinkedHashMap<PerformanceMetric, Reading<Double>>()
            repeat(count) {
                val metric = PerformanceMetric.valueOf(input.readUTF())
                val status = ReadingStatus.valueOf(input.readUTF())
                val source = input.readUTF()
                val reason = input.readUTF().ifEmpty { null }
                val value = if (status == ReadingStatus.AVAILABLE) input.readDouble() else null
                readings[metric] = if (value == null || metric.accepts(value)) Reading(value, source, status, reason)
                    else Reading.unavailable(source, "保存的样本无效", ReadingStatus.ERROR)
            }
            val targetStatus = ReadingStatus.valueOf(input.readUTF())
            val targetSource = input.readUTF()
            val targetReason = input.readUTF().ifEmpty { null }
            val target = Reading(if (targetStatus == ReadingStatus.AVAILABLE) input.readUTF() else null,
                targetSource, targetStatus, targetReason)
            PerformancePoint(elapsed, epoch, readings, target)
        }
    }
}
