package io.github.abhishekcs194.printdesk.core.design.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * A 14sp base scale, not Material's 16sp.
 *
 * This app is dense — page grids, capability lists, printer attributes — and the
 * default Material scale makes dense UI look like a blown-up phone demo. Weights
 * stop at SemiBold; Bold headings are the other half of that generic look.
 */
private val Default = FontFamily.Default

private val TrimmedLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

val PrintDeskTypography = Typography(
    // Screen titles.
    headlineSmall = TextStyle(
        fontFamily = Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.4).sp,
        lineHeightStyle = TrimmedLineHeight,
    ),
    // Section headings inside bordered cards.
    titleMedium = TextStyle(
        fontFamily = Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.2).sp,
        lineHeightStyle = TrimmedLineHeight,
    ),
    titleSmall = TextStyle(
        fontFamily = Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        lineHeightStyle = TrimmedLineHeight,
    ),
    // Body — the base size.
    bodyMedium = TextStyle(
        fontFamily = Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        lineHeightStyle = TrimmedLineHeight,
    ),
    // Secondary text, metadata, helper copy.
    bodySmall = TextStyle(
        fontFamily = Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        lineHeightStyle = TrimmedLineHeight,
    ),
    // Buttons and tabs.
    labelLarge = TextStyle(
        fontFamily = Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        lineHeightStyle = TrimmedLineHeight,
    ),
    // Pills, badges, captions.
    labelSmall = TextStyle(
        fontFamily = Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.1.sp,
        lineHeightStyle = TrimmedLineHeight,
    ),
)
