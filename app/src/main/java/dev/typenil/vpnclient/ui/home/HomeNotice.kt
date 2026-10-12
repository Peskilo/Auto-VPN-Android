package dev.typenil.vpnclient.ui.home

import dev.typenil.vpnclient.core.vpn.VpnConnectionState
import dev.typenil.vpnclient.core.vpn.VpnError

/**
 * The single notice slot under the hero. A failed session already reads
 * "Connection failed" in the hero, so [Failure] carries only the reason;
 * [SelectionRejected] is a live pick the policy refused while the tunnel keeps
 * running on the previous server — the hero stays truthfully "Connected".
 */
internal sealed interface HomeNotice {
    data class Failure(val error: VpnError) : HomeNotice
    data class SelectionRejected(val error: VpnError) : HomeNotice
}

/** A real failure outranks a stale pick rejection; nothing else is invented. */
internal fun HomeUiState.notice(): HomeNotice? =
    when (val c = connection) {
        is VpnConnectionState.Error -> HomeNotice.Failure(c.error)
        else -> selectionError?.let { HomeNotice.SelectionRejected(it) }
    }
