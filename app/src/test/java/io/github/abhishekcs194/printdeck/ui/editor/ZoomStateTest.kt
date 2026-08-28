package io.github.abhishekcs194.printdeck.ui.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ZoomStateTest {

    private val viewport = IntSize(1000, 2000)
    private val centre = Offset(500f, 1000f)

    private fun zoomState() = ZoomState().apply { viewportSize = viewport }

    /** Where a content point currently sits on screen. */
    private fun screenPositionOf(state: ZoomState, contentPoint: Offset): Offset =
        centre + state.offset + contentPoint * state.scale

    /** Which content point currently sits under a screen position. */
    private fun contentPointUnder(state: ZoomState, screen: Offset): Offset =
        (screen - centre - state.offset) / state.scale

    @Test
    fun `starts unzoomed`() {
        val state = zoomState()
        assertThat(state.scale).isEqualTo(1f)
        assertThat(state.offset).isEqualTo(Offset.Zero)
        assertThat(state.isZoomed).isFalse()
    }

    @Test
    fun `pinching zooms about the centroid, not the middle of the view`() {
        // The whole point of tracking the centroid: whatever is under the fingers
        // must stay under the fingers. Anchoring at the centre instead pulls the
        // page out from under an off-centre pinch.
        val state = zoomState()
        val centroid = Offset(300f, 700f) // deliberately off centre
        val pinnedContent = contentPointUnder(state, centroid)

        state.pinch(zoomChange = 2f, panChange = Offset.Zero, centroid = centroid)

        val after = screenPositionOf(state, pinnedContent)
        assertThat(after.x).isWithin(TOLERANCE).of(centroid.x)
        assertThat(after.y).isWithin(TOLERANCE).of(centroid.y)
    }

    @Test
    fun `the anchored point stays put across several pinch steps`() {
        val state = zoomState()
        val centroid = Offset(720f, 400f)
        val pinnedContent = contentPointUnder(state, centroid)

        repeat(5) { state.pinch(1.2f, Offset.Zero, centroid) }

        val after = screenPositionOf(state, pinnedContent)
        assertThat(after.x).isWithin(TOLERANCE).of(centroid.x)
        assertThat(after.y).isWithin(TOLERANCE).of(centroid.y)
    }

    @Test
    fun `scale is bounded`() {
        val state = zoomState()
        repeat(30) { state.pinch(2f, Offset.Zero, centre) }
        assertThat(state.scale).isEqualTo(ZoomState.MAX_SCALE)

        repeat(30) { state.pinch(0.5f, Offset.Zero, centre) }
        assertThat(state.scale).isEqualTo(1f)
    }

    @Test
    fun `zooming back out recentres the sheet`() {
        // Otherwise the page settles off to one side at 1x with no way to nudge
        // it back, since panning is disabled when not zoomed.
        val state = zoomState()
        state.pinch(4f, Offset.Zero, Offset(100f, 100f))
        state.pan(Offset(200f, 200f))
        assertThat(state.offset).isNotEqualTo(Offset.Zero)

        state.pinch(0.05f, Offset.Zero, centre)
        assertThat(state.scale).isEqualTo(1f)
        assertThat(state.offset).isEqualTo(Offset.Zero)
    }

    @Test
    fun `panning cannot drag the sheet outside the frame`() {
        val state = zoomState()
        state.pinch(2f, Offset.Zero, centre)

        state.pan(Offset(10_000f, 10_000f))

        // At 2x the sheet may move by half the viewport in each direction.
        assertThat(state.offset.x).isWithin(TOLERANCE).of(viewport.width / 2f)
        assertThat(state.offset.y).isWithin(TOLERANCE).of(viewport.height / 2f)
    }

    @Test
    fun `panning does nothing while unzoomed, leaving the swipe to the pager`() {
        val state = zoomState()
        state.pan(Offset(250f, 250f))
        assertThat(state.offset).isEqualTo(Offset.Zero)
    }

    @Test
    fun `double tap zooms towards the tap, then back out`() {
        val state = zoomState()
        val tap = Offset(250f, 500f)

        val (zoomInScale, zoomInOffset) = state.doubleTapTarget(tap)
        assertThat(zoomInScale).isEqualTo(ZoomState.DOUBLE_TAP_SCALE)
        // Tapping above and left of centre moves the sheet down and right.
        assertThat(zoomInOffset.x).isGreaterThan(0f)
        assertThat(zoomInOffset.y).isGreaterThan(0f)

        state.pinch(ZoomState.DOUBLE_TAP_SCALE, Offset.Zero, tap)
        val (zoomOutScale, zoomOutOffset) = state.doubleTapTarget(tap)
        assertThat(zoomOutScale).isEqualTo(1f)
        assertThat(zoomOutOffset).isEqualTo(Offset.Zero)
    }

    @Test
    fun `reset clears everything`() {
        val state = zoomState()
        state.pinch(3f, Offset(50f, 50f), centre)
        state.reset()

        assertThat(state.scale).isEqualTo(1f)
        assertThat(state.offset).isEqualTo(Offset.Zero)
        assertThat(state.isZoomed).isFalse()
    }

    private companion object {
        const val TOLERANCE = 0.01f
    }
}
