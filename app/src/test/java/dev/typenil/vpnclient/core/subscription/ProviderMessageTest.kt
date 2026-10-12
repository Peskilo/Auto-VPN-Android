package dev.typenil.vpnclient.core.subscription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderMessageTest {
    @Test fun `blank or markup-only text yields nothing`() {
        assertNull(sanitizeProviderMessage(null))
        assertNull(sanitizeProviderMessage("  \n\t "))
        assertNull(sanitizeProviderMessage("<br/><script></script>\u202e\u0000"))
    }

    @Test fun `a cut never splits a surrogate pair`() {
        val text = sanitizeProviderMessage("a".repeat(PROVIDER_MESSAGE_MAX_CHARS - 2) + "\uD83D\uDE00\uD83D\uDE00")!!
        assertTrue(text.length <= PROVIDER_MESSAGE_MAX_CHARS)
        assertTrue(text.endsWith("…"))
        assertTrue(text.dropLast(1).last() != '\uD83D')
    }

    @Test fun `short plain text passes through unchanged`() {
        assertEquals("Device limit: 3 of 3", sanitizeProviderMessage("Device limit: 3 of 3"))
    }
}
