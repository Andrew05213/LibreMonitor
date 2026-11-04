/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.core

import org.junit.Assert.*
import org.junit.Test

class FpsSessionTest {
    private val sample = FpsSample(Reading(60.0, "fake"), Reading(45.0, "fake"),
        Reading(16.7, "fake"), Reading("SurfaceView[com.example/.Main]", "fake"), rootSessionId = 7L)

    @Test fun matchingLiveSessionKeepsReading() {
        assertSame(sample, sample.validatedFor(RootState(RootAccess.GRANTED, sessionId = 7L)))
    }

    @Test fun rootStoppedAfterIoBeforePublishClearsAllFpsFields() {
        val rejected = sample.validatedFor(RootState(RootAccess.DISABLED, sessionId = 7L))
        assertFalse(rejected.fps.available)
        assertFalse(rejected.onePercentLow.available)
        assertFalse(rejected.frameTimeMs.available)
        assertFalse(rejected.target.available)
    }

    @Test fun replacementSessionCannotPublishPriorSessionReading() {
        assertFalse(sample.validatedFor(RootState(RootAccess.GRANTED, sessionId = 8L)).fps.available)
    }

    @Test fun untaggedNumberIsRejectedButActiveFailureReasonIsPreserved() {
        val root = RootState(RootAccess.GRANTED, sessionId = 7L)
        assertFalse(sample.copy(rootSessionId = null).validatedFor(root).fps.available)
        val failure = FpsSample(fps = Reading.unavailable("fake", "No frame timestamps"))
        assertSame(failure, failure.validatedFor(root))
    }
}
