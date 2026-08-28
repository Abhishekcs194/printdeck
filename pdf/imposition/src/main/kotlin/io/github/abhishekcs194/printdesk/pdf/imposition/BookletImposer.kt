package io.github.abhishekcs194.printdesk.pdf.imposition

import io.github.abhishekcs194.printdesk.core.model.AffineTransform
import io.github.abhishekcs194.printdesk.core.model.ImpositionMode
import io.github.abhishekcs194.printdesk.core.model.ImpositionSettings
import io.github.abhishekcs194.printdesk.core.model.RectPt
import io.github.abhishekcs194.printdesk.core.model.SizePt

/**
 * Saddle-stitch booklet imposition: print two-up double-sided, fold the stack in
 * half, staple the spine.
 *
 * The page order is the whole trick. On the outermost sheet the last page sits
 * beside the first, because once folded they end up as the back and front
 * covers. Working inwards, sheet `i` of a signature of `n` pages carries:
 *
 * ```
 *   front:  [ n-1-2i , 2i     ]      (left, right)
 *   back:   [ 2i+1   , n-2-2i ]
 * ```
 *
 * A 4-page signature is therefore `[3,0]` / `[1,2]` — fold it and you read
 * 0,1,2,3. This is verified for every page count in the unit tests rather than
 * trusted, because it is very easy to get subtly wrong and the failure only
 * shows up on folded paper.
 */
internal object BookletImposer {

    private const val PAGES_PER_SHEET = 4

    fun impose(
        pages: List<SourcePage>,
        mode: ImpositionMode.Booklet,
        settings: ImpositionSettings,
        sheetSize: SizePt,
    ): ImpositionPlan {
        if (pages.isEmpty()) return ImpositionPlan(sheetSize, emptyList())

        // A folded sheet always carries four pages, so the document is padded
        // with blanks up to a multiple of four. Those blanks are real output
        // pages - a booklet with a missing back cover is not a booklet.
        val padded: List<SourcePage?> = pages + List(paddingFor(pages.size)) { null }

        val signatures = padded.chunked(
            mode.sheetsPerSignature?.let { it * PAGES_PER_SHEET } ?: padded.size,
        )

        val sheets = signatures.flatMap { signature ->
            imposeSignature(signature, mode, settings, sheetSize)
        }
        return ImpositionPlan(sheetSize = sheetSize, sheets = sheets)
    }

    /** Blanks needed to round a page count up to a whole number of folded sheets. */
    private fun paddingFor(pageCount: Int): Int =
        (PAGES_PER_SHEET - pageCount % PAGES_PER_SHEET) % PAGES_PER_SHEET

    private fun imposeSignature(
        signature: List<SourcePage?>,
        mode: ImpositionMode.Booklet,
        settings: ImpositionSettings,
        sheetSize: SizePt,
    ): List<SheetPlan> {
        val n = signature.size
        val sheetCount = n / PAGES_PER_SHEET

        val content = SheetGeometry.contentArea(sheet = sheetSize, margins = settings.margins)
        val halves = SheetGeometry.cells(content, columns = 2, rows = 1)
        // The binding gutter is taken off each page's spine edge, so text does
        // not disappear into the fold.
        val leftCell = halves[0].insetRight(settings.bindingGutterPt)
        val rightCell = halves[1].insetLeft(settings.bindingGutterPt)

        return buildList {
            for (sheet in 0 until sheetCount) {
                val creep = creepFor(sheet, sheetCount, mode.creepPt)

                val frontOuter = signature[n - 1 - 2 * sheet]
                val frontInner = signature[2 * sheet]
                val backInner = signature[2 * sheet + 1]
                val backOuter = signature[n - 2 - 2 * sheet]

                // Right-to-left binding mirrors which half of the sheet each
                // page lands on; the sequence itself is unchanged.
                val (frontLeft, frontRight) =
                    if (mode.rightToLeft) frontInner to frontOuter else frontOuter to frontInner
                val (backLeft, backRight) =
                    if (mode.rightToLeft) backOuter to backInner else backInner to backOuter

                add(
                    SheetPlan(
                        placements = listOfNotNull(
                            place(frontLeft, leftCell, settings, creep, spineOnRight = true),
                            place(frontRight, rightCell, settings, creep, spineOnRight = false),
                        ),
                        side = SheetSide.FRONT,
                    ),
                )
                add(
                    SheetPlan(
                        placements = listOfNotNull(
                            place(backLeft, leftCell, settings, creep, spineOnRight = true),
                            place(backRight, rightCell, settings, creep, spineOnRight = false),
                        ),
                        side = SheetSide.BACK,
                    ),
                )
            }
        }
    }

    /**
     * Creep, a.k.a. shingling.
     *
     * Nested folded sheets do not fold flat: the inner sheets have to travel
     * around the thickness of the outer ones, so their fore-edges protrude. The
     * binder then trims the whole block flush, which cuts more off the inner
     * pages than the outer ones. Shifting inner pages **towards the spine**
     * cancels that out, so margins look even after trimming.
     *
     * Sheet 0 is the outermost and needs no correction; the innermost needs the
     * full [totalCreep].
     */
    private fun creepFor(sheet: Int, sheetCount: Int, totalCreep: Double): Double {
        if (totalCreep <= 0.0 || sheetCount <= 1) return 0.0
        return totalCreep * sheet / (sheetCount - 1)
    }

    private fun place(
        page: SourcePage?,
        cell: RectPt,
        settings: ImpositionSettings,
        creep: Double,
        spineOnRight: Boolean,
    ): Placement? {
        if (page == null) return null

        // Towards the spine: a left-hand page moves right, a right-hand page
        // moves left.
        val creepShift = if (spineOnRight) creep else -creep
        val nudge = AffineTransform.translate(
            settings.offsetXPt + creepShift,
            settings.offsetYPt,
        )

        val fit = PageFitter.fit(
            source = page.size,
            cell = cell,
            rotation = settings.rotation,
            scaling = settings.scaling,
            autoRotate = false,
        )
        return Placement(
            sourcePageIndex = page.index,
            transform = fit.transform.then(nudge),
            clip = cell.offsetBy(nudge),
        )
    }
}

private fun RectPt.insetLeft(amount: Double): RectPt =
    if (amount <= 0) this else RectPt(x + amount, y, width - amount, height)

private fun RectPt.insetRight(amount: Double): RectPt =
    if (amount <= 0) this else RectPt(x, y, width - amount, height)
