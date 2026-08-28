package io.github.abhishekcs194.printdeck.pdf.imposition

import io.github.abhishekcs194.printdeck.core.model.AffineTransform
import io.github.abhishekcs194.printdeck.core.model.ImpositionMode
import io.github.abhishekcs194.printdeck.core.model.ImpositionSettings
import io.github.abhishekcs194.printdeck.core.model.RectPt
import io.github.abhishekcs194.printdeck.core.model.SizePt

/**
 * N pages per sheet.
 *
 * The everyday mode: 2-up handouts, 4-up drafts, 9-up contact sheets. Pages fill
 * the grid in the chosen reading order, and a short final sheet simply leaves
 * its remaining cells empty rather than wrapping the document round.
 */
internal object NUpImposer {

    fun impose(
        pages: List<SourcePage>,
        mode: ImpositionMode.NUp,
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
        val cells = SheetGeometry.cells(content, mode.columns, mode.rows, mode.gutterPt)
        val nudge = AffineTransform.translate(settings.offsetXPt, settings.offsetYPt)

        val sheets = pages.chunked(mode.perSheet).map { pagesOnSheet ->
            val placements = buildList {
                for (ordinal in 0 until mode.perSheet) {
                    val cell = cells[SheetGeometry.slotToCell(ordinal, mode.columns, mode.rows, mode.order)]
                    val page = pagesOnSheet.getOrNull(ordinal)

                    if (page == null) {
                        // A short final sheet. Only worth emitting a placement at
                        // all if borders are being drawn, so the grid still reads
                        // as a grid.
                        if (mode.drawCellBorders) add(Placement.blank(border = cell.offsetBy(nudge)))
                        continue
                    }

                    val fit = PageFitter.fit(
                        source = page.size,
                        cell = cell,
                        rotation = settings.rotation,
                        scaling = settings.scaling,
                        autoRotate = mode.autoRotate,
                    )
                    add(
                        Placement(
                            sourcePageIndex = page.index,
                            transform = fit.transform.then(nudge),
                            // Clip to the cell so an ActualSize page larger than
                            // its cell cannot bleed over its neighbour.
                            clip = cell.offsetBy(nudge),
                            border = if (mode.drawCellBorders) cell.offsetBy(nudge) else null,
                        ),
                    )
                }
            }
            SheetPlan(placements = placements)
        }

        return ImpositionPlan(sheetSize = sheetSize, sheets = sheets)
    }
}

/** Shifts a rectangle by a pure translation. */
internal fun RectPt.offsetBy(translation: AffineTransform): RectPt =
    RectPt(x + translation.e, y + translation.f, width, height)
