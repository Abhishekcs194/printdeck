package io.github.abhishekcs194.printdeck.pdf.imposition

import io.github.abhishekcs194.printdeck.core.model.BindingEdge
import io.github.abhishekcs194.printdeck.core.model.Margins
import io.github.abhishekcs194.printdeck.core.model.PageOrder
import io.github.abhishekcs194.printdeck.core.model.RectPt
import io.github.abhishekcs194.printdeck.core.model.SizePt

/**
 * Carves a sheet into the rectangles a layout mode places pages into.
 *
 * Coordinates are PDF user space: origin bottom-left, Y upwards. Cells are
 * always returned in **visual reading order, row 0 at the top**, because that
 * is how a person describes a layout. Mapping a slot number onto a cell for a
 * given [PageOrder] is [slotToCell]'s job, which keeps the "where is it" and
 * "what goes there" questions separate.
 */
internal object SheetGeometry {

    /**
     * The printable area: the sheet less margins, less the binding gutter on
     * whichever edge is being bound.
     */
    fun contentArea(
        sheet: SizePt,
        margins: Margins,
        bindingGutterPt: Double = 0.0,
        bindingEdge: BindingEdge = BindingEdge.LEFT,
    ): RectPt {
        val gutterLeft = if (bindingEdge == BindingEdge.LEFT) bindingGutterPt else 0.0
        val gutterRight = if (bindingEdge == BindingEdge.RIGHT) bindingGutterPt else 0.0
        val gutterTop = if (bindingEdge == BindingEdge.TOP) bindingGutterPt else 0.0
        val gutterBottom = if (bindingEdge == BindingEdge.BOTTOM) bindingGutterPt else 0.0

        val x = margins.left + gutterLeft
        val y = margins.bottom + gutterBottom
        val width = sheet.width - x - margins.right - gutterRight
        val height = sheet.height - y - margins.top - gutterTop

        require(width > 0 && height > 0) {
            "Margins and binding gutter leave no printable area on a " +
                "${sheet.width}x${sheet.height}pt sheet"
        }
        return RectPt(x, y, width, height)
    }

    /**
     * Splits [content] into a [columns] x [rows] grid, separated by [gutterPt].
     *
     * Returned row-major with **row 0 at the top of the sheet**, so index
     * `row * columns + col` addresses a cell the way it is read.
     */
    fun cells(
        content: RectPt,
        columns: Int,
        rows: Int,
        gutterPt: Double = 0.0,
    ): List<RectPt> {
        require(columns >= 1 && rows >= 1) { "Grid must be at least 1x1" }

        val cellWidth = (content.width - (columns - 1) * gutterPt) / columns
        val cellHeight = (content.height - (rows - 1) * gutterPt) / rows
        require(cellWidth > 0 && cellHeight > 0) {
            "A ${columns}x$rows grid with ${gutterPt}pt gutters does not fit the printable area"
        }

        return buildList(columns * rows) {
            for (row in 0 until rows) {
                for (col in 0 until columns) {
                    add(
                        RectPt(
                            x = content.x + col * (cellWidth + gutterPt),
                            // Row 0 is the top row, so walk down from the top edge.
                            y = content.top - (row + 1) * cellHeight - row * gutterPt,
                            width = cellWidth,
                            height = cellHeight,
                        ),
                    )
                }
            }
        }
    }

    /**
     * Maps the n-th page on a sheet onto a cell index from [cells], honouring
     * the reading order.
     */
    fun slotToCell(ordinal: Int, columns: Int, rows: Int, order: PageOrder): Int {
        require(ordinal in 0 until columns * rows) {
            "Slot $ordinal is outside a ${columns}x$rows grid"
        }

        val row: Int
        var col: Int
        if (order.isColumnMajor) {
            col = ordinal / rows
            row = ordinal % rows
        } else {
            row = ordinal / columns
            col = ordinal % columns
        }

        // Right-to-left mirrors the columns; rows still read top to bottom.
        if (order.isRightToLeft) col = columns - 1 - col

        return row * columns + col
    }
}
