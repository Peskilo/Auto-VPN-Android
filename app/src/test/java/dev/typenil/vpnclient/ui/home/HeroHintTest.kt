package dev.typenil.vpnclient.ui.home

import dev.typenil.vpnclient.R
import dev.typenil.vpnclient.ui.theme.StateTone
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeroHintTest {
    private fun strings(locale: String): Map<String, String> {
        val res = File("src/main/res").takeIf { it.isDirectory } ?: File("app/src/main/res")
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        val nodes = factory.newDocumentBuilder().parse(File(res, "$locale/strings.xml"))
            .getElementsByTagName("string")
        return (0 until nodes.length).associate {
            nodes.item(it).attributes.getNamedItem("name").nodeValue to nodes.item(it).textContent
        }
    }

    @Test fun `only a Connected hero with a failed check turns amber`() {
        val bad = listOf(HomeHealthHint.Degraded, HomeHealthHint.Unreachable)
        bad.forEach { assertEquals(StateTone.Progress, heroTone(VisualState.Connected, it)) }
        assertEquals(StateTone.Positive, heroTone(VisualState.Connected, HomeHealthHint.None))
        for (state in VisualState.entries.filter { it != VisualState.Connected }) {
            bad.forEach { assertEquals(state.tone(), heroTone(state, it)) }
        }
        // Red stays exclusive to Error.
        assertEquals(StateTone.Error, heroTone(VisualState.Error, HomeHealthHint.Unreachable))
    }

    @Test fun `None has no line and the others use their own strings`() {
        assertNull(HomeHealthHint.None.textRes())
        assertEquals(R.string.home_health_degraded, HomeHealthHint.Degraded.textRes())
        assertEquals(R.string.home_health_unreachable, HomeHealthHint.Unreachable.textRes())
    }

    @Test fun `hint and stale texts are the agreed en and ru wording`() {
        val en = strings("values")
        val ru = strings("values-ru")
        assertEquals("Connected, but one connection check failed", en["home_health_degraded"])
        assertEquals("Connected, but connection checks failed", en["home_health_unreachable"])
        assertEquals("Latency outdated · %1\$d min ago", en["home_latency_outdated"])
        assertEquals("Подключено, но одна из проверок соединения не прошла", ru["home_health_degraded"])
        assertEquals("Подключено, но проверки соединения не прошли", ru["home_health_unreachable"])
        assertEquals("Задержка устарела · %1\$d мин назад", ru["home_latency_outdated"])
    }
}
