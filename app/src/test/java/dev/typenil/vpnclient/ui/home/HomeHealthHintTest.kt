package dev.typenil.vpnclient.ui.home

import dev.typenil.vpnclient.core.engine.OutboundItemInfo
import dev.typenil.vpnclient.core.engine.URLTEST_FRESH_WINDOW_MS
import dev.typenil.vpnclient.core.subscription.model.NodeSummary
import dev.typenil.vpnclient.core.subscription.model.ProtocolType
import dev.typenil.vpnclient.core.vpn.ConnectionHealth
import dev.typenil.vpnclient.core.vpn.HealthLevel
import dev.typenil.vpnclient.core.vpn.HealthObservation
import dev.typenil.vpnclient.core.vpn.HealthReason
import dev.typenil.vpnclient.core.vpn.HealthStatus
import dev.typenil.vpnclient.core.vpn.VpnConnectionState
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeHealthHintTest {
    private val node = NodeSummary("n", "synthetic", ProtocolType.SOCKS, "127.0.0.1")
    private val connected = VpnConnectionState.Connected(node, Instant.EPOCH, null)
    private val checkedAt = 1_000L

    private fun obs(level: HealthLevel, status: HealthStatus, at: Long? = checkedAt) =
        HealthObservation(level, status, HealthReason.DnsAnswered, checkedAtMillis = at)

    private fun health(dns: HealthStatus?, traffic: HealthStatus?, at: Long? = checkedAt) = ConnectionHealth(
        observations = HealthLevel.entries.map { level ->
            when (level) {
                HealthLevel.DnsReachable -> dns?.let { obs(level, it, at) }
                HealthLevel.TrafficForwarding -> traffic?.let { obs(level, it, at) }
                else -> null
            } ?: HealthObservation(level)
        },
    )

    private fun hint(h: ConnectionHealth, now: Long = checkedAt + 10, state: VpnConnectionState = connected) =
        homeHealthHint(state, h, now)

    @Test fun `case table for a Connected session with fresh evidence`() {
        val ok = HealthStatus.Ok
        val bad = HealthStatus.Degraded
        val un = HealthStatus.Unverified
        assertEquals(HomeHealthHint.None, hint(health(ok, un)))
        assertEquals(HomeHealthHint.None, hint(health(ok, ok)))
        assertEquals(HomeHealthHint.Degraded, hint(health(bad, ok)))
        assertEquals(HomeHealthHint.Degraded, hint(health(ok, bad)))
        assertEquals(HomeHealthHint.Degraded, hint(health(bad, un)))
        assertEquals(HomeHealthHint.Degraded, hint(health(null, HealthStatus.Failed)))
        assertEquals(HomeHealthHint.Unreachable, hint(health(bad, bad)))
        assertEquals(HomeHealthHint.Unreachable, hint(health(HealthStatus.Failed, bad)))
        assertEquals(HomeHealthHint.None, hint(health(null, null)))
    }

    @Test fun `unobserved levels give None`() {
        assertEquals(HomeHealthHint.None, hint(ConnectionHealth()))
    }

    @Test fun `evidence expires at the ttl boundary and a rewound clock is not evidence`() {
        val h = health(HealthStatus.Degraded, HealthStatus.Degraded)
        assertEquals(HomeHealthHint.Unreachable, hint(h, checkedAt + 59_999))
        assertEquals(HomeHealthHint.None, hint(h, checkedAt + 60_000))
        assertEquals(HomeHealthHint.None, hint(h, checkedAt - 1))
    }

    @Test fun `only one expired level leaves the fresh one as Degraded`() {
        val dns = obs(HealthLevel.DnsReachable, HealthStatus.Degraded, at = checkedAt)
        val traffic = obs(HealthLevel.TrafficForwarding, HealthStatus.Degraded, at = checkedAt + 50_000)
        val h = ConnectionHealth(observations = HealthLevel.entries.map {
            when (it) { HealthLevel.DnsReachable -> dns; HealthLevel.TrafficForwarding -> traffic; else -> HealthObservation(it) }
        })
        assertEquals(HomeHealthHint.Unreachable, hint(h, checkedAt + 59_000))
        assertEquals(HomeHealthHint.Degraded, hint(h, checkedAt + 61_000))
    }

    @Test fun `anything but Connected resets to None`() {
        val h = health(HealthStatus.Degraded, HealthStatus.Degraded)
        listOf(
            VpnConnectionState.Idle,
            VpnConnectionState.Connecting(node),
            VpnConnectionState.Reconnecting(node, VpnConnectionState.Reconnecting.Reason.NetworkUnavailable, 1),
        ).forEach { assertEquals(it.toString(), HomeHealthHint.None, hint(h, state = it)) }
    }

    @Test fun `other levels never raise a hint`() {
        val h = ConnectionHealth(observations = HealthLevel.entries.map {
            if (it == HealthLevel.DnsReachable || it == HealthLevel.TrafficForwarding) HealthObservation(it)
            else obs(it, HealthStatus.Failed)
        })
        assertEquals(HomeHealthHint.None, hint(h))
    }

    private fun item(delay: Int?, timeSec: Long) = OutboundItemInfo("t", "socks", delay, timeSec)
    private val nowMs = 10_000_000L

    @Test fun `stale age needs a real measurement older than the window`() {
        val windowEdge = (nowMs - URLTEST_FRESH_WINDOW_MS) / 1000
        assertNull(staleLatencyAgeMinutes(listOf(item(120, windowEdge)), nowMs))
        assertEquals(7, staleLatencyAgeMinutes(listOf(item(120, windowEdge - 1)), nowMs))
        assertEquals(10, staleLatencyAgeMinutes(listOf(item(120, nowMs / 1000 - 600)), nowMs))
    }

    @Test fun `never measured fresh or future measurements are not stale`() {
        assertNull(staleLatencyAgeMinutes(emptyList(), nowMs))
        assertNull(staleLatencyAgeMinutes(listOf(item(null, 0)), nowMs))
        assertNull(staleLatencyAgeMinutes(listOf(item(120, 0)), nowMs))
        assertNull(staleLatencyAgeMinutes(listOf(item(0, 5)), nowMs))
        assertNull(staleLatencyAgeMinutes(listOf(item(120, nowMs / 1000 - 60)), nowMs))
        assertNull(staleLatencyAgeMinutes(listOf(item(120, nowMs / 1000 + 100)), nowMs))
    }

    @Test fun `newest measurement decides so one fresh item means not stale`() {
        val items = listOf(item(100, nowMs / 1000 - 3600), item(90, nowMs / 1000 - 30))
        assertNull(staleLatencyAgeMinutes(items, nowMs))
    }
}
