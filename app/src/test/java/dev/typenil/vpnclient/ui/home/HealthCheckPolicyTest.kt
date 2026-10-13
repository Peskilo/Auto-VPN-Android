package dev.typenil.vpnclient.ui.home

import dev.typenil.vpnclient.core.subscription.model.NodeSummary
import dev.typenil.vpnclient.core.subscription.model.ProtocolType
import dev.typenil.vpnclient.core.vpn.ConnectionHealth
import dev.typenil.vpnclient.core.vpn.HealthLevel
import dev.typenil.vpnclient.core.vpn.HealthObservation
import dev.typenil.vpnclient.core.vpn.HealthStatus
import dev.typenil.vpnclient.core.vpn.VpnConnectionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class HealthCheckPolicyTest {
    private var now = 0L
    private val policy = HealthCheckPolicy { now }

    private fun snap(
        dns: HealthStatus = HealthStatus.Unverified,
        traffic: HealthStatus = HealthStatus.Unverified,
        checkedAt: Long? = 0L,
    ) = ConnectionHealth(observations = HealthLevel.entries.map {
        when (it) {
            HealthLevel.DnsReachable -> HealthObservation(it, dns, checkedAtMillis = checkedAt)
            HealthLevel.TrafficForwarding -> HealthObservation(it, traffic, checkedAtMillis = checkedAt)
            else -> HealthObservation(it)
        }
    })

    /** Seconds (1 s steps) at which the bad-state policy asks for a probe. */
    private fun firesWhileBad(from: Long, until: Long): List<Long> =
        (from..until).filter { now = it * 1000; policy.onTick(true) }

    @Test fun `bad result retries at 15 30 60 then every 120 seconds`() {
        assertTrue(policy.onOpen(stale = true)) // probe at t=0
        (1L..7L).forEach { now = it * 1000; assertFalse(policy.onTick(false)) }
        // The bad result lands at t=8, 8 s after our own request: still counted from t=0.
        assertEquals(listOf(15L, 45L, 105L, 225L, 345L), firesWhileBad(8, 400))
    }

    @Test fun `a bad state not caused by our request counts from when it was first seen`() {
        assertEquals(listOf(115L, 145L, 205L), firesWhileBad(100, 210))
    }

    @Test fun `recovery resets the backoff`() {
        firesWhileBad(0, 50) // 15, 45
        now = 51_000; assertFalse(policy.onTick(false))
        // Starts over (+15, +30), counted from the t=45 request whose result this is.
        assertEquals(listOf(60L, 90L), firesWhileBad(52, 100))
    }

    @Test fun `ok state never asks for a probe and has no periodic timer`() {
        (0L..900L).forEach { now = it * 1000; assertFalse(policy.onTick(false)) }
    }

    @Test fun `open probe needs stale evidence and honours the sixty second cooldown`() {
        assertFalse(policy.onOpen(stale = false))
        assertTrue(policy.onOpen(stale = true))
        now = 59_999; assertFalse(policy.onOpen(stale = true))
        now = 60_000; assertTrue(policy.onOpen(stale = true))
    }

    @Test fun `leaving Home keeps the open cooldown but ends the bad streak`() {
        assertTrue(policy.onOpen(stale = true))
        now = 20_000; policy.onTick(true); now = 35_000; assertTrue(policy.onTick(true)) // streak=1
        policy.reset()
        now = 40_000; assertFalse(policy.onOpen(stale = true)) // cooldown from the 35 s request
        now = 60_000; assertFalse(policy.onTick(true)) // streak restarted: new 15 s gap
        now = 75_000; assertTrue(policy.onTick(true))
    }

    @Test fun `bad is the raw status of dns or traffic only and survives expiry`() {
        assertTrue(healthBad(snap(dns = HealthStatus.Degraded)))
        assertTrue(healthBad(snap(traffic = HealthStatus.Failed)))
        assertFalse(healthBad(snap(dns = HealthStatus.Ok, traffic = HealthStatus.Unverified)))
        assertFalse(healthBad(ConnectionHealth()))
        val other = ConnectionHealth(observations = HealthLevel.entries.map {
            HealthObservation(it, if (it == HealthLevel.UnderlyingNetwork) HealthStatus.Failed else HealthStatus.Unverified)
        })
        assertFalse(healthBad(other))
        // Long past the 60 s TTL the raw status is still Degraded (the hint is gone, retries go on).
        assertTrue(healthBad(snap(dns = HealthStatus.Degraded, checkedAt = 0)))
    }

    @Test fun `stale means no fresh dns or traffic result`() {
        assertTrue(healthStale(ConnectionHealth(), 0))
        assertFalse(healthStale(snap(traffic = HealthStatus.Ok, checkedAt = 1_000), 60_999))
        assertTrue(healthStale(snap(traffic = HealthStatus.Ok, checkedAt = 1_000), 61_000))
        assertTrue(healthStale(snap(checkedAt = null), 1_000))
    }

    // ---- the loop Home runs while STARTED, on virtual time ----

    private val connected = VpnConnectionState.Connected(
        NodeSummary("n", "Example", ProtocolType.OTHER, "example.invalid"), Instant.EPOCH, null)

    @Test fun `loop probes on open then backs off while bad and stops when ok`() = runTest {
        val state = MutableStateFlow<VpnConnectionState>(connected)
        val health = MutableStateFlow(ConnectionHealth())
        var requests = 0
        val job = launch { runHealthChecks(state, health, HealthCheckPolicy { currentTime }, { currentTime }) { requests++ } }
        runCurrent()
        assertEquals(1, requests) // open: stale evidence
        advanceTimeBy(8_000)
        health.value = snap(dns = HealthStatus.Degraded, checkedAt = 8_000)
        advanceTimeBy(8_000) // t=16 s
        assertEquals(2, requests) // +15 s
        advanceTimeBy(28_000) // t=44 s
        assertEquals(2, requests)
        advanceTimeBy(2_000) // t=46 s
        assertEquals(3, requests) // +30 s
        health.value = snap(dns = HealthStatus.Ok, checkedAt = 46_000)
        advanceTimeBy(600_000)
        assertEquals(3, requests)
        job.cancel()
    }

    @Test fun `loop is silent with fresh ok evidence and when not Connected`() = runTest {
        val state = MutableStateFlow<VpnConnectionState>(connected)
        val health = MutableStateFlow(snap(traffic = HealthStatus.Ok, checkedAt = 0))
        var requests = 0
        val job = launch { runHealthChecks(state, health, HealthCheckPolicy { currentTime }, { currentTime }) { requests++ } }
        advanceTimeBy(30_000)
        assertEquals(0, requests)
        health.value = snap(dns = HealthStatus.Degraded, checkedAt = 30_000)
        state.value = VpnConnectionState.Reconnecting(connected.node, VpnConnectionState.Reconnecting.Reason.NetworkUnavailable, 1)
        advanceTimeBy(600_000)
        assertEquals(0, requests)
        job.cancel()
    }

    @Test fun `cancelling the loop stops all requests`() = runTest {
        val state = MutableStateFlow<VpnConnectionState>(connected)
        val health = MutableStateFlow(snap(dns = HealthStatus.Degraded, checkedAt = 0))
        var requests = 0
        val job = launch { runHealthChecks(state, health, HealthCheckPolicy { currentTime }, { currentTime }) { requests++ } }
        advanceTimeBy(20_000)
        val before = requests
        assertTrue(before >= 1)
        job.cancel()
        advanceTimeBy(600_000)
        assertEquals(before, requests)
    }
}
