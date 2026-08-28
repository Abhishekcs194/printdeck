package io.github.abhishekcs194.printdeck.ui.editor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import io.github.abhishekcs194.printdeck.core.design.theme.PrintDeckTheme
import io.github.abhishekcs194.printdeck.core.design.theme.Radius
import io.github.abhishekcs194.printdeck.core.design.theme.Spacing
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Shows the sheets that will actually come out of the printer, swipeable.
 *
 * Each image is a render of the imposed PDF itself, not a re-drawing of the
 * settings, so it cannot disagree with the output. That is the whole reason to
 * build the preview this way rather than painting a mock layout in Compose: a
 * mock is a second implementation of the imposition rules, and second
 * implementations drift.
 */
@Composable
fun SheetPreview(
    state: LayoutEditorViewModel.UiState,
    onShowSheet: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PrintDeckTheme.colors
    val sheetCount = state.sheetCount.coerceAtLeast(1)
    val pagerState = rememberPagerState(initialPage = state.previewIndex) { sheetCount }

    // Swipes drive the view model.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect(onShowSheet)
    }

    // ...and the arrows drive the pager, so the two never disagree.
    LaunchedEffect(state.previewIndex) {
        if (state.previewIndex != pagerState.currentPage) {
            pagerState.animateScrollToPage(state.previewIndex)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                pageSpacing = Spacing.md,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = Spacing.xl,
                ),
            ) { page ->
                Sheet(bitmap = state.previewOf(page))
            }

            // Only shown while nothing is drawn yet. A spinner over a sheet that
            // is already visible just makes a settled screen look busy.
            if (state.rendering && state.previewOf(pagerState.currentPage) == null) {
                CircularProgressIndicator(
                    modifier = Modifier.size(SPINNER),
                    strokeWidth = 2.dp,
                    color = colors.primary,
                )
            }
        }

        if (state.sheetCount > 1) {
            Row(
                modifier = Modifier.padding(top = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    text = "Sheet ${pagerState.currentPage + 1} of ${state.sheetCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.mutedForeground,
                )
            }
        }
    }
}

@Composable
private fun Sheet(bitmap: android.graphics.Bitmap?) {
    val colors = PrintDeckTheme.colors
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap == null) {
            // Paper-shaped placeholder, so the layout does not jump when the
            // render lands mid-swipe.
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(A4_PORTRAIT_ASPECT)
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(colors.muted),
            )
        } else {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Imposed sheet",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(bitmap.width.toFloat() / bitmap.height)
                    .clip(RoundedCornerShape(Radius.sm))
                    // A hairline outline, because white paper on a white surface
                    // has no edge of its own.
                    .border(1.dp, colors.border, RoundedCornerShape(Radius.sm))
                    .background(colors.paper),
            )
        }
    }
}

private val SPINNER = 24.dp
private const val A4_PORTRAIT_ASPECT = 0.707f
