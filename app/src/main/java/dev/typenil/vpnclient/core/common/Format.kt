package dev.typenil.vpnclient.core.common

import android.content.res.Resources
import dev.typenil.vpnclient.R
import java.util.Locale

/** Unit labels in display order: B, KB, MB, GB, TB (localized by the caller). */
fun formatBytes(bytes: Long, units: List<String>, locale: Locale): String {
    if (bytes < 1024) return "${bytes.coerceAtLeast(0)} ${units[0]}"
    var value = bytes.toDouble()
    var unit = 0
    do {
        value /= 1024.0
        unit++
        // 1048575 B is 1023.999 KB: round first so it reads "1.0 MB", not "1024.0 KB".
    } while (Math.round(value * 10) / 10.0 >= 1024.0 && unit < units.lastIndex)
    return String.format(locale, "%.1f %s", value, units[unit])
}

/** `1.5 MB/s`-style rate; [rateFormat] is the localized pattern with one `%1$s`. */
fun formatRate(bytesPerSec: Long, units: List<String>, rateFormat: String, locale: Locale): String =
    String.format(locale, rateFormat, formatBytes(bytesPerSec, units, locale))

private fun Resources.byteUnits() = listOf(
    getString(R.string.format_unit_b), getString(R.string.format_unit_kb), getString(R.string.format_unit_mb),
    getString(R.string.format_unit_gb), getString(R.string.format_unit_tb),
)

private fun Resources.displayLocale(): Locale = configuration.locales[0]

/** Byte count in the resources' language: `512 B` / `512 Б`, decimal separator by locale. */
fun Resources.formatBytes(bytes: Long): String = formatBytes(bytes, byteUnits(), displayLocale())

/** Rate in the resources' language: `1.5 KB/s` / `1,5 КБ/с`. */
fun Resources.formatRate(bytesPerSec: Long): String =
    formatRate(bytesPerSec, byteUnits(), getString(R.string.format_rate), displayLocale())
