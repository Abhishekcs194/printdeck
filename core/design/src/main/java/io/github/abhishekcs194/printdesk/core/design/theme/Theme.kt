package io.github.abhishekcs194.printdesk.core.design.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Entry point for PrintDesk styling.
 *
 * Dynamic colour is intentionally NOT supported. This is a print tool: the sheet
 * preview has to be trustworthy, and letting the wallpaper recolour the chrome
 * around a page proof undermines that. The brand palette is fixed.
 */
@Composable
fun PrintDeskTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors

    // Material components still need a ColorScheme; map our tokens onto it so
    // anything we haven't wrapped still lands on-brand.
    val materialScheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.primary,
            onPrimary = colors.onPrimary,
            background = colors.background,
            onBackground = colors.foreground,
            surface = colors.background,
            onSurface = colors.foreground,
            surfaceVariant = colors.muted,
            onSurfaceVariant = colors.mutedForeground,
            outline = colors.border,
            outlineVariant = colors.border,
            error = colors.danger,
        )
    } else {
        lightColorScheme(
            primary = colors.primary,
            onPrimary = colors.onPrimary,
            background = colors.background,
            onBackground = colors.foreground,
            surface = colors.background,
            onSurface = colors.foreground,
            surfaceVariant = colors.muted,
            onSurfaceVariant = colors.mutedForeground,
            outline = colors.border,
            outlineVariant = colors.border,
            error = colors.danger,
        )
    }

    CompositionLocalProvider(LocalPrintDeskColors provides colors) {
        MaterialTheme(
            colorScheme = materialScheme,
            typography = PrintDeskTypography,
            shapes = PrintDeskShapes,
            content = content,
        )
    }
}

/**
 * Semantic colours. Prefer this over [MaterialTheme.colorScheme] in app code —
 * it exposes the roles this design language actually uses (muted, border,
 * paper) which Material's scheme has no equivalent for.
 */
object PrintDeskTheme {
    val colors: PrintDeskColors
        @Composable @ReadOnlyComposable get() = LocalPrintDeskColors.current
}
