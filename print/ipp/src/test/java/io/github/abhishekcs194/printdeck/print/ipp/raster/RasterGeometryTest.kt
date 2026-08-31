package io.github.abhishekcs194.printdeck.print.ipp.raster

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A landscape two-up job printed shrunk into the top half of an upright sheet,
 * three separate times. The imposition was correct throughout — the imposed PDF
 * measures 841.89 x 595.276 — so the fault was entirely in presenting it to the
 * printer the right way round.
 *
 * The first fix used jipp-pdl's `RenderablePage.rotated()`. That is a *half*
 * turn, meant for the reverse side of a duplex sheet, and it leaves page
 * dimensions untouched; it was shipped in the belief that it rotated by a
 * quarter. These tests check the corners actually land where they should.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RasterGeometryTest {

    /** Maps a point in page coordinates through the transform. */
    private fun map(x: Float, y: Float, quarterTurn: Boolean, yOffset: Int = 0): Pair<Float, Float> {
        val point = floatArrayOf(x, y)
        RasterGeometry.transform(A4_SHORT, DPI, quarterTurn, yOffset).mapPoints(point)
        return point[0] to point[1]
    }

    @Test
    fun `a landscape page stays landscape when left alone`() {
        val (width, height) = RasterGeometry.pixelSize(A4_LONG, A4_SHORT, DPI, quarterTurn = false)
        assertThat(width).isGreaterThan(height)
    }

    @Test
    fun `a quarter turn makes a landscape page portrait`() {
        // The whole point: taller than wide, so the printer images it at full
        // size on portrait media rather than shrinking it to the short edge.
        val (width, height) = RasterGeometry.pixelSize(A4_LONG, A4_SHORT, DPI, quarterTurn = true)
        assertThat(height).isGreaterThan(width)
    }

    @Test
    fun `the turn swaps the dimensions rather than cropping`() {
        val straight = RasterGeometry.pixelSize(A4_LONG, A4_SHORT, DPI, quarterTurn = false)
        val turned = RasterGeometry.pixelSize(A4_LONG, A4_SHORT, DPI, quarterTurn = true)

        assertThat(turned.first).isEqualTo(straight.second)
        assertThat(turned.second).isEqualTo(straight.first)
    }

    @Test
    fun `the whole page lands inside the raster when turned`() {
        // Every corner must fall within the bounds. Getting the post-rotation
        // translation wrong sends half the page to negative coordinates, where
        // it is silently clipped away.
        val (width, height) = RasterGeometry.pixelSize(A4_LONG, A4_SHORT, DPI, quarterTurn = true)
        val corners = listOf(
            0f to 0f,
            A4_LONG to 0f,
            0f to A4_SHORT,
            A4_LONG to A4_SHORT,
        )

        corners.forEach { (x, y) ->
            val (mappedX, mappedY) = map(x, y, quarterTurn = true)
            assertThat(mappedX).isAtLeast(-TOLERANCE)
            assertThat(mappedX).isAtMost(width + TOLERANCE)
            assertThat(mappedY).isAtLeast(-TOLERANCE)
            assertThat(mappedY).isAtMost(height + TOLERANCE)
        }
    }

    @Test
    fun `the turned page fills the raster corner to corner`() {
        // Not merely inside the bounds - actually reaching them. A page that
        // lands in a corner at half size would satisfy a containment check and
        // still print exactly the fault being fixed.
        val (width, height) = RasterGeometry.pixelSize(A4_LONG, A4_SHORT, DPI, quarterTurn = true)
        val corners = listOf(0f to 0f, A4_LONG to 0f, 0f to A4_SHORT, A4_LONG to A4_SHORT)
            .map { (x, y) -> map(x, y, quarterTurn = true) }

        assertThat(corners.minOf { it.first }).isWithin(TOLERANCE).of(0f)
        assertThat(corners.maxOf { it.first }).isWithin(TOLERANCE).of(width.toFloat())
        assertThat(corners.minOf { it.second }).isWithin(TOLERANCE).of(0f)
        assertThat(corners.maxOf { it.second }).isWithin(TOLERANCE).of(height.toFloat())
    }

    @Test
    fun `an untuned page also fills its raster`() {
        val (width, height) = RasterGeometry.pixelSize(A4_LONG, A4_SHORT, DPI, quarterTurn = false)
        val corners = listOf(0f to 0f, A4_LONG to 0f, 0f to A4_SHORT, A4_LONG to A4_SHORT)
            .map { (x, y) -> map(x, y, quarterTurn = false) }

        assertThat(corners.maxOf { it.first }).isWithin(TOLERANCE).of(width.toFloat())
        assertThat(corners.maxOf { it.second }).isWithin(TOLERANCE).of(height.toFloat())
    }

    @Test
    fun `a band offset shifts the page up by exactly that many pixels`() {
        val (_, atTop) = map(0f, 0f, quarterTurn = true, yOffset = 0)
        val (_, atBand) = map(0f, 0f, quarterTurn = true, yOffset = BAND)

        assertThat(atTop - atBand).isWithin(TOLERANCE).of(BAND.toFloat())
    }

    private companion object {
        const val DPI = 300
        const val A4_LONG = 842f
        const val A4_SHORT = 595f
        const val BAND = 256
        const val TOLERANCE = 1.5f
    }
}
