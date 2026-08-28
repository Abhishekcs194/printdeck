package io.github.abhishekcs194.printdesk.pdf.imposition

import io.github.abhishekcs194.printdesk.core.model.ImpositionMode
import io.github.abhishekcs194.printdesk.core.model.ImpositionSettings
import io.github.abhishekcs194.printdesk.core.model.QuarterTurn
import io.github.abhishekcs194.printdesk.core.model.SizePt

/**
 * Entry point for layout. Give it the source page sizes and what the user asked
 * for; get back a plan describing every sheet.
 *
 * Pure and deterministic — no I/O, no Android, no PDF. That is what lets the
 * awkward parts (booklet ordering, creep, tile geometry) be tested exhaustively
 * in milliseconds instead of by printing paper.
 */
object Imposer {

    /**
     * @param sourcePageSizes every page in the document, in document order.
     *   Pages may differ in size; each is fitted individually.
     */
    fun plan(sourcePageSizes: List<SizePt>, settings: ImpositionSettings): ImpositionPlan {
        val sheetSize = chooseSheetSize(sourcePageSizes, settings)
        val pages = selectPages(sourcePageSizes, settings)

        return when (val mode = settings.mode) {
            is ImpositionMode.NUp -> NUpImposer.impose(pages, mode, settings, sheetSize)
            is ImpositionMode.Booklet -> BookletImposer.impose(pages, mode, settings, sheetSize)
            is ImpositionMode.Split -> SplitImposer.impose(pages, mode, settings, sheetSize)
            is ImpositionMode.Poster -> PosterImposer.impose(pages, mode, settings, sheetSize)
        }
    }

    /** Applies the page selection and reversal, producing the working page list. */
    private fun selectPages(
        sourcePageSizes: List<SizePt>,
        settings: ImpositionSettings,
    ): List<SourcePage> {
        val indices = PageRangeResolver.resolve(settings.pageSelection, sourcePageSizes.size)
        val ordered = if (settings.reverseOrder) indices.reversed() else indices
        return ordered.map { SourcePage(index = it, size = sourcePageSizes[it]) }
    }

    /**
     * Picks sheet orientation when the user has not forced one.
     *
     * Rather than guessing from the mode alone, this measures: it computes how
     * large a source page would end up in a single cell under each orientation
     * and takes the better one. That gets the genuinely useful default — two
     * portrait pages onto a landscape sheet — without a special case, and still
     * does the right thing for landscape source material.
     */
    private fun chooseSheetSize(
        sourcePageSizes: List<SizePt>,
        settings: ImpositionSettings,
    ): SizePt {
        val paper = settings.sheet.size

        settings.sheetLandscape?.let { return paper.oriented(landscape = it) }

        val reference = sourcePageSizes.firstOrNull()
            ?: return paper.oriented(landscape = settings.mode is ImpositionMode.Booklet)

        // What will actually be fitted into one cell, honouring any explicit
        // rotation. For split and poster that is a TILE of the source page, not
        // the whole page - splitting a landscape A3 into two gives portrait
        // halves, which want a portrait sheet even though the source is
        // landscape.
        val rotated = if (settings.rotation.swapsAxes) reference.swapped() else reference
        val effective = tileOf(rotated, settings.mode)

        val portraitScore = cellFillScore(paper, effective, settings)
        val landscapeScore = cellFillScore(paper.swapped(), effective, settings)

        return if (landscapeScore > portraitScore) paper.swapped() else paper
    }

    /**
     * The unit that lands on one sheet. N-up and booklet place a whole source
     * page per cell; split and poster place a fraction of one.
     */
    private fun tileOf(source: SizePt, mode: ImpositionMode): SizePt = when (mode) {
        is ImpositionMode.NUp, is ImpositionMode.Booklet -> source
        is ImpositionMode.Split -> SizePt(source.width / mode.columns, source.height / mode.rows)
        is ImpositionMode.Poster -> SizePt(source.width / mode.columns, source.height / mode.rows)
    }

    /** How much of a cell a source page fills on this sheet orientation. */
    private fun cellFillScore(
        sheet: SizePt,
        source: SizePt,
        settings: ImpositionSettings,
    ): Double {
        val (columns, rows) = gridFor(settings.mode)

        val content = runCatching {
            SheetGeometry.contentArea(
                sheet = sheet,
                margins = settings.margins,
                bindingGutterPt = settings.bindingGutterPt,
                bindingEdge = settings.bindingEdge,
            )
        }.getOrNull() ?: return Double.NEGATIVE_INFINITY

        val cellWidth = content.width / columns
        val cellHeight = content.height / rows
        if (cellWidth <= 0 || cellHeight <= 0) return Double.NEGATIVE_INFINITY

        // Allow the same quarter turn N-up would apply, so the comparison
        // reflects what the imposer will really do.
        val autoRotate = (settings.mode as? ImpositionMode.NUp)?.autoRotate ?: false
        val candidates = if (autoRotate) listOf(source, source.swapped()) else listOf(source)

        return candidates.maxOf { candidate ->
            minOf(cellWidth / candidate.width, cellHeight / candidate.height)
        }
    }

    /** The cell grid a mode divides one sheet into. */
    private fun gridFor(mode: ImpositionMode): Pair<Int, Int> = when (mode) {
        is ImpositionMode.NUp -> mode.columns to mode.rows
        // A booklet is 2-up across the fold.
        is ImpositionMode.Booklet -> 2 to 1
        // Split and poster both put one window on each sheet.
        is ImpositionMode.Split -> 1 to 1
        is ImpositionMode.Poster -> 1 to 1
    }

    /** Kept for callers that only need to know whether a rotation flips axes. */
    internal fun swapsAxes(turn: QuarterTurn): Boolean = turn.swapsAxes
}
