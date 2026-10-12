package dev.typenil.vpnclient.ui.common

import dev.typenil.vpnclient.core.subscription.model.NodeSummary
import dev.typenil.vpnclient.core.subscription.model.ProtocolType
import dev.typenil.vpnclient.core.vpn.VpnConnectionState
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Home battery card rules, driven through the same model HomeViewModel uses (virtual time). */
@OptIn(ExperimentalCoroutinesApi::class)
class BatteryHintModelTest {
    private val node = NodeSummary("synthetic", "Test", ProtocolType.VLESS, "192.0.2.1:443")
    private val connected = VpnConnectionState.Connected(node, Instant.EPOCH, null)

    private class Rig(scope: TestScope, initial: VpnConnectionState, var optimized: Boolean?, dismissedInit: Boolean? = false,
                      var failWrite: Boolean = false) {
        val state = MutableStateFlow(initial)
        val dismissed = MutableStateFlow(dismissedInit)
        var writes = 0
        val model = BatteryHintModel(scope.backgroundScope, state, dismissed, { optimized }) {
            writes++
            if (failWrite) throw IOException("disk")
            dismissed.value = true
        }
        val visible get() = model.visible.value
        init { scope.backgroundScope.launch { model.visible.collect {} } }
    }

    private fun test(body: suspend TestScope.() -> Unit) = runTest(UnconfinedTestDispatcher(), testBody = body)

    @Test fun `shown only in real Connected with optimization on and not dismissed`() = test {
        val r = Rig(this, VpnConnectionState.Idle, optimized = true)
        r.model.refresh()
        assertFalse(r.visible)
        r.state.value = VpnConnectionState.Connecting(node)
        assertFalse(r.visible)
        r.state.value = connected
        assertTrue(r.visible)
        r.state.value = VpnConnectionState.Reconnecting(node, VpnConnectionState.Reconnecting.Reason.CoreFailure, 1)
        assertFalse(r.visible)
    }

    @Test fun `unknown or exempt battery status suppresses the card`() = test {
        val r = Rig(this, connected, optimized = null)
        assertFalse("before first refresh", r.visible)
        r.model.refresh()
        assertFalse("unknown", r.visible)
        r.optimized = false
        r.model.refresh()
        assertFalse("exempt", r.visible)
        r.optimized = true
        r.model.refresh()
        assertTrue(r.visible)
    }

    @Test fun `unreadable dismissal state suppresses the card`() = test {
        val r = Rig(this, connected, optimized = true, dismissedInit = null)
        r.model.refresh()
        assertFalse(r.visible)
    }

    @Test fun `dismissal persists across reconnect`() = test {
        val r = Rig(this, connected, optimized = true)
        r.model.refresh()
        assertTrue(r.visible)
        r.model.dismiss()
        assertEquals(1, r.writes)
        assertFalse(r.visible)
        r.state.value = VpnConnectionState.Idle
        r.state.value = connected.copy(since = Instant.ofEpochSecond(1))
        assertFalse(r.visible)
    }

    @Test fun `card stays when the dismissal write fails`() = test {
        val r = Rig(this, connected, optimized = true, failWrite = true)
        r.model.refresh()
        r.model.dismiss()
        assertEquals(1, r.writes)
        assertTrue(r.visible)
    }
}
