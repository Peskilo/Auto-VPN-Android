package dev.typenil.vpnclient.ui.subscriptions

import dev.typenil.vpnclient.core.subscription.ImportUrlExtractor
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * The host a subscription request would go to, for display only: no scheme,
 * port, login, path or query (the token lives there). Null — hide the line —
 * for empty/garbage input, links without a scheme and single-node share links.
 * Same parsing as the add flow, so what is shown is what is fetched. IDN hosts
 * come out in their ASCII (punycode) form: the real destination name.
 * Never logged.
 */
internal fun subscriptionHost(input: String): String? {
    val candidate = ImportUrlExtractor.extract(null, input.trim(), null) ?: return null
    if (candidate.kind != ImportUrlExtractor.Kind.Subscription) return null
    val host = candidate.url.toHttpUrlOrNull()?.host ?: return null
    return if (':' in host) "[$host]" else host
}
