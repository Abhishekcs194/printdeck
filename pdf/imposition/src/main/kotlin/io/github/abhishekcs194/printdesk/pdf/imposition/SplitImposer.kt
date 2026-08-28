package io.github.abhishekcs194.printdesk.pdf.imposition

import io.github.abhishekcs194.printdesk.core.model.AffineTransform
import io.github.abhishekcs194.printdesk.core.model.ImpositionMode
import io.github.abhishekcs194.printdesk.core.model.ImpositionSettings
import io.github.abhishekcs194.printdesk.core.model.RectPt
import io.github.abhishekcs194.printdesk.core.model.SizePt

/**
 * The inverse of N-up: cut each source page into a grid of output pages.
 *
 * This is what turns an A3 spread, or a phone photo that caught both facing
 * pages of a book, into separate sheets you can actually read. Each output sheet
 * places the *whole* source page and then clips to the window of interest, so
 * nothing is resampled and text stays vector.
 *
 * [ImpositionMode.Split.overlapPt] is the width of the band shared by two
 * neighbouring tiles: each tile reaches half that distance past the nominal cut
 * line, so a slightly crooked guillotine does not lose a line of text.
 */
internal object SplitImposer {

    fun impose(
        pages: List<SourcePage>,
        mode: ImpositionMode.Split,
        settings: ImpositionSettings,
        sheetSize: SizePt,
    ): ImpositionPlan {
        if (pages.isEmpty()) return ImpositionPlan(sheetSize, emptyList())

        val content = SheetGeometry.contentArea(
            sheet = sheetSize,
            margins = settings.margins,
            bindingGutterPt = settings.bindingGutterPt,
            bindingEdge = settings.bindingEdge,
        )
        val nudge = AffineTransform.translate(settings.offsetXPt, settings.offsetYPt)

        val sheets = pages.flatMap { page ->
            (0 until mode.perPage).map { ordinal ->
                val cellIndex = SheetGeometry.slotToCell(ordinal, mode.columns, mode.rows, mode.order)
                val col = cellIndex % mode.columns
                val row = cellIndex / mode.columns

                val region = regionFor(page.size, mode, col, row)
                SheetPlan(placements = listOf(placeRegion(page, region, content, nudge)))
            }
        }
        return ImpositionPlan(sheetSize = sheetSize, sheets = sheets)
    }

    /**
     * The window of the source page shown by tile ([col], [row]), in source
     * coordinates. Row 0 is the **top** of the page, which is how a person
     * counts, but PDF's origin is bottom-left — hence the flip on Y.
     */
    private fun regionFor(
        source: SizePt,
        mode: ImpositionMode.Split,
        col: Int,
        row: Int,
    ): RectPt {
        val tileWidth = source.width / mode.columns
        val tileHeight = source.height / mode.rows
        val bleed = mode.overlapPt / 2

        // Only reach past internal cut lines; the outer edges of the page have
        // nothing beyond them to borrow.
        val left = (col * tileWidth - if (col > 0) bleed else 0.0).coerceAtLeast(0.0)
        val right = ((col + 1) * tileWidth + if (col < mode.columns - 1) bleed else 0.0)
            .coerceAtMost(source.width)
        val top = source.height - (row * tileHeight - if (row > 0) bleed else 0.0)
        val bottom = source.height - ((row + 1) * tileHeight + if (row < mode.rows - 1) bleed else 0.0)

        val clampedTop = top.coerceAtMost(source.height)
        val clampedBottom = bottom.coerceAtLeast(0.0)

        return RectPt(
            x = left,
            y = clampedBottom,
            width = right - left,
            height = clampedTop - clampedBottom,
        )
    }

    /**
     * Places the whole source page such that [region] lands centred in
     * [content], then clips to exactly where it landed.
     */
    private fun placeRegion(
        page: SourcePage,
        region: RectPt,
        content: RectPt,
        nudge: AffineTransform,
    ): Placement {
        val scale = minOf(content.width / region.width, content.height / region.height)
        val placedWidth = region.width * scale
        val placedHeight = region.height * scale
        val x = content.x + (content.width - placedWidth) / 2
        val y = content.y + (content.height - placedHeight) / 2

        // Move the region's own origin to (0,0), scale, then drop it in place.
        val transform = AffineTransform.translate(-region.x, -region.y)
            .then(AffineTransform.scale(scale))
            .then(AffineTransform.translate(x, y))
            .then(nudge)

        val window = RectPt(x, y, placedWidth, placedHeight).offsetBy(nudge)
        return Placement(
            sourcePageIndex = page.index,
            transform = transform,
            clip = window,
        )
    }
}
