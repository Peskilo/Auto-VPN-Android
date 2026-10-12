package dev.typenil.vpnclient.ui.home

import org.junit.Assert.assertEquals
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

    @Test fun `hero art keeps full size up to 1_3x and shrinks beyond`() {
        assertEquals(1f, heroArtScale(1.0f), 0f)
        assertEquals(1f, heroArtScale(1.3f), 0f)
        assertEquals(0.81f, heroArtScale(1.5f), 0.01f)
        assertEquals(0.52f, heroArtScale(2.0f), 0.01f)
        assertTrue(heroArtScale(1.5f) > heroArtScale(2.0f))
    }
}
