package com.dateroem.woodsales.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

// "is it possible to add more themes like the dark and light theme and what
// we have will be default ... a user can change the theme and it will be
// remembered and when that user for that account goes into app or the
// mobile the theme is still the one they choose last" (20 October 2026).
// Forest - the board's Living Forest greens - is the default; Dark and Light
// are chosen in Settings and saved on the phone and with the account (the
// website and the desktop app open in it too).
enum class AppTheme(val id: String, val label: String) {
    FOREST("forest", "Forest"),
    DARK("dark", "Dark"),
    LIGHT("light", "Light");

    companion object {
        fun fromId(id: String?): AppTheme = entries.firstOrNull { it.id == id } ?: FOREST
    }
}

/** Every colour that changes with the theme. */
class DateroemPalette(
    val background: Color,
    val surface: Color,
    val text: Color,
    val textMuted: Color,
    // "since this wood for areas previously having the orange color looks good
    // you can extend it to the mobile and desktop" - the flat amber accent is
    // the website's wood: one sapwood tone (from the freshly cut stump photo)
    // wherever a single colour is needed - text, borders, progress, focus -
    // and the painted plank/log-end textures (Wood.kt) for filled buttons and
    // the round add buttons. On Light it is a darker, barkier wood so small
    // text stays readable on white.
    val accent: Color,
    val accentHover: Color,
    val danger: Color,
    /** Hairlines and faint tints: white on the dark themes, black on Light. */
    val line: Color,
    /** The side menu's panel. */
    val drawer: Color,
    /** The bottom of the long forms' backdrop. */
    val sheetEnd: Color,
    /** The fingerprint lock's backdrop, top to bottom. */
    val lockGradient: List<Color>,
    val isLight: Boolean
)

val ForestPalette = DateroemPalette(
    background = Color(0xFF0F241B),
    surface = Color(0xFF1D4232),
    text = Color(0xFFF5F5F1),
    textMuted = Color(0xFFB3C2B4),
    accent = Color(0xFFEB9A4B),
    accentHover = Color(0xFFF4B566),
    danger = Color(0xFFE5484D),
    line = Color.White,
    drawer = Color(0xFF0E1F15),
    sheetEnd = Color(0xFF0E1F15),
    lockGradient = listOf(Color(0xFF0F241B), Color(0xFF1D4232), Color(0xFF295A45)),
    isLight = false
)

// Charcoal greys with the same wood, for anyone who finds the green tiring.
private val DarkPalette = DateroemPalette(
    background = Color(0xFF121417),
    surface = Color(0xFF1E2124),
    text = Color(0xFFF2F2EF),
    textMuted = Color(0xFFA9B1B6),
    accent = Color(0xFFEB9A4B),
    accentHover = Color(0xFFF4B566),
    danger = Color(0xFFE5484D),
    line = Color.White,
    drawer = Color(0xFF0E1012),
    sheetEnd = Color(0xFF0E1012),
    lockGradient = listOf(Color(0xFF121417), Color(0xFF1E2124), Color(0xFF2A2E33)),
    isLight = false
)

// Warm paper with dark text.
private val LightPalette = DateroemPalette(
    background = Color(0xFFF4F1EA),
    surface = Color(0xFFFFFFFF),
    text = Color(0xFF1C2520),
    textMuted = Color(0xFF57635B),
    accent = Color(0xFFA4561A),
    accentHover = Color(0xFF8A4512),
    danger = Color(0xFFC5292E),
    line = Color.Black,
    drawer = Color(0xFFFBF9F4),
    sheetEnd = Color(0xFFEBE5D9),
    lockGradient = listOf(Color(0xFFF4F1EA), Color(0xFFFFFFFF), Color(0xFFEBE5D9)),
    isLight = true
)

/**
 * The theme on screen. Its values are Compose state, so every colour below
 * read while drawing follows a change at once - nothing is rebuilt, the app
 * isn't restarted and the fingerprint lock isn't asked for again.
 */
object DateroemThemeState {
    /** The person's choice (Settings; SessionManager keeps it). */
    var chosen by mutableStateOf(AppTheme.FOREST)

    /**
     * True for the entrance and sign-in: they are built on the forest
     * photographs and come before the account is known, so they keep the
     * Forest colours whatever was chosen (AppRoot).
     */
    var forestOnly by mutableStateOf(true)

    val palette: DateroemPalette
        get() = if (forestOnly) ForestPalette else paletteOf(chosen)
}

/** A theme's colours - also for Settings' small previews of each one. */
fun paletteOf(theme: AppTheme): DateroemPalette = when (theme) {
    AppTheme.FOREST -> ForestPalette
    AppTheme.DARK -> DarkPalette
    AppTheme.LIGHT -> LightPalette
}

val DateroemBackground: Color get() = DateroemThemeState.palette.background
val DateroemSurface: Color get() = DateroemThemeState.palette.surface
val DateroemAccent: Color get() = DateroemThemeState.palette.accent
val DateroemAccentHover: Color get() = DateroemThemeState.palette.accentHover
val DateroemText: Color get() = DateroemThemeState.palette.text
val DateroemTextMuted: Color get() = DateroemThemeState.palette.textMuted
val DateroemDanger: Color get() = DateroemThemeState.palette.danger
val DateroemLine: Color get() = DateroemThemeState.palette.line
val DateroemDrawer: Color get() = DateroemThemeState.palette.drawer
val DateroemSheetEnd: Color get() = DateroemThemeState.palette.sheetEnd

// Dark bark brown - text and icons sitting on painted wood (the same in every theme).
val WoodOn = Color(0xFF2A1508)
val WoodBark = Color(0xFF4A260E)
