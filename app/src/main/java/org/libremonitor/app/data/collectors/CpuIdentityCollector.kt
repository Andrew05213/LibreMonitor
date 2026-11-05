/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.data.collectors

import android.content.Context
import android.os.Build
import org.libremonitor.core.CpuCatalog
import org.libremonitor.core.CpuIdentity

internal object CpuIdentityCollector {
    fun sample(context: Context): CpuIdentity {
        val catalog = runCatching { context.assets.open("soc_catalog.tsv").bufferedReader().use { CpuCatalog.parse(it.readText()) } }.getOrDefault(emptyList())
        val models = buildList {
            if (Build.VERSION.SDK_INT >= 31) add(Build.SOC_MODEL)
            add(Build.HARDWARE); add(Build.BOARD)
        }
        val capacities = (0 until boundedProcessorCount()).mapNotNull { id ->
            readBounded("/sys/devices/system/cpu/cpu$id/cpu_capacity", 64).value?.trim()?.toLongOrNull()
                ?.takeIf { it > 0 }?.let { id to it }
        }.toMap()
        return CpuCatalog.identify(catalog, models, readBounded("/proc/cpuinfo", 131_072).value.orEmpty(), capacities)
    }
}
