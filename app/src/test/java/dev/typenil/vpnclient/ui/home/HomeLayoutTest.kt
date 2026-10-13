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

    @Test fun `hint cap clears the mascot face at default font`() {
        // 358dp hero, full-size art: 358 - 164*0.9 - 8 gap.
        assertEquals(202.4f, heroHintMaxWidthDp(358f, 164f, 1f), 0.01f)
    }

    @Test fun `hint cap widens as the art shrinks at 2x font`() {
        // heroMinEff 223.5dp and artScale ~0.52 at fontScale 2.0.
        assertEquals(244.59f, heroHintMaxWidthDp(358f, 223.5f, heroArtScale(2.0f)), 0.01f)
    }

    @Test fun `hint cap floors at forty percent of the hero`() {
        assertEquals(143.2f, heroHintMaxWidthDp(358f, 400f, 0.6f), 0.01f)
        assertEquals(80f, heroHintMaxWidthDp(200f, 300f, 1f), 0.01f)
    }
}
