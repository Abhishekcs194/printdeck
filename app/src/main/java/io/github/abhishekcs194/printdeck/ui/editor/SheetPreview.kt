package io.github.abhishekcs194.printdeck.ui.editor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.abhishekcs194.printdeck.core.design.PrintDeckIcons
import io.github.abhishekcs194.printdeck.core.design.component.Pill
import io.github.abhishekcs194.printdeck.core.design.theme.PrintDeckTheme
import io.github.abhishekcs194.printdeck.core.design.theme.Radius
import io.github.abhishekcs194.printdeck.core.design.theme.Spacing
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs

/**
 * Shows the sheets that will actually come out of the printer: swipeable, and
 * zoomable for checking small print.
 *
 * Each image is a render of the imposed PDF itself, not a re-drawing of the
 * settings, so it cannot disagree with the output. That matters most at high
 * zoom, which is exactly when someone is checking whether 9-up will still be
 * readable — a mock layout would be answering a different question.
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

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    val zoomed = scale > MIN_ZOOMED_SCALE

    /** Keeps the sheet from being dragged off screen. */
    fun clamp(candidate: Offset, forScale: Float): Offset {
        val maxX = (forScale - 1f) * viewportSize.width / 2f
        val maxY = (forScale - 1f) * viewportSize.height / 2f
        return Offset(
            x = candidate.x.coerceIn(-maxX, maxX),
            y = candidate.y.coerceIn(-maxY, maxY),
        )
    }

    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.distinctUntilChanged().collect { page ->
            // Zoom belongs to the sheet being looked at, not to the pager.
            reset()
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
    LaunchedEffect(state.settings) { reset() }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { viewportSize = it }
                .clipToBounds()
                .pinchToZoom(
                    scale = { scale },
                    offset = { offset },
                    viewportSize = viewportSize,
                    onTransform = { nextScale, nextOffset ->
                        scale = nextScale
                        offset = if (nextScale <= MIN_ZOOMED_SCALE) Offset.Zero else clamp(nextOffset, nextScale)
                    },
                )
                // One-finger drag pans, but only once zoomed in. Below that the
                // gesture is left alone so the pager still turns pages.
                .pointerInput(zoomed) {
                    if (!zoomed) return@pointerInput
                    detectDragGestures { change, drag ->
                        offset = clamp(offset + drag, scale)
                        change.consume()
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { tap ->
                            if (scale > MIN_ZOOMED_SCALE) {
                                reset()
                            } else {
                                scale = DOUBLE_TAP_SCALE
                                // Zoom towards the tap rather than the centre, so
                                // the thing being inspected stays under the finger.
                                val centre = Offset(viewportSize.width / 2f, viewportSize.height / 2f)
                                offset = clamp((centre - tap) * (DOUBLE_TAP_SCALE - 1f), DOUBLE_TAP_SCALE)
                            }
                        },
                    )
                },
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
                Sheet(
                    bitmap = state.previewOf(page),
                    // Only the sheet in view carries the zoom; neighbours stay
                    // unscaled so they are correct the moment they arrive.
                    scale = if (page == pagerState.currentPage) scale else 1f,
                    offset = if (page == pagerState.currentPage) offset else Offset.Zero,
                )
            }

            if (state.rendering && state.previewOf(pagerState.currentPage) == null) {
                CircularProgressIndicator(
                    modifier = Modifier.size(SPINNER),
                    strokeWidth = 2.dp,
                    color = colors.primary,
                )
            }

            if (zoomed) {
                Pill(
                    text = "${(scale * PERCENT).toInt()}%",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(Spacing.md),
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

/**
 * Pinch-to-zoom that leaves one-finger gestures alone.
 *
 * Written by hand rather than with `transformable` for two reasons. It only
 * consumes events once a second finger is down, so a one-finger swipe still
 * reaches the pager and the two gestures never fight over the same touch. And it
 * yields the centroid, so the page zooms about the point between the fingers
 * instead of the middle of the screen — without that a pinch on a corner scales
 * the page out from under you, which is the difference between a zoom that feels
 * attached to the paper and one that does not.
 */
private fun Modifier.pinchToZoom(
    scale: () -> Float,
    offset: () -> Offset,
    viewportSize: IntSize,
    onTransform: (scale: Float, offset: Offset) -> Unit,
): Modifier = this.pointerInput(viewportSize) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)

        // Read the live values at the start of each gesture rather than closing
        // over them. A modifier captures whatever was current when it was
        // composed, so a second pinch would otherwise begin from the first one's
        // starting point and visibly jump.
        var current = scale()
        var currentOffset = offset()

        do {
            val event = awaitPointerEvent()
            if (event.changes.count { it.pressed } >= 2) {
                val zoomChange = event.calculateZoom()
                val panChange = event.calculatePan()
                val centroid = event.calculateCentroid(useCurrent = false)

                val next = (current * zoomChange).coerceIn(1f, MAX_SCALE)
                val applied = next / current
                val viewCentre = Offset(viewportSize.width / 2f, viewportSize.height / 2f)

                // Hold the content under the centroid still while the scale changes.
                currentOffset = (centroid - viewCentre) * (1f - applied) +
                    currentOffset * applied + panChange
                current = next

                onTransform(current, currentOffset)
                event.changes.forEach { if (it.pressed) it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}

@Composable
private fun Sheet(bitmap: android.graphics.Bitmap?, scale: Float, offset: Offset) {
    val colors = PrintDeckTheme.colors
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val zoom = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
            translationX = offset.x
            translationY = offset.y
        }

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
                modifier = zoom
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

/** Enough to read 16-up body text; beyond this the render's own pixels show. */
private const val MAX_SCALE = 8f
private const val DOUBLE_TAP_SCALE = 3f

/** Anything at or below this counts as "not zoomed", allowing for float drift. */
private const val MIN_ZOOMED_SCALE = 1.01f
private const val PERCENT = 100
