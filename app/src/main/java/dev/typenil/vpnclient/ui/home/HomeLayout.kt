package dev.typenil.vpnclient.ui.home

/** Landscape phones and split-screen windows: height is the scarce axis. */
internal const val ShortWindowMaxHeightDp = 420

internal fun isShortWindow(heightDp: Int): Boolean = heightDp <= ShortWindowMaxHeightDp
