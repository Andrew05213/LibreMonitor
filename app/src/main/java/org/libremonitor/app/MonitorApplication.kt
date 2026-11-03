/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app

import android.app.Application

class MonitorApplication : Application() {
    val runtime: MonitorRuntime by lazy { MonitorRuntime(this) }
}
