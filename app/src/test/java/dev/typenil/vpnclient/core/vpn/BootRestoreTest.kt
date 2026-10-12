package dev.typenil.vpnclient.core.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Post-boot restore decision: restore, or tell the user why not. */
class BootRestoreTest {
    private class Sink : NoticeSink {
        val posted = mutableListOf<AutoStartNotice>()
        override fun post(notice: AutoStartNotice): Boolean { posted += notice; return true }
        override fun cancel() {}
    }

    private fun run(wanted: Boolean, consented: Boolean, start: () -> Unit = {}): Pair<Int, List<AutoStartNotice>> {
        val sink = Sink()
        var starts = 0
        restoreOrNotify(wanted, consented, AutoStartNotifier(sink)) { starts++; start() }
        return starts to sink.posted
    }

    @Test fun `wanted and consented starts the service without a notice`() =
        assertEquals(1 to emptyList<AutoStartNotice>(), run(wanted = true, consented = true))

    @Test fun `failed foreground start posts the start-failed notice and does not throw`() =
        assertEquals(1 to listOf(AutoStartNotice.StartFailed),
            run(wanted = true, consented = true) { throw IllegalStateException("fgs not allowed") })

    @Test fun `missing consent posts the permission notice and never starts`() =
        assertEquals(0 to listOf(AutoStartNotice.VpnPermission), run(wanted = true, consented = false))

    @Test fun `not wanted stays silent whatever the consent`() {
        assertEquals(0 to emptyList<AutoStartNotice>(), run(wanted = false, consented = true))
        assertTrue(run(wanted = false, consented = false).second.isEmpty())
    }
}
