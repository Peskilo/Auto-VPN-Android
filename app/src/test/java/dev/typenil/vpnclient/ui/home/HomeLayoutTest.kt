package dev.typenil.vpnclient.ui.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutTest {
    @Test fun `landscape phone height is short`() {
        assertTrue(isShortWindow(411))
        assertTrue(isShortWindow(ShortWindowMaxHeightDp))
    }

    @Test fun `portrait phone and tablet heights are not short`() {
        assertFalse(isShortWindow(ShortWindowMaxHeightDp + 1))
        assertFalse(isShortWindow(914))
        assertFalse(isShortWindow(1280))
    }
}
