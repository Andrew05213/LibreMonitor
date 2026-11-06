/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.data.collectors

import org.libremonitor.core.BatterySample
import org.libremonitor.core.Reading

/** Slow metadata only. Never retains level, current, voltage, power, temperature or remaining charge. */
internal class BatteryDetailsCache {
    private var sampledAt: Long? = null
    private var identity: String? = null
    private var cached: BatterySample? = null

    fun enrich(base: BatterySample, now: Long, rootIdentity: String, collect: () -> BatterySample): BatterySample {
        val at = sampledAt
        if (identity != rootIdentity || at == null || now - at !in 0..29_999) {
            cached = collect(); sampledAt = now; identity = rootIdentity
        }
        val slow = cached ?: return base
        fun <T> Reading<T>.metadata() = copy(reason = listOfNotNull(reason, "慢变字段，最长缓存 30 秒").joinToString("；"))
        return base.copy(
            healthStatus = if (base.healthStatus.available) base.healthStatus else slow.healthStatus.metadata(),
            healthPercent = slow.healthPercent.metadata(),
            designCapacityMah = slow.designCapacityMah.metadata(),
            fullChargeCapacityMah = slow.fullChargeCapacityMah.metadata(),
            cycleCount = slow.cycleCount.metadata(),
        )
    }
}
