package dev.typenil.vpnclient.core.subscription

import dev.typenil.vpnclient.core.subscription.model.SubscriptionError.InactiveReason
import dev.typenil.vpnclient.core.subscription.model.SubscriptionUserInfo

internal const val PROVIDER_MESSAGE_MAX_CHARS = 300

private val MARKUP = Regex("<[^>]*>")

/**
 * Hostile provider text (the `announce` header of an error response) reduced to one short
 * plain-text line: markup tags dropped, control and invisible format characters (incl. bidi
 * overrides) removed, whitespace collapsed, length capped. Null when nothing readable is left.
 * Callers show it in a non-clickable Text and never log it.
 */
internal fun sanitizeProviderMessage(raw: String?): String? {
    if (raw == null) return null
    val cleaned = StringBuilder()
    // Bound the work first; a header can be far larger than anything we display.
    for (c in MARKUP.replace(raw.take(PROVIDER_MESSAGE_MAX_CHARS * 8), " ")) {
        val type = Character.getType(c)
        when {
            Character.isWhitespace(c) || c == '\u00A0' -> if (cleaned.isNotEmpty() && cleaned.last() != ' ') cleaned.append(' ')
            Character.isISOControl(c) || type == Character.FORMAT.toInt() ||
                type == Character.PRIVATE_USE.toInt() || type == Character.UNASSIGNED.toInt() -> Unit
            else -> cleaned.append(c)
        }
    }
    val text = cleaned.toString().trim()
    if (text.isEmpty()) return null
    if (text.length <= PROVIDER_MESSAGE_MAX_CHARS) return text
    var end = PROVIDER_MESSAGE_MAX_CHARS - 1
    if (Character.isHighSurrogate(text[end - 1])) end-- // never cut a surrogate pair
    return text.substring(0, end).trimEnd() + "…"
}

/** Why a node-less response means "inactive", from real `subscription-userinfo` values only. */
internal fun inactiveReason(info: SubscriptionUserInfo?, nowEpochSeconds: Long): InactiveReason? = when {
    info == null -> null
    info.expireEpochSeconds != null && info.expireEpochSeconds <= nowEpochSeconds -> InactiveReason.Expired
    info.totalBytes > 0 && info.usedBytes >= info.totalBytes -> InactiveReason.TrafficUsedUp
    else -> null
}
