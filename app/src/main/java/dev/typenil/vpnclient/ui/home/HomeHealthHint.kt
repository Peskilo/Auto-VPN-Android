package dev.typenil.vpnclient.ui.home

import dev.typenil.vpnclient.core.engine.OutboundItemInfo
import dev.typenil.vpnclient.core.engine.URLTEST_FRESH_WINDOW_MS
import dev.typenil.vpnclient.core.vpn.ConnectionHealth
import dev.typenil.vpnclient.core.vpn.HealthFreshness
import dev.typenil.vpnclient.core.vpn.HealthLevel
import dev.typenil.vpnclient.core.vpn.HealthStatus
import dev.typenil.vpnclient.core.vpn.VpnConnectionState

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
