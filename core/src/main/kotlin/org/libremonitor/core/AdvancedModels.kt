/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.core

enum class RootAccess { DISABLED, REQUESTING, GRANTED, DENIED, UNAVAILABLE, ERROR }

data class RootState(
    val access: RootAccess = RootAccess.DISABLED,
    val message: String = "",
    val sessionId: Long = 0,
)

/** Fixed full node paths are keys; the sole HAL key is "thermalservice". */
data class PrivilegedNodeFrame(
    val readings: Map<String, Reading<String>>,
    val sampledElapsedRealtimeMs: Long,
    val sessionId: Long,
)
