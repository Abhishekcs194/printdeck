package io.github.abhishekcs194.printdeck.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import io.github.abhishekcs194.printdeck.core.design.component.ScreenHeader
import io.github.abhishekcs194.printdeck.core.design.component.Section
import io.github.abhishekcs194.printdeck.core.design.theme.PrintDeckTheme
import io.github.abhishekcs194.printdeck.core.design.theme.Spacing

/**
 * Root composable. Navigation lands here once the Documents and Layout screens
 * exist; for now this renders the shell so the design tokens can be verified on
 * a device before any feature code depends on them.
 */
@Composable
fun PrintDeckApp() {
    val colors = PrintDeckTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding()
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        ScreenHeader(
            title = "Documents",
            subtitle = "Pick a file to lay out and print.",
        )
        Section(
            title = "Scaffold",
            description = "Design tokens, typography and the bordered section shell.",
        ) {
            Text(
                text = "Imposition engine, preview and IPP transport land next.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.mutedForeground,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PrintDeckAppPreview() {
    PrintDeckTheme { PrintDeckApp() }
}
