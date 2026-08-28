package io.github.abhishekcs194.printdeck.ui.editor

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import io.github.abhishekcs194.printdeck.core.design.PrintDeckIcons
import io.github.abhishekcs194.printdeck.core.design.component.PrintDeckIconButton
import io.github.abhishekcs194.printdeck.core.design.theme.PrintDeckTheme
import io.github.abhishekcs194.printdeck.core.design.theme.Radius
import io.github.abhishekcs194.printdeck.core.design.theme.Spacing

/**
 * Shows the sheet that will actually come out of the printer.
 *
 * The image is a render of the imposed PDF itself, not a re-drawing of the
 * settings, so it cannot disagree with the output. That is the whole reason to
 * build the preview this way rather than the cheaper route of painting a mock
 * layout with Compose: a mock is a second implementation of the imposition
 * rules, and second implementations drift.
 */
@Composable
fun SheetPreview(
    state: LayoutEditorViewModel.UiState,
    onShowSheet: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PrintDeckTheme.colors

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(),
            contentAlignment = Alignment.Center,
        ) {
            Crossfade(targetState = state.preview, label = "sheet") { bitmap ->
                if (bitmap == null) {
                    // Paper-shaped placeholder, so the layout does not jump when
                    // the first render lands.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(PAPER_WIDTH_FRACTION)
                            .aspectRatio(A4_PORTRAIT_ASPECT)
                            .clip(RoundedCornerShape(Radius.sm))
                            .background(colors.muted),
                    )
                } else {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Sheet ${state.previewIndex + 1} of ${state.sheetCount}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth(PAPER_WIDTH_FRACTION)
                            .aspectRatio(bitmap.width.toFloat() / bitmap.height)
                            .clip(RoundedCornerShape(Radius.sm))
                            // A hairline outline, because white paper on a white
                            // surface has no edge of its own.
                            .border(1.dp, colors.border, RoundedCornerShape(Radius.sm))
                            .background(colors.paper),
                    )
                }
            }

            if (state.rendering) {
                CircularProgressIndicator(
                    modifier = Modifier.size(SPINNER),
                    strokeWidth = 2.dp,
                    color = colors.primary,
                )
            }
        }

        if (state.sheetCount > 1) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                PrintDeckIconButton(
                    icon = PrintDeckIcons.CaretLeft,
                    contentDescription = "Previous sheet",
                    enabled = state.previewIndex > 0,
                    onClick = { onShowSheet(state.previewIndex - 1) },
                )
                Text(
                    text = "Sheet ${state.previewIndex + 1} of ${state.sheetCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.mutedForeground,
                )
                PrintDeckIconButton(
                    icon = PrintDeckIcons.CaretRight,
                    contentDescription = "Next sheet",
                    enabled = state.previewIndex < state.sheetCount - 1,
                    onClick = { onShowSheet(state.previewIndex + 1) },
                )
            }
        }
    }
}

/** Caps the preview so controls stay reachable without scrolling on a phone. */
@Composable
private fun Modifier.heightIn(): Modifier = this.height(PREVIEW_HEIGHT)

private val PREVIEW_HEIGHT = 320.dp
private val SPINNER = 24.dp
private const val PAPER_WIDTH_FRACTION = 0.62f
private const val A4_PORTRAIT_ASPECT = 0.707f
