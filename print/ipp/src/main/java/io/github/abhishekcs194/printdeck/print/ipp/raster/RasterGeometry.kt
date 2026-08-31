package io.github.abhishekcs194.printdeck.print.ipp.raster

import android.graphics.Matrix

/**
 * The arithmetic that turns a PDF page into a raster of the right shape.
 *
 * Separated from the rendering because this is where the mistake was. A landscape
 * two-up job printed shrunk into the top half of an upright sheet three times
 * over, and the first attempted fix used jipp-pdl's `RenderablePage.rotated()`,
 * which is a *half* turn for the reverse of a duplex sheet and leaves the page
 * dimensions untouched. It changed nothing, and was shipped believing otherwise.
 *
 * The platform's PdfRenderer cannot be exercised off a device, so the geometry
 * lives here where it can be checked without one.
 */
internal object RasterGeometry {

    private const val POINTS_PER_INCH = 72f
    private const val QUARTER_TURN_DEGREES = 90f

    /**
     * Pixel dimensions of the rasterised page.
     *
     * A quarter turn swaps them, which is the entire point: media keywords name
     * a portrait sheet and have no landscape variant, so a landscape imposition
     * has to arrive already turned or the printer shrinks it to fit the paper's
     * short edge.
     */
    fun pixelSize(
        pageWidthPoints: Float,
        pageHeightPoints: Float,
        dpi: Int,
        quarterTurn: Boolean,
    ): Pair<Int, Int> {
        val scale = dpi / POINTS_PER_INCH
        val width = if (quarterTurn) pageHeightPoints else pageWidthPoints
        val height = if (quarterTurn) pageWidthPoints else pageHeightPoints
        return (width * scale).toInt().coerceAtLeast(1) to (height * scale).toInt().coerceAtLeast(1)
    }

    /**
     * Maps the page's own coordinates onto one horizontal band of the raster.
     *
     * @param yOffset top of the band being rendered, in pixels. Pages are drawn
     *   a band at a time so a full-page bitmap is never allocated.
     */
    fun transform(
        pageHeightPoints: Float,
        dpi: Int,
        quarterTurn: Boolean,
        yOffset: Int,
    ): Matrix {
        val scale = dpi / POINTS_PER_INCH
        return Matrix().apply {
            setScale(scale, scale)
            if (quarterTurn) {
                // Rotating about the origin carries the page into negative x, so
                // it is brought back by its own scaled height - which is the
                // width of the turned result.
                postRotate(QUARTER_TURN_DEGREES)
                postTranslate(pageHeightPoints * scale, 0f)
            }
            postTranslate(0f, -yOffset.toFloat())
        }
    }
}
