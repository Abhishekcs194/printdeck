package io.github.abhishekcs194.printdesk.pdf.imposition

import io.github.abhishekcs194.printdesk.core.model.AffineTransform
import io.github.abhishekcs194.printdesk.core.model.ImpositionMode
import io.github.abhishekcs194.printdesk.core.model.ImpositionSettings
import io.github.abhishekcs194.printdesk.core.model.RectPt
import io.github.abhishekcs194.printdesk.core.model.SizePt

/**
 * Blows one page up across a grid of sheets, to be trimmed and taped into a
 * poster.
 *
 * [ImpositionMode.Poster.overlapPt] is a real glue flap, not decoration: each
 * sheet repeats a strip of its neighbour so the seam can be lapped rather than
 * butted, which is the difference between a poster and a visible crack down the
 * middle. Assembly marks label every tile, because a 4x5 poster face-down on a
 * table is otherwise a jigsaw.
 */
internal object PosterImposer {

    fun impose(
        pages: List<SourcePage>,
        mode: ImpositionMode.Poster,
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

        // Usable poster area: sheets tile with each seam eating one overlap.
        val step = SizePt(
            width = content.width - mode.overlapPt,
            height = content.height - mode.overlapPt,
        )
        val canvas = SizePt(
            width = mode.columns * step.width + mode.overlapPt,
            height = mode.rows * step.height + mode.overlapPt,
        )

        val sheets = pages.flatMap { page ->
            val scale = minOf(canvas.width / page.size.width, canvas.height / page.size.height)
            val enlarged = SizePt(page.size.width * scale, page.size.height * scale)
            // Centre the enlargement on the canvas so any slack is shared out
            // rather than dumped on the last row.
            val originX = (canvas.width - enlarged.width) / 2
            val originY = (canvas.height - enlarged.height) / 2

            buildList {
                for (row in 0 until mode.rows) {
                    for (col in 0 until mode.columns) {
                        add(
                            tileSheet(
                                page = page,
                                col = col,
                                row = row,
                                mode = mode,
                                content = content,
                                step = step,
                                canvasHeight = canvas.height,
                                scale = scale,
                                originX = originX,
                                originY = originY,
                                nudge = nudge,
                            ),
                        )
                    }
                }
            }
        }

        return ImpositionPlan(sheetSize = sheetSize, sheets = sheets)
    }

    @Suppress("LongParameterList")
    private fun tileSheet(
        page: SourcePage,
        col: Int,
        row: Int,
        mode: ImpositionMode.Poster,
        content: RectPt,
        step: SizePt,
        canvasHeight: Double,
        scale: Double,
        originX: Double,
        originY: Double,
        nudge: AffineTransform,
    ): SheetPlan {
        // Window onto the canvas. Row 0 is the top row of the finished poster.
        val windowX = col * step.width
        val windowY = canvasHeight - row * step.height - content.height

        val transform = AffineTransform.scale(scale)
            .then(
                AffineTransform.translate(
                    originX - windowX + content.x,
                    originY - windowY + content.y,
                ),
            )
            .then(nudge)

        val clip = content.offsetBy(nudge)
        return SheetPlan(
            placements = listOf(
                Placement(
                    sourcePageIndex = page.index,
                    transform = transform,
                    clip = clip,
                ),
            ),
            marks = if (mode.assemblyMarks) assemblyMarks(clip, col, row, mode) else emptyList(),
        )
    }

    /** Trim guides along the overlap, plus a human-readable tile label. */
    private fun assemblyMarks(
        clip: RectPt,
        col: Int,
        row: Int,
        mode: ImpositionMode.Poster,
    ): List<SheetMark> = buildList {
        // Dashed cut line on each edge that has a neighbour to lap onto.
        if (col > 0) {
            add(SheetMark.Line(clip.x + mode.overlapPt, clip.y, clip.x + mode.overlapPt, clip.top, dashed = true))
        }
        if (row > 0) {
            add(SheetMark.Line(clip.x, clip.top - mode.overlapPt, clip.right, clip.top - mode.overlapPt, dashed = true))
        }
        add(
            SheetMark.Label(
                text = "Row ${row + 1} of ${mode.rows}, column ${col + 1} of ${mode.columns}",
                x = clip.x + LABEL_INSET_PT,
                y = clip.y + LABEL_INSET_PT,
            ),
        )
    }

    private const val LABEL_INSET_PT = 6.0
}
