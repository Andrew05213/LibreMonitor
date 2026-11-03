/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.data.privileged

import kotlinx.coroutines.flow.StateFlow
import org.libremonitor.core.PermissionGrantResult
import org.libremonitor.core.PrivilegedNodeFrame
import org.libremonitor.core.RootState

/** User-initiated root capability. Implementations must never accept caller-provided shell text. */
interface RootSession {
    val state: StateFlow<RootState>
    suspend fun requestAuthorization(): Boolean
    suspend fun readFrame(): PrivilegedNodeFrame?
    suspend fun grantPermissions(): PermissionGrantResult
    fun close()
}
