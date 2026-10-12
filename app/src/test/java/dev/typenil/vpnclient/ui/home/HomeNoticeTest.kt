package dev.typenil.vpnclient.ui.home

import dev.typenil.vpnclient.core.subscription.model.NodeSummary
import dev.typenil.vpnclient.core.subscription.model.ProtocolType
import dev.typenil.vpnclient.core.vpn.VpnConnectionState
import dev.typenil.vpnclient.core.vpn.VpnError
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeNoticeTest {
    private val node = NodeSummary("n1", "synthetic", ProtocolType.SOCKS, "127.0.0.1")
    private val connected = VpnConnectionState.Connected(node, Instant.EPOCH, null)

    @Test fun `no error and no rejected pick shows nothing`() {
        assertNull(HomeUiState(connection = connected).notice())
        assertNull(HomeUiState().notice())
    }

    @Test fun `failed session is a failure notice`() {
        val ui = HomeUiState(connection = VpnConnectionState.Error(VpnError.NoNodeSelected, null))
        assertEquals(HomeNotice.Failure(VpnError.NoNodeSelected), ui.notice())
    }

    @Test fun `rejected live pick while connected is not a failure`() {
        val ui = HomeUiState(connection = connected, selectionError = VpnError.UnencryptedTransport)
        assertEquals(HomeNotice.SelectionRejected(VpnError.UnencryptedTransport), ui.notice())
    }

    @Test fun `a real failure outranks a stale pick rejection`() {
        val ui = HomeUiState(
            connection = VpnConnectionState.Error(VpnError.UnencryptedTransport, node),
            selectionError = VpnError.UnencryptedTransport,
        )
        assertEquals(HomeNotice.Failure(VpnError.UnencryptedTransport), ui.notice())
    }
}
