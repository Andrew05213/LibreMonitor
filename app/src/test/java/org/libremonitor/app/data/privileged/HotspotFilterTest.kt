/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.data.privileged

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class HotspotFilterTest {
    @Test fun filterExcludesKeysAddressesAndUnrelatedStateMachines() {
        val file = Files.createTempFile("wifi-status-fixture", ".txt")
        try {
            file.toFile().writeText("""Dump of ClientModeManager
current StateMachine mode: StartedState
Dump of SoftApManager id=1
current StateMachine mode: StartedState
mCurrentSoftApConfiguration: SSID=TEST_ONLY_SSID password=TEST_ONLY_KEY
getConnectedClientList().size(): 2
client MAC=aa:bb:cc:dd:ee:ff
Dump of OtherManager
current StateMachine mode: IdleState
getConnectedClientList().size(): 99
""")
            val script = RootProtocol.diagnosticNodes().single().command.replace("/system/bin/dumpsys wifi", "cat '$file'")
            val process = ProcessBuilder("sh", "-c", script).start()
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(0, process.waitFor())
            assertEquals("AP_BEGIN\nSTATE=enabled\nCLIENTS=2\n", output)
            assertFalse(output.contains("TEST_ONLY")); assertFalse(output.contains("aa:bb"))
            assertTrue(RootProtocol.frameScript("0123456789abcdef0123456789abcdef", RootProtocol.diagnosticNodes()).contains("BEGIN"))
        } finally { Files.deleteIfExists(file) }
    }
}
