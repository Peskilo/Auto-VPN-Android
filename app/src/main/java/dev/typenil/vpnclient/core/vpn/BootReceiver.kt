package dev.typenil.vpnclient.core.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import dev.typenil.vpnclient.core.common.log.SecureLog
import dev.typenil.vpnclient.data.settings.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Restores the tunnel after boot / app update when the user left it running.
 *
 * Guards: `desiredVpnRunning` must still be set, and `VpnService.prepare`
 * must report consent is not needed (a revoked permission means no silent
 * restart — the user has to grant it again in the UI).
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var settings: SettingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        // Injection is bytecode-instrumented into this method by the Hilt
        // Gradle plugin (@OnReceiveBytecodeInjectionMarker) — super.onReceive
        // can't be called in source (BroadcastReceiver.onReceive is abstract).
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        val pending = goAsync()
        scope.launch {
            try {
                // goAsync grants ~10s — bound the DataStore read so a cold
                // store under boot load can't stall past it.
                val wanted = withTimeoutOrNull(BOOT_READ_TIMEOUT_MS) {
                    settings.desiredVpnRunning.first()
                } == true
                val consented = VpnService.prepare(context) == null
                restoreOrNotify(wanted, consented, AutoStartNotifier(AndroidNoticeSink(context))) {
                    ContextCompat.startForegroundService(context, ClientVpnService.restoreIntent(context))
                }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val BOOT_READ_TIMEOUT_MS = 8_000L
    }
}

/**
 * The post-boot decision: restore when wanted and consented, otherwise tell the user
 * instead of staying silent. FGS-start can still throw on edge states (API 31+) — a crash
 * inside onReceive is worse than a missed restore, so a failed start becomes a notice.
 */
internal fun restoreOrNotify(wanted: Boolean, consented: Boolean, notifier: AutoStartNotifier, start: () -> Unit) {
    if (wanted && consented) {
        runCatching(start).onFailure {
            SecureLog.w("BootReceiver", "boot restore start failed")
            notifier.failed(AutomaticStartBranch.Restore, VpnError.Unexpected("boot restore start failed"))
        }
    } else if (wanted) {
        notifier.failed(AutomaticStartBranch.Restore, VpnError.PermissionDenied)
    }
}
