package io.github.abhishekcs194.printdeck.core.design.theme

import androidx.compose.ui.graphics.Color

/**
 * Raw palette. Nothing outside this file should reference these values directly —
 * UI code uses the semantic tokens in [PrintDeckColors] so that light and dark
 * themes stay in step and a palette change is a one-file edit.
 */

// Brand — carried over from the GoVisually design language.
internal val MarkerBlue = Color(0xFF0D6ABE)
internal val MarkerBlue90 = Color(0xFF0A559A)
internal val MarkerBlue30 = Color(0xFF3E8BD0)
internal val MarkerBlue10 = Color(0xFFE7F1FA)
internal val MarkerBlueDark = Color(0xFF4A9BE0)

// Status
internal val MarkerGreen = Color(0xFF16A34A)
internal val MustardYellow = Color(0xFFD97706)
internal val UnderlineRed = Color(0xFFDC2626)

// Light neutrals
internal val White = Color(0xFFFFFFFF)
internal val Neutral50 = Color(0xFFFAFAFA)
internal val Neutral100 = Color(0xFFF4F4F5)
internal val Neutral200 = Color(0xFFE4E4E7)
internal val Neutral300 = Color(0xFFD4D4D8)
internal val Neutral500 = Color(0xFF71717A)
internal val Neutral900 = Color(0xFF18181B)

// Dark neutrals
internal val Dark950 = Color(0xFF09090B)
internal val Dark900 = Color(0xFF121214)
internal val Dark800 = Color(0xFF1C1C1F)
internal val Dark700 = Color(0xFF27272A)
internal val Dark600 = Color(0xFF3F3F46)
internal val Dark400 = Color(0xFFA1A1AA)
internal val Dark50 = Color(0xFFFAFAFA)

// Paper — used by the sheet preview, which must read as paper in both themes
// rather than inheriting the surface colour.
internal val PaperWhite = Color(0xFFFFFFFF)
internal val PaperShadow = Color(0x1A000000)
