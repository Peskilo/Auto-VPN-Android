package dev.typenil.vpnclient.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class RelativeAgeTest {
    @Test
    fun `age rounds down into the largest whole unit`() {
        assertEquals(RelativeAge(RelativeUnit.Now, 0), relativeAge(0))
        assertEquals(RelativeAge(RelativeUnit.Now, 0), relativeAge(59_999L))
        assertEquals(RelativeAge(RelativeUnit.Minutes, 1), relativeAge(60_000L))
        assertEquals(RelativeAge(RelativeUnit.Minutes, 59), relativeAge(3_599_999L))
        assertEquals(RelativeAge(RelativeUnit.Hours, 1), relativeAge(3_600_000L))
        assertEquals(RelativeAge(RelativeUnit.Hours, 23), relativeAge(86_399_999L))
        assertEquals(RelativeAge(RelativeUnit.Days, 3), relativeAge(3 * 86_400_000L + 5))
    }
}
