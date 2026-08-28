package io.github.abhishekcs194.printdesk.pdf.imposition

import io.github.abhishekcs194.printdesk.core.model.AffineTransform
import io.github.abhishekcs194.printdesk.core.model.RectPt
import io.github.abhishekcs194.printdesk.core.model.SizePt

/**
 * The complete, resolved description of what to print.
 *
 * A plan is pure data: it names source page indices and the transforms that
 * place them, and knows nothing about PDFs. :pdf:engine executes it against
 * PdfBox; the preview renders the same plan. Because both consume one
 * description, what the user sees really is what gets printed — the preview
 * cannot drift from the output.
 */
data class ImpositionPlan(
    val sheetSize: SizePt,
    val sheets: List<SheetPlan>,
) {
    val sheetCount: Int get() = sheets.size

    /** Distinct source pages actually used. Blank slots are excluded. */
    val referencedPages: Set<Int>
        get() = sheets.flatMap { sheet ->
            sheet.placements.mapNotNull { it.sourcePageIndex }
        }.toSet()
}

/** One physical side of paper. */
data class SheetPlan(
    val placements: List<Placement>,
    val marks: List<SheetMark> = emptyList(),
    /**
     * Which side of a duplexed sheet this is. Used to decide when a manual
     * duplex pass has to pause, and to keep front/back pairs together when a
     * printer is doing the flipping itself.
     */
    val side: SheetSide = SheetSide.SINGLE,
)

enum class SheetSide { SINGLE, FRONT, BACK }

/**
 * One source page placed on a sheet.
 *
 * [transform] maps the source page's own coordinate space onto the sheet.
 * [clip] is in **sheet** space and restricts drawing, which is what makes split
 * and poster tiling work: the same full page is placed several times, each time
 * clipped to a different window.
 */
data class Placement(
    /** Index into the source document. Null means a deliberately blank slot. */
    val sourcePageIndex: Int?,
    val transform: AffineTransform,
    val clip: RectPt? = null,
    /** Where to stroke a hairline cell border, if the user asked for one. */
    val border: RectPt? = null,
) {
    val isBlank: Boolean get() = sourcePageIndex == null

    companion object {
        fun blank(border: RectPt? = null) = Placement(
            sourcePageIndex = null,
            transform = AffineTransform.Identity,
            border = border,
        )
    }
}

/** Printed guides: trim marks, fold lines, assembly labels. */
sealed interface SheetMark {
    /** A hairline, used for crop and fold marks. */
    data class Line(
        val x1: Double,
        val y1: Double,
        val x2: Double,
        val y2: Double,
        val dashed: Boolean = false,
    ) : SheetMark

    /** Small text, e.g. "Row 2, column 3" on a poster tile. */
    data class Label(
        val text: String,
        val x: Double,
        val y: Double,
    ) : SheetMark
}
