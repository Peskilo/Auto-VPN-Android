package dev.typenil.vpnclient.ui.home

import dev.typenil.vpnclient.core.engine.OutboundItemInfo
import dev.typenil.vpnclient.core.engine.URLTEST_FRESH_WINDOW_MS
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

/** What Home may say about the live path on top of "Connected". */
enum class HomeHealthHint { None, Degraded, Unreachable }

/**
 * Health hint from REAL probe evidence only. [nowMonotonicMs] must be the clock the health
 * store stamps with (`System.nanoTime() / 1_000_000`), not wall time; projection through
 * [ConnectionHealth.at] means a 60 s old probe is Expired and yields [HomeHealthHint.None].
 *
 * | Connection state | DnsReachable (Fresh) | TrafficForwarding (Fresh) | Hint        |
 * |------------------|----------------------|---------------------------|-------------|
 * | not Connected    | any                  | any                       | None        |
 * | Connected        | Degraded/Failed      | Degraded/Failed           | Unreachable |
 * | Connected        | Degraded/Failed      | Ok/Unverified/not Fresh   | Degraded    |
 * | Connected        | Ok/Unverified/not Fresh | Degraded/Failed        | Degraded    |
 * | Connected        | otherwise            | otherwise                 | None        |
 *
 * Not Fresh = Unobserved or Expired (their status is Unverified). Every other level is ignored:
 * OutboundReachable has no producer in ConnectionManager, and Tun/Engine/Underlay/Consent/
 * LastSuccessfulCheck do not say whether traffic flows right now.
 */
fun homeHealthHint(
    connection: VpnConnectionState,
    health: ConnectionHealth,
    nowMonotonicMs: Long,
): HomeHealthHint {
    if (connection !is VpnConnectionState.Connected) return HomeHealthHint.None
    val projected = health.at(nowMonotonicMs)
    fun failing(level: HealthLevel) = projected.observations.firstOrNull { it.level == level }
        ?.let { it.freshness == HealthFreshness.Fresh && (it.status == HealthStatus.Degraded || it.status == HealthStatus.Failed) }
        ?: false
    val dns = failing(HealthLevel.DnsReachable)
    val traffic = failing(HealthLevel.TrafficForwarding)
    return when {
        dns && traffic -> HomeHealthHint.Unreachable
        dns || traffic -> HomeHealthHint.Degraded
        else -> HomeHealthHint.None
    }
}

/** Re-evaluation period of [healthHintFlow]: a 60 s probe expires at most this late. */
internal const val HEALTH_HINT_TICK_MS = 5_000L

private val healthHintTicks: Flow<Unit> = flow {
    while (true) {
        emit(Unit)
        delay(HEALTH_HINT_TICK_MS)
    }
}

/**
 * [homeHealthHint] as a flow. The health store only emits when it is written, so expiry (60 s TTL on
 * the monotonic clock) needs its own [HEALTH_HINT_TICK_MS] tick; it lives here and not in the 30 s
 * Home clock so the latency age keeps its slow cadence. `distinctUntilChanged` means the big Home
 * combine re-emits only when the hint itself changes, never on a bare tick. The tick is cold: it runs
 * only while Home's uiState is collected.
 */
fun healthHintFlow(
    connection: Flow<VpnConnectionState>,
    health: Flow<ConnectionHealth>,
    nowMonotonicMs: () -> Long,
    ticks: Flow<Unit> = healthHintTicks,
): Flow<HomeHealthHint> =
    combine(connection, health, ticks) { c, h, _ -> homeHealthHint(c, h, nowMonotonicMs()) }
        .distinctUntilChanged()

/**
 * Whole minutes since the newest real urltest measurement of [items], or null unless a measurement
 * exists AND is older than [URLTEST_FRESH_WINDOW_MS] (never measured, a fresh one, or a clock that
 * moved back are not "stale"). Only [OutboundItemInfo.urlTestTime] counts; nothing is estimated.
 */
fun staleLatencyAgeMinutes(items: List<OutboundItemInfo>, nowMs: Long): Int? {
    val newest = items.filter { (it.urlTestDelayMs ?: 0) > 0 && it.urlTestTime > 0 }
        .maxOfOrNull { it.urlTestTime } ?: return null
    val ageMs = nowMs - newest * 1000
    return if (ageMs > URLTEST_FRESH_WINDOW_MS) (ageMs / 60_000).toInt() else null
}
