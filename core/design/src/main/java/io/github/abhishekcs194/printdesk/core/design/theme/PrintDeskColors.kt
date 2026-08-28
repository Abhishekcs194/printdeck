package io.github.abhishekcs194.printdesk.core.design.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic colour tokens.
 *
 * Components reference roles ("muted background", "border") rather than colours,
 * which is what makes dark mode free and stops one-off greys creeping in. Adding
 * a hard-coded [Color] to a component is the single fastest way to make this app
 * look generic, so don't.
 */
@Immutable
data class PrintDeskColors(
    /** Page and sheet backgrounds. */
    val background: Color,
    /** Primary text. */
    val foreground: Color,
    /** Tinted surfaces: table headers, inset panels, disabled fills. */
    val muted: Color,
    /** Secondary text, icons at rest, metadata. */
    val mutedForeground: Color,
    /** Hover and pressed surfaces, skeleton fill. */
    val accent: Color,
    /** Dividers and section outlines. This app draws structure with borders, not shadows. */
    val border: Color,
    /** Input outlines, slightly stronger than [border] so fields read as interactive. */
    val inputBorder: Color,
    /** Brand blue: primary actions, selection, focus. */
    val primary: Color,
    val onPrimary: Color,
    /** Low-opacity brand fill for selected rows and tinted pills. */
    val primaryMuted: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    /** Simulated paper in the sheet preview. Stays white in dark mode on purpose. */
    val paper: Color,
    val paperShadow: Color,
)

internal val LightColors = PrintDeskColors(
    background = White,
    foreground = Neutral900,
    muted = Neutral100,
    mutedForeground = Neutral500,
    accent = Neutral50,
    border = Neutral200,
    inputBorder = Neutral300,
    primary = MarkerBlue,
    onPrimary = White,
    primaryMuted = MarkerBlue10,
    success = MarkerGreen,
    warning = MustardYellow,
    danger = UnderlineRed,
    paper = PaperWhite,
    paperShadow = PaperShadow,
)

internal val DarkColors = PrintDeskColors(
    background = Dark950,
    foreground = Dark50,
    muted = Dark800,
    mutedForeground = Dark400,
    accent = Dark700,
    border = Dark700,
    inputBorder = Dark600,
    primary = MarkerBlueDark,
    onPrimary = Dark950,
    primaryMuted = MarkerBlueDark.copy(alpha = 0.16f),
    success = MarkerGreen,
    warning = MustardYellow,
    danger = UnderlineRed,
    paper = PaperWhite,
    paperShadow = Color(0x66000000),
)

internal val LocalPrintDeskColors = staticCompositionLocalOf { LightColors }
