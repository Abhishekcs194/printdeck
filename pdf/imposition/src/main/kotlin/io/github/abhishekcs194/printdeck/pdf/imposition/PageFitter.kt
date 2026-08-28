package io.github.abhishekcs194.printdeck.pdf.imposition

import io.github.abhishekcs194.printdeck.core.model.AffineTransform
import io.github.abhishekcs194.printdeck.core.model.QuarterTurn
import io.github.abhishekcs194.printdeck.core.model.RectPt
import io.github.abhishekcs194.printdeck.core.model.Scaling
import io.github.abhishekcs194.printdeck.core.model.SizePt

/**
 * Places one source page inside one rectangle.
 *
 * Every layout mode funnels through here, so aspect handling, rotation and
 * scaling behave identically whether the rectangle is an N-up cell, half a
 * booklet sheet, or a poster tile.
 */
internal object PageFitter {

    data class Fit(
        val transform: AffineTransform,
        /** Where the page actually landed, in sheet space. Used for borders and marks. */
        val bounds: RectPt,
        val appliedRotation: QuarterTurn,
        val scale: Double,
    )

    /**
     * @param autoRotate when true, a quarter turn is added if it lets the page
     *   fill [cell] more fully. This is what turns the classic wasteful 2-up -
     *   two portrait pages stranded side by side on a portrait sheet - into a
     *   properly filled landscape pair.
     */
    fun fit(
        source: SizePt,
        cell: RectPt,
        rotation: QuarterTurn = QuarterTurn.NONE,
        scaling: Scaling = Scaling.FitToPage,
        autoRotate: Boolean = false,
    ): Fit {
        val chosen = if (autoRotate) betterRotation(source, cell, rotation) else rotation
        val rotated = if (chosen.swapsAxes) source.swapped() else source

        val scale = scaleFor(rotated, cell.size, scaling)
        val placedWidth = rotated.width * scale
        val placedHeight = rotated.height * scale

        // Centre within the cell. Content larger than the cell (ActualSize, or a
        // Fixed factor above 1) overhangs symmetrically and is clipped by the
        // caller rather than being silently shrunk.
        val x = cell.x + (cell.width - placedWidth) / 2
        val y = cell.y + (cell.height - placedHeight) / 2

        val transform = anchoredRotation(source, chosen)
            .then(AffineTransform.scale(scale))
            .then(AffineTransform.translate(x, y))

        return Fit(
            transform = transform,
            bounds = RectPt(x, y, placedWidth, placedHeight),
            appliedRotation = chosen,
            scale = scale,
        )
    }

    /** Picks between [base] and [base] plus a quarter turn, whichever fills more of [cell]. */
    private fun betterRotation(source: SizePt, cell: RectPt, base: QuarterTurn): QuarterTurn {
        val alternative = base.plusQuarterTurn()
        val baseSize = if (base.swapsAxes) source.swapped() else source
        val altSize = if (alternative.swapsAxes) source.swapped() else source

        val baseScale = fitScale(baseSize, cell.size)
        val altScale = fitScale(altSize, cell.size)

        // Only turn the page when it is a real improvement. A hair's difference
        // is not worth printing a page sideways.
        return if (altScale > baseScale * ROTATION_GAIN_THRESHOLD) alternative else base
    }

    private fun scaleFor(source: SizePt, cell: SizePt, scaling: Scaling): Double = when (scaling) {
        Scaling.FitToPage -> fitScale(source, cell)
        Scaling.ShrinkOversized -> minOf(1.0, fitScale(source, cell))
        Scaling.ActualSize -> 1.0
        is Scaling.Fixed -> scaling.factor
    }

    private fun fitScale(source: SizePt, cell: SizePt): Double =
        minOf(cell.width / source.width, cell.height / source.height)

    /**
     * Rotation about the origin, followed by the translation that brings the
     * page back into the positive quadrant.
     *
     * PDF rotates about (0,0), which throws a page into negative coordinates,
     * so each turn needs a matching shift. Doing it here means no caller has to
     * remember which axis moves.
     */
    private fun anchoredRotation(source: SizePt, turn: QuarterTurn): AffineTransform {
        val rotate = AffineTransform.rotate(turn)
        val shift = when (turn) {
            QuarterTurn.NONE -> AffineTransform.Identity
            // (x, y) -> (y, -x): the page falls below the axis by its width.
            QuarterTurn.CW_90 -> AffineTransform.translate(0.0, source.width)
            // (x, y) -> (-x, -y): the page falls into the third quadrant.
            QuarterTurn.HALF -> AffineTransform.translate(source.width, source.height)
            // (x, y) -> (-y, x): the page falls left of the axis by its height.
            QuarterTurn.CCW_90 -> AffineTransform.translate(source.height, 0.0)
        }
        return rotate.then(shift)
    }

    private fun QuarterTurn.plusQuarterTurn(): QuarterTurn = when (this) {
        QuarterTurn.NONE -> QuarterTurn.CW_90
        QuarterTurn.CW_90 -> QuarterTurn.HALF
        QuarterTurn.HALF -> QuarterTurn.CCW_90
        QuarterTurn.CCW_90 -> QuarterTurn.NONE
    }

    /** A rotation must gain at least this much scale to be worth applying. */
    private const val ROTATION_GAIN_THRESHOLD = 1.001
}
