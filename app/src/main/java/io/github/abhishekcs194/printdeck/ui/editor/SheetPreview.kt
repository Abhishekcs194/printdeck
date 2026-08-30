package io.github.abhishekcs194.printdeck.ui.editor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.abhishekcs194.printdeck.core.design.theme.PrintDeckTheme
import io.github.abhishekcs194.printdeck.core.model.ColorMode
import io.github.abhishekcs194.printdeck.core.design.theme.Radius
import io.github.abhishekcs194.printdeck.core.design.theme.Spacing
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Shows the sheets that will actually come out of the printer: swipeable, and
 * zoomable for checking small print.
 *
 * Each image is a render of the imposed PDF itself, so it cannot disagree with
 * the output. That matters most at high zoom, which is exactly when someone is
 * checking whether 9-up will still be readable.
 *
 * **Zoom state is deliberately never read during composition.** Scale and offset
 * are held in [Animatable]s and passed to `graphicsLayer` as lambdas, so a pinch
 * re-runs only the draw phase. Reading them in the composable body instead —
 * the obvious way to write this — recomposes the whole pager on every touch
 * event, and that is what makes a zoom feel like it is dragging.
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
    val scope = rememberCoroutineScope()
    val zoom = remember { ZoomState() }

    // Only flips when the threshold is crossed, so the pager is reconfigured
    // twice per zoom rather than on every frame.
    val zoomed by remember { derivedStateOf { zoom.isZoomed } }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.distinctUntilChanged().collect { page ->
            zoom.reset() // Zoom belongs to the sheet being looked at, not the pager.
            onShowSheet(page)
        }
    }

    LaunchedEffect(state.previewIndex) {
        if (state.previewIndex != pagerState.currentPage) {
            pagerState.animateScrollToPage(state.previewIndex)
        }
    }

    // A re-imposed sheet is a different picture; staying zoomed into where the
    // old one happened to be would leave the user looking at nothing.
    LaunchedEffect(state.settings) { zoom.reset() }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { zoom.viewportSize = it }
                .clipToBounds()
                .zoomable(zoom, scope),
            contentAlignment = Alignment.Center,
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                pageSpacing = Spacing.md,
                contentPadding = PaddingValues(horizontal = Spacing.xl),
                // A zoomed sheet must not slide away under a pan.
                userScrollEnabled = !zoomed,
            ) { page ->
                val isCurrent = page == pagerState.currentPage
                Sheet(
                    bitmap = state.previewOf(page),
                    monochrome = state.colorMode == ColorMode.MONOCHROME,
                    // Lambdas, not values: the read happens inside graphicsLayer
                    // during draw, so a pinch never triggers recomposition.
                    scale = { if (isCurrent) zoom.scale else 1f },
                    offset = { if (isCurrent) zoom.offset else Offset.Zero },
                )
            }

            if (state.rendering && state.previewOf(pagerState.currentPage) == null) {
                CircularProgressIndicator(
                    modifier = Modifier.size(SPINNER),
                    strokeWidth = 2.dp,
                    color = colors.primary,
                )
            }
        }

        if (state.sheetCount > 1) {
            Text(
                text = "Sheet ${pagerState.currentPage + 1} of ${state.sheetCount}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.mutedForeground,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }
    }
}

@Composable
private fun Sheet(
    bitmap: android.graphics.Bitmap?,
    monochrome: Boolean,
    scale: () -> Float,
    offset: () -> Offset,
) {
    val colors = PrintDeckTheme.colors
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                // Desaturated at draw time rather than re-rendered: the sheet on
                // disk is unchanged, and the printer is what actually converts.
                // Showing colour after the user picked black and white would be
                // the preview quietly disagreeing with the output.
                colorFilter = if (monochrome) {
                    ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
                } else {
                    null
                },
                modifier = Modifier
                    .graphicsLayer {
                        val current = scale()
                        scaleX = current
                        scaleY = current
                        translationX = offset().x
                        translationY = offset().y
                    }
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
