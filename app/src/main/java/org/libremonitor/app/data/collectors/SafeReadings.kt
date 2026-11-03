/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.data.collectors

import android.system.ErrnoException
import android.system.OsConstants
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.CancellationException
import org.libremonitor.core.Reading
import org.libremonitor.core.ReadingStatus

/** Keep a denied or malformed node local to the metric that uses it. */
internal inline fun <T> safely(source: String, block: () -> Reading<T>): Reading<T> = try {
    block()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    failedReading(source, error)
}

internal fun <T> failedReading(source: String, error: Exception): Reading<T> {
    val causes = generateSequence<Throwable>(error) { it.cause }.take(8).toList()
    val denied = causes.any {
        it is SecurityException ||
            (it is ErrnoException && (it.errno == OsConstants.EACCES || it.errno == OsConstants.EPERM)) ||
            (it is IOException && (it.message.orEmpty().contains("Permission denied", true) ||
                it.message.orEmpty().contains("EACCES") || it.message.orEmpty().contains("EPERM")))
    }
    val missing = !denied && causes.any {
        (it is ErrnoException && (it.errno == OsConstants.ENOENT || it.errno == OsConstants.ENODEV)) ||
            (it is FileNotFoundException &&
                (it.message.orEmpty().contains("No such file", true) ||
                    it.message.orEmpty().contains("ENOENT") || it.message.orEmpty().contains("ENODEV")))
    }
    return Reading.unavailable(
        source,
        when {
            denied -> "系统拒绝读取；不尝试提权或绕过"
            missing -> "设备未提供此节点或 API"
            else -> "读取失败（${error.javaClass.simpleName}）"
        },
        when {
            denied -> ReadingStatus.PERMISSION_DENIED
            missing -> ReadingStatus.UNSUPPORTED
            else -> ReadingStatus.ERROR
        },
    )
}

internal inline fun <A, B> Reading<A>.mapReading(
    source: String = this.source,
    invalidReason: String = "返回值无效或超出合理范围",
    transform: (A) -> B?,
): Reading<B> {
    val input = value ?: return Reading.unavailable(source, reason ?: "没有可用数据", status)
    return safely(source) {
        val output = transform(input)
        if (output == null) Reading.unavailable(source, invalidReason, ReadingStatus.ERROR)
        else Reading(output, source, reason = reason)
    }
}

/**
 * Read only an explicit node, never a directory/process tree. At most maxBytes + 1
 * bytes are consumed. Oversize input is rejected, never parsed as complete data.
 */
internal fun readBounded(path: String, maxBytes: Int = 65_536): Reading<String> = safely(path) {
    require(maxBytes in 1..262_144)
    FileInputStream(path).use { input ->
        val bytes = ByteArray(maxBytes + 1)
        var count = 0
        while (count < bytes.size) {
            val read = input.read(bytes, count, bytes.size - count)
            if (read < 0) break
            if (read == 0) {
                val next = input.read()
                if (next < 0) break
                bytes[count++] = next.toByte()
            } else count += read
        }
        if (count > maxBytes) {
            Reading.unavailable(path, "节点内容超过有界读取上限（$maxBytes 字节）", ReadingStatus.ERROR)
        } else Reading(String(bytes, 0, count, Charsets.UTF_8), path)
    }
}

internal fun <T> unsupported(source: String, reason: String): Reading<T> =
    Reading.unavailable(source, reason)

internal fun <T> invalid(source: String, reason: String): Reading<T> =
    Reading.unavailable(source, reason, ReadingStatus.ERROR)

internal fun boundedProcessorCount(): Int = Runtime.getRuntime().availableProcessors().coerceIn(1, 256)

/** These exceptional fallbacks are used by the sampler without swallowing cancellation. */
internal inline fun <T> isolate(block: () -> T, fallback: (Exception) -> T): T = try {
    block()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    fallback(error)
}
