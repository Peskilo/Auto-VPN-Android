package dev.typenil.vpnclient.ui.subscriptions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubscriptionHostTest {
    @Test
    fun `https and http give only the host`() {
        assertEquals("example.com", subscriptionHost("https://example.com/sub/SYNTHETIC?token=abc"))
        assertEquals("example.com", subscriptionHost("  http://example.com  "))
        assertEquals("example.com", subscriptionHost("HTTPS://Example.COM/x"))
    }

    @Test
    fun `port and credentials are never shown`() {
        assertEquals("example.com", subscriptionHost("https://example.com:8443/s?t=1"))
        assertEquals("example.com", subscriptionHost("https://user:pass@example.com/s"))
    }

    @Test
    fun `ipv6 literal keeps brackets`() {
        assertEquals("[2001:db8::1]", subscriptionHost("https://[2001:db8::1]:444/s"))
    }

    @Test
    fun `idn host is shown in its real ascii form`() {
        assertEquals("xn--e1afmkfd.xn--p1ai", subscriptionHost("https://пример.рф/sub"))
    }

    @Test
    fun `garbage empty scheme-less and share links hide the line`() {
        listOf("", "   ", "not a url", "example.com/sub?token=x", "https://", "http://:80/", "ftp://example.com/x",
            "vless://00000000-0000-0000-0000-000000000000@example.com:443").forEach {
            assertNull(it, subscriptionHost(it))
        }
    }

    @Test
    fun `host inside a sing-box import link is the fetched one`() {
        assertEquals(
            "example.com",
            subscriptionHost("sing-box://import-remote-profile?url=https%3A%2F%2Fexample.com%2Fs%3Ft%3D1#x"),
        )
    }
}
