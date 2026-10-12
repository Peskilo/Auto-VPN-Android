package dev.typenil.vpnclient.ui.common

import dev.typenil.vpnclient.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class A11yTest {
    @Test fun `expanded state picks distinct strings`() {
        assertEquals(R.string.a11y_expanded, expandedStateRes(true))
        assertEquals(R.string.a11y_collapsed, expandedStateRes(false))
        assertNotEquals(expandedStateRes(true), expandedStateRes(false))
    }
}
