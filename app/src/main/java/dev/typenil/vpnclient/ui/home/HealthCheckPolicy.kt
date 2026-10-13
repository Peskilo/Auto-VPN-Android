package dev.typenil.vpnclient.ui.home

import dev.typenil.vpnclient.core.vpn.ConnectionHealth
import dev.typenil.vpnclient.core.vpn.HealthFreshness
import dev.typenil.vpnclient.core.vpn.HealthLevel
import dev.typenil.vpnclient.core.vpn.HealthStatus
import dev.typenil.vpnclient.core.vpn.VpnConnectionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

private val PROBE_LEVELS = setOf(HealthLevel.DnsReachable, HealthLevel.TrafficForwarding)

/** Raw (unprojected) status of the last probe results: bad stays bad after the 60 s
 *  TTL hides the hint, so retries continue until a probe actually recovers. */
internal fun healthBad(health: ConnectionHealth): Boolean =
    health.observations.any { it.level in PROBE_LEVELS && (it.status == HealthStatus.Degraded || it.status == HealthStatus.Failed) }

/** No fresh probe result at all (never probed, or both past their TTL). */
internal fun healthStale(health: ConnectionHealth, nowMs: Long): Boolean =
    health.observations.none { it.level in PROBE_LEVELS && it.at(nowMs).freshness == HealthFreshness.Fresh }

/**
 * When Home asks the manager for an on-demand probe. Pure: time comes from [nowMs]; the
 * caller decides visibility (it only drives this while Home is STARTED).
 *
 *  - open: one probe if the evidence is stale, at most once per [OPEN_COOLDOWN_MS];
 *  - bad: retries [GAPS_MS] apart (15 s, 30 s, 60 s, then every 120 s), measured between
 *    requests; a bad result landing right after our own request counts from that request;
 *  - OK / not Connected: nothing, and no periodic timer.
 */
internal class HealthCheckPolicy(private val nowMs: () -> Long) {
    private var lastRequestAt: Long? = null
    private var badSince: Long? = null
    private var streak = 0

    fun onOpen(stale: Boolean): Boolean {
        if (!stale) return false
        val now = nowMs()
        val last = lastRequestAt
        if (last != null && now - last < OPEN_COOLDOWN_MS) return false
        lastRequestAt = now
        return true
    }

    fun onTick(bad: Boolean): Boolean {
        if (!bad) {
            reset()
            return false
        }
        val now = nowMs()
        val last = lastRequestAt
        val since = badSince ?: (if (last != null && now - last <= RESULT_WAIT_MS) last else now).also { badSince = it }
        val anchor = maxOf(since, last ?: since)
        if (now - anchor < GAPS_MS[minOf(streak, GAPS_MS.lastIndex)]) return false
        lastRequestAt = now
        streak++
        return true
    }

    /** Leaving Home or Connected: the bad streak ends; the open cooldown deliberately survives. */
    fun reset() {
        badSince = null
        streak = 0
    }

    companion object {
        const val OPEN_COOLDOWN_MS = 60_000L
        /** A probe takes at most ~8 s (IpProbe) — a bad result this soon after a request is its result. */
        const val RESULT_WAIT_MS = 10_000L
        val GAPS_MS = longArrayOf(15_000, 30_000, 60_000, 120_000)
    }
}

private val healthCheckTicks: Flow<Unit> = flow {
    while (true) {
        emit(Unit)
        delay(HEALTH_HINT_TICK_MS)
    }
}

/** Runs while Home is STARTED (the caller cancels it on stop). [request] only ever asks the
 *  manager for a probe; the manager re-checks Connected and single-flight itself. */
internal suspend fun runHealthChecks(
    connection: Flow<VpnConnectionState>,
    health: Flow<ConnectionHealth>,
    policy: HealthCheckPolicy,
    nowMs: () -> Long,
    ticks: Flow<Unit> = healthCheckTicks,
    request: () -> Unit,
) {
    var first = true
    combine(connection.map { it is VpnConnectionState.Connected }.distinctUntilChanged(), health, ticks) { connected, h, _ -> connected to h }
        .collect { (connected, h) ->
            val opening = first
            first = false
            val fire = when {
                !connected -> { policy.reset(); false }
                opening && policy.onOpen(healthStale(h, nowMs())) -> true
                else -> policy.onTick(healthBad(h))
            }
            if (fire) request()
        }
}
