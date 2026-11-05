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
    /** Fixed reduced frame for the overlay's aggregate CPU counter. */
    suspend fun readOverlayFrame(): PrivilegedNodeFrame? = readFrame()
    /** Filtered read-only diagnostics, requested only by the visible dashboard. */
    suspend fun readDiagnosticsFrame(): PrivilegedNodeFrame? = null
    suspend fun grantPermissions(): PermissionGrantResult
    /** Best-effort foreground surface frame timing; null means not measured. */
    suspend fun readFps(): org.libremonitor.core.FpsSample? = null
    fun close()
}
