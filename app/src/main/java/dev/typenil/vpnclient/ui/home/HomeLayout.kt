package dev.typenil.vpnclient.ui.home

/** Landscape phones and split-screen windows: height is the scarce axis. */
internal const val ShortWindowMaxHeightDp = 420

internal fun isShortWindow(heightDp: Int): Boolean = heightDp <= ShortWindowMaxHeightDp

/** Hero mascot size as a fraction of the hero height. Full up to 1.3x, then it
 * shrinks as r^1.5 (r = 1.3/fontScale) so the wider headline never sits on the face. */
internal fun heroArtScale(fontScale: Float): Float =
    if (fontScale <= 1.3f) 1f else (1.3f / fontScale).let { it * kotlin.math.sqrt(it) }
