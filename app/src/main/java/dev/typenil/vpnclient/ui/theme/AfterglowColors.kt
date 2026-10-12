package dev.typenil.vpnclient.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/** Brand surfaces remain stable even when Material dynamic color is enabled. */
@Immutable
data class AfterglowPalette(
    val paper: Color,
    val paperSecondary: Color,
    val surfaceElevated: Color,
    val ink: Color,
    val muted: Color,
    val border: Color,
    /** Boundaries of controls (fields, switches, chips): WCAG non-text 3:1. [border] stays decorative. */
    val borderStrong: Color,
    val coral: Color,
    val coralLight: Color,
    val jade: Color,
    val amber: Color,
    val error: Color,
    val errorSurface: Color,
    val onInk: Color,
    val actionSurface: Color,
    val onActionSurface: Color,
    val errorAccent: Color,
    /** State surfaces, see [stateColors]; the status text always names the state too. */
    val heroNeutral: Color,
    val heroProgress: Color,
    val heroPositive: Color,
    val heroError: Color,
    val onHero: Color,
    val onHeroMuted: Color,
)

val LightAfterglow =
    AfterglowPalette(
        paper = Color(0xFFFAF4ED),
        paperSecondary = Color(0xFFF2E9E0),
        surfaceElevated = Color(0xFFFFF8F1),
        ink = Color(0xFF272528),
        muted = Color(0xFF675D5C),
        border = Color(0xFFD7C9C0),
        borderStrong = Color(0xFF8A7E76),
        coral = Color(0xFFB53531),
        coralLight = Color(0xFFFF8580),
        jade = Color(0xFF5DC9A7),
        amber = Color(0xFFF4C27B),
        error = Color(0xFFAE303C),
        errorSurface = Color(0xFFF8DFD9),
        onInk = Color(0xFFFFFAF6),
        actionSurface = Color(0xFF272528),
        onActionSurface = Color(0xFFFFFAF6),
        errorAccent = Color(0xFFFF5A6A),
        heroNeutral = Color(0xFF5E5659),
        heroProgress = Color(0xFF82500F),
        heroPositive = Color(0xFF17483F),
        heroError = Color(0xFFA12632),
        onHero = Color(0xFFFFFAF6),
        onHeroMuted = Color(0xFFE6DAD3),
    )

val DarkAfterglow =
    AfterglowPalette(
        paper = Color(0xFF171719),
        paperSecondary = Color(0xFF252427),
        surfaceElevated = Color(0xFF302D30),
        ink = Color(0xFFF7F0E8),
        muted = Color(0xFFBEB4B0),
        border = Color(0xFF4F4545),
        borderStrong = Color(0xFF8B7F7B),
        coral = Color(0xFFFF8580),
        coralLight = Color(0xFFFFA49C),
        jade = Color(0xFF5DC9A7),
        amber = Color(0xFFF4C27B),
        error = Color(0xFFFF9CA5),
        errorSurface = Color(0xFF4C292D),
        onInk = Color(0xFF171719),
        actionSurface = Color(0xFF302D30),
        onActionSurface = Color(0xFFFFFAF6),
        errorAccent = Color(0xFFFF9CA5),
        heroNeutral = Color(0xFF3B373A),
        heroProgress = Color(0xFF6A4510),
        heroPositive = Color(0xFF102B27),
        heroError = Color(0xFF7A1F2A),
        onHero = Color(0xFFFFFAF6),
        onHeroMuted = Color(0xFFE6DAD3),
    )

val LocalAfterglowPalette = compositionLocalOf { LightAfterglow }

/** Semantic meaning of a connection state; red is reserved for [Error]. */
enum class StateTone { Neutral, Progress, Positive, Error }

/** Colors of the hero and the action bar for one [StateTone]. */
@Immutable
data class StateColors(val hero: Color, val onHero: Color, val onHeroMuted: Color, val accent: Color, val line: Color)

/** Pure tone -> tokens mapping; screens never pick state colors themselves. */
fun AfterglowPalette.stateColors(tone: StateTone): StateColors =
    when (tone) {
        StateTone.Neutral -> StateColors(heroNeutral, onHero, onHeroMuted, coralLight, coralLight)
        StateTone.Progress -> StateColors(heroProgress, onHero, onHeroMuted, amber, amber)
        StateTone.Positive -> StateColors(heroPositive, onHero, onHeroMuted, jade, jade)
        StateTone.Error -> StateColors(heroError, onHero, onHeroMuted, errorAccent, errorAccent)
    }
