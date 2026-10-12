package dev.typenil.vpnclient.ui.common

import dev.typenil.vpnclient.core.vpn.VpnConnectionState
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Null means unknown; this query cannot diagnose a particular disconnect. */
fun interface BatteryOptimizationStatus {
    fun enabled(): Boolean?
}

internal fun showBatteryHint(state: VpnConnectionState, enabled: Boolean?, dismissed: Boolean?): Boolean =
    state is VpnConnectionState.Connected && enabled == true && dismissed == false

/** Home battery card state: the VM's former inline logic, split out so it runs on the JVM. */
internal class BatteryHintModel(
    private val scope: kotlinx.coroutines.CoroutineScope,
    state: kotlinx.coroutines.flow.Flow<VpnConnectionState>,
    dismissed: kotlinx.coroutines.flow.Flow<Boolean?>,
    private val status: BatteryOptimizationStatus,
    private val persistDismissal: suspend () -> Unit,
) {
    private val enabled = kotlinx.coroutines.flow.MutableStateFlow<Boolean?>(null)

    val visible: kotlinx.coroutines.flow.StateFlow<Boolean> =
        kotlinx.coroutines.flow.combine(state, enabled, dismissed, ::showBatteryHint).stateIn(
            scope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), false,
        )

    fun refresh() { enabled.value = status.enabled() }

    fun dismiss() {
        scope.launch {
            try { persistDismissal() } catch (_: java.io.IOException) {
                // Keep the card: a failed write is not a persisted dismissal.
            }
        }
    }
}
