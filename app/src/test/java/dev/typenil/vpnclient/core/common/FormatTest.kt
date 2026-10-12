package dev.typenil.vpnclient.core.common

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    private val en = listOf("B", "KB", "MB", "GB", "TB")
    private val ru = listOf("Б", "КБ", "МБ", "ГБ", "ТБ")
    private val enLocale = Locale.US
    private val ruLocale = Locale.forLanguageTag("ru")

    private fun en(b: Long) = formatBytes(b, en, enLocale)
    private fun ru(b: Long) = formatBytes(b, ru, ruLocale)

    @Test
    fun `en units on boundaries`() {
        assertEquals("0 B", en(0))
        assertEquals("1 B", en(1))
        assertEquals("1023 B", en(1023))
        assertEquals("1.0 KB", en(1024))
        assertEquals("1.5 KB", en(1536))
        assertEquals("1.0 MB", en(1024L * 1024))
        assertEquals("1.0 MB", en(1024L * 1024 - 1)) // would round to 1024.0 KB
        assertEquals("2.0 GB", en(2L * 1024 * 1024 * 1024))
        assertEquals("5.0 TB", en(5L * 1024 * 1024 * 1024 * 1024))
        assertEquals("8192.0 TB", en(8192L * 1024 * 1024 * 1024 * 1024)) // top unit never overflows to a missing one
    }

    @Test
    fun `ru uses Cyrillic units and a decimal comma`() {
        assertEquals("0 Б", ru(0))
        assertEquals("1023 Б", ru(1023))
        assertEquals("1,0 КБ", ru(1024))
        assertEquals("1,5 КБ", ru(1536))
        assertEquals("1,0 МБ", ru(1024L * 1024))
        assertEquals("1,5 МБ", ru(1024L * 1024 * 3 / 2))
        assertEquals("2,0 ГБ", ru(2L * 1024 * 1024 * 1024))
        assertEquals("3,0 ТБ", ru(3L * 1024 * 1024 * 1024 * 1024))
    }

    @Test
    fun `negatives clamp to zero`() {
        assertEquals("0 B", en(-1))
        assertEquals("0 Б", ru(-5))
    }

    @Test
    fun `rate applies the localized pattern`() {
        assertEquals("1.5 KB/s", formatRate(1536, en, "%1\$s/s", enLocale))
        assertEquals("1,5 КБ/с", formatRate(1536, ru, "%1\$s/с", ruLocale))
        assertEquals("0 Б/с", formatRate(0, ru, "%1\$s/с", ruLocale))
    }
}
