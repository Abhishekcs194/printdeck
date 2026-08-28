package io.github.abhishekcs194.printdeck.ui.editor

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Pinch-zoom state for the sheet preview.
 *
 * Held as plain snapshot state rather than an `Animatable`, for two reasons.
 * `awaitEachGesture` runs in a restricted suspend scope that cannot call
 * `Animatable.snapTo`. And plain state is what allows the values to be read
 * inside `graphicsLayer` — a deferred read that re-runs only the draw phase.
 * Reading them during composition instead, which is the obvious way to write
 * this, recomposes the whole pager on every touch event and is exactly what
 * makes a zoom feel like it is dragging behind the fingers.
 */
@Stable
class ZoomState {

    var scale by mutableFloatStateOf(1f)
        private set

    var offset by mutableStateOf(Offset.Zero)
        private set

    /** Size of the area the sheet is displayed in; needed to bound panning. */
    var viewportSize by mutableStateOf(IntSize.Zero)

    val isZoomed: Boolean get() = scale > MIN_ZOOMED_SCALE

    private val centre: Offset
        get() = Offset(viewportSize.width / 2f, viewportSize.height / 2f)

    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }

    /**
     * Applies one pinch step.
     *
     * The content under [centroid] is held still while the scale changes, so the
     * zoom feels attached to the paper. Anchoring at the centre of the view
     * instead — which is what the framework's `transformable` does here — pulls
     * the page out from under the fingers whenever the pinch is off-centre.
     */
    fun pinch(zoomChange: Float, panChange: Offset, centroid: Offset) {
        val next = (scale * zoomChange).coerceIn(1f, MAX_SCALE)
        val applied = next / scale
        val candidate = (centroid - centre) * (1f - applied) + offset * applied + panChange

        scale = next
        offset = if (next <= MIN_ZOOMED_SCALE) Offset.Zero else clamp(candidate, next)
    }

    fun pan(delta: Offset) {
        if (!isZoomed) return
        offset = clamp(offset + delta, scale)
    }

    /** Target for a double tap: zoom towards [tap], or back out if already zoomed. */
    fun doubleTapTarget(tap: Offset): Pair<Float, Offset> =
        if (isZoomed) {
            1f to Offset.Zero
        } else {
            DOUBLE_TAP_SCALE to clamp((centre - tap) * (DOUBLE_TAP_SCALE - 1f), DOUBLE_TAP_SCALE)
        }

    suspend fun animateTo(targetScale: Float, targetOffset: Offset) {
        val fromScale = scale
        val fromOffset = offset
        animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        ) { fraction, _ ->
            scale = fromScale + (targetScale - fromScale) * fraction
            offset = fromOffset + (targetOffset - fromOffset) * fraction
        }
    }

    /** Keeps the sheet from being dragged outside the frame. */
    private fun clamp(candidate: Offset, forScale: Float): Offset {
        val maxX = (forScale - 1f) * viewportSize.width / 2f
        val maxY = (forScale - 1f) * viewportSize.height / 2f
        return Offset(
            x = candidate.x.coerceIn(-maxX, maxX),
            y = candidate.y.coerceIn(-maxY, maxY),
        )
    }

    companion object {
        /** Enough to read 16-up body text; past this the render's own pixels show. */
        const val MAX_SCALE = 8f
        const val DOUBLE_TAP_SCALE = 3f

        /** At or below this counts as "not zoomed", allowing for float drift. */
        const val MIN_ZOOMED_SCALE = 1.01f
    }
}

/**
 * Pinch to zoom, drag to pan once zoomed, double tap to toggle.
 *
 * Pointer events are consumed only when a second finger is down, or when already
 * zoomed. Below that they are left alone, so a one-finger swipe still reaches the
 * pager underneath. Separating the two gestures by pointer count avoids them
 * competing for the same touch, which is what otherwise makes a swipe
 * occasionally get eaten by the zoom handler.
 */
fun Modifier.zoomable(state: ZoomState, scope: CoroutineScope): Modifier = this
    .pointerInput(state.viewportSize) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            do {
                val event = awaitPointerEvent()
                when {
                    event.changes.count { it.pressed } >= 2 -> {
                        state.pinch(
                            zoomChange = event.calculateZoom(),
                            panChange = event.calculatePan(),
                            centroid = event.calculateCentroid(useCurrent = false),
                        )
                        event.changes.forEach { if (it.pressed) it.consume() }
                    }

                    state.isZoomed -> {
                        val pan = event.calculatePan()
                        if (pan != Offset.Zero) {
                            state.pan(pan)
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            } while (event.changes.any { it.pressed })
        }
    }
    .pointerInput(state.viewportSize) {
        detectTapGestures(
            onDoubleTap = { tap ->
                // Animated rather than snapped: a jump gives the eye nothing to
                // follow between the two views.
                val (targetScale, targetOffset) = state.doubleTapTarget(tap)
                scope.launch { state.animateTo(targetScale, targetOffset) }
            },
        )
    }
