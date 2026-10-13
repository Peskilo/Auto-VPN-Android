package dev.typenil.vpnclient.ui.home

import dev.typenil.vpnclient.core.subscription.model.NodeSummary
import dev.typenil.vpnclient.core.subscription.model.ProtocolType
import dev.typenil.vpnclient.core.vpn.ConnectionHealth
import dev.typenil.vpnclient.core.vpn.HealthLevel
import dev.typenil.vpnclient.core.vpn.HealthObservation
import dev.typenil.vpnclient.core.vpn.HealthReason
import dev.typenil.vpnclient.core.vpn.HealthStatus
import dev.typenil.vpnclient.core.vpn.VpnConnectionState
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Virtual-time checks that the hint follows the 60 s probe TTL without a health write. */
@OptIn(ExperimentalCoroutinesApi::class)
class HealthHintFlowTest {
    private val node = NodeSummary("n", "synthetic", ProtocolType.SOCKS, "127.0.0.1")
    private val connected = VpnConnectionState.Connected(node, Instant.EPOCH, null)

    private fun health(dns: HealthStatus, traffic: HealthStatus, at: Long) = ConnectionHealth(
        observations = HealthLevel.entries.map { level ->
            val status = when (level) {
                HealthLevel.DnsReachable -> dns
                HealthLevel.TrafficForwarding -> traffic
                else -> null
            }
            if (status == null) HealthObservation(level)
            else HealthObservation(level, status, HealthReason.DnsAnswered, checkedAtMillis = at)
        },
    )

    private class Run(val hints: MutableList<Pair<Long, HomeHealthHint>>)

    private fun TestScope.collect(
        state: MutableStateFlow<VpnConnectionState>,
        health: MutableStateFlow<ConnectionHealth>,
    ): Run {
        val hints = mutableListOf<Pair<Long, HomeHealthHint>>()
        backgroundScope.launch {
            healthHintFlow(state, health, { currentTime }).collect { hints += currentTime to it }
        }
        return Run(hints)
    }

    private suspend fun TestScope.at(ms: Long) {
        advanceTimeBy(ms - currentTime)
        runCurrent()
    }

    @Test fun `a fresh probe expires at the first tick after the TTL and only then re-emits`() = runTest {
        val state = MutableStateFlow<VpnConnectionState>(connected)
        val health = MutableStateFlow(health(HealthStatus.Degraded, HealthStatus.Ok, at = 0))
        val run = collect(state, health)
        at(59_999)
        assertEquals(listOf(0L to HomeHealthHint.Degraded), run.hints)
        at(120_000)
        // No re-emission on the bare ticks in between; expiry lands within one tick of the TTL.
        assertEquals(2, run.hints.size)
        val (t, h) = run.hints[1]
        assertEquals(HomeHealthHint.None, h)
        assertTrue("expired at $t", t in 60_000L..65_000L)
    }

    @Test fun `leaving Connected clears the hint immediately`() = runTest {
        val state = MutableStateFlow<VpnConnectionState>(connected)
        val health = MutableStateFlow(health(HealthStatus.Degraded, HealthStatus.Ok, at = 0))
        val run = collect(state, health)
        at(10_000)
        state.value = VpnConnectionState.Idle
        runCurrent()
        assertEquals(listOf(0L to HomeHealthHint.Degraded, 10_000L to HomeHealthHint.None), run.hints)
    }

    @Test fun `a second failing level upgrades Degraded to Unreachable on publish`() = runTest {
        val state = MutableStateFlow<VpnConnectionState>(connected)
        val health = MutableStateFlow(health(HealthStatus.Degraded, HealthStatus.Ok, at = 0))
        val run = collect(state, health)
        at(7_000)
        health.value = health(HealthStatus.Degraded, HealthStatus.Degraded, at = 7_000)
        runCurrent()
        assertEquals(listOf(0L to HomeHealthHint.Degraded, 7_000L to HomeHealthHint.Unreachable), run.hints)
    }

    @Test fun `republishing a new observation restarts the expiry window`() = runTest {
        val state = MutableStateFlow<VpnConnectionState>(connected)
        val health = MutableStateFlow(health(HealthStatus.Degraded, HealthStatus.Ok, at = 0))
        val run = collect(state, health)
        at(50_000)
        health.value = health(HealthStatus.Degraded, HealthStatus.Ok, at = 50_000)
        at(109_000)
        // Same hint republished: nothing new, and still shown past the old 60 s mark.
        assertEquals(listOf(0L to HomeHealthHint.Degraded), run.hints)
        at(130_000)
        assertEquals(2, run.hints.size)
        assertEquals(HomeHealthHint.None, run.hints[1].second)
        assertTrue("expired at ${run.hints[1].first}", run.hints[1].first in 110_000L..115_000L)
    }
}
