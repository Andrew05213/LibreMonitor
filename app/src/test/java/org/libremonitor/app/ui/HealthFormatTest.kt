/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.libremonitor.core.Reading
import java.util.Locale

class HealthFormatTest {
    @Test fun capacityHealthRangeAndDisplay() {
        listOf(100.0, 101.0, 150.0).forEach { assertTrue(validHealthPercent(it)) }
        listOf(0.0, 150.1, Double.NaN).forEach { assertFalse(validHealthPercent(it)) }
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            assertEquals("120.0 %", Reading(120.0, "test").validValue(validHealthPercent)?.let(::percentQuantity)?.display)
        } finally { Locale.setDefault(previous) }
    }
}
