package io.github.abhishekcs194.printdeck.core.model

/**
 * What the user asked for. Consumed by :pdf:imposition, which turns it into a
 * concrete plan; nothing in here knows about PDFs.
 */
data class ImpositionSettings(
    val mode: ImpositionMode = ImpositionMode.NUp(),
    val sheet: PaperSize = PaperSize.Default,
    /** Forces sheet orientation. Null lets the mode choose whichever wastes less paper. */
    val sheetLandscape: Boolean? = null,
    val margins: Margins = Margins.None,
    /** Extra margin on the binding edge, added on top of [margins]. */
    val bindingGutterPt: Double = 0.0,
    val bindingEdge: BindingEdge = BindingEdge.LEFT,
    val scaling: Scaling = Scaling.FitToPage,
    val rotation: QuarterTurn = QuarterTurn.NONE,
    /** Whole-sheet nudge, for trays that feed slightly off-register. */
    val offsetXPt: Double = 0.0,
    val offsetYPt: Double = 0.0,
    val pageSelection: PageSelection = PageSelection.All,
    val reverseOrder: Boolean = false,
)

sealed interface ImpositionMode {

    /**
     * N pages per sheet. The everyday case: handouts, lecture notes, drafts.
     */
    data class NUp(
        val columns: Int = 2,
        val rows: Int = 1,
        val order: PageOrder = PageOrder.ACROSS_THEN_DOWN,
        val gutterPt: Double = 0.0,
        val drawCellBorders: Boolean = false,
        /**
         * Rotates each page 90° when that fills its cell better. Turns the
         * classic wasteful 2-up (two portrait pages stranded on a portrait
         * sheet) into a full-bleed landscape pair.
         */
        val autoRotate: Boolean = true,
    ) : ImpositionMode {
        init {
            require(columns >= 1 && rows >= 1) { "N-up grid must be at least 1x1" }
        }

        val perSheet: Int get() = columns * rows
    }

    /**
     * Saddle-stitch booklet: fold a stack in half and staple the spine.
     *
     * Page count is padded to a multiple of four, because one folded sheet
     * always carries four pages.
     */
    data class Booklet(
        /**
         * Sheets per folded signature. A 40-page booklet folded as one signature
         * has a badly protruding fore-edge; splitting it into signatures of 4 or
         * 8 sheets and stitching those together is how real binderies do it.
         * Null means one signature for the whole document.
         */
        val sheetsPerSignature: Int? = null,
        /**
         * Creep (a.k.a. shingling) compensation, in points, for the outermost
         * sheet. Folded paper pushes inner pages outward at the fore-edge; this
         * shifts pages progressively towards the spine to cancel it. Roughly
         * paper thickness x sheets in the signature. Zero disables it.
         */
        val creepPt: Double = 0.0,
        /** Right-to-left binding, for Arabic, Hebrew and Japanese documents. */
        val rightToLeft: Boolean = false,
    ) : ImpositionMode {
        init {
            require(sheetsPerSignature == null || sheetsPerSignature >= 1) {
                "sheetsPerSignature must be at least 1"
            }
            require(creepPt >= 0) { "creepPt cannot be negative" }
        }
    }

    /**
     * The inverse of N-up: cut each source page into a grid of output pages.
     *
     * This is how an A3 spread, or a scan that caught two facing book pages in
     * one shot, becomes separate printable pages.
     */
    data class Split(
        val columns: Int = 2,
        val rows: Int = 1,
        val order: PageOrder = PageOrder.ACROSS_THEN_DOWN,
        /**
         * Overlap between adjacent tiles, in points. A little overlap means a
         * slightly crooked guillotine cut does not lose a line of text.
         */
        val overlapPt: Double = 0.0,
    ) : ImpositionMode {
        init {
            require(columns >= 1 && rows >= 1) { "Split grid must be at least 1x1" }
            require(columns * rows > 1) { "A 1x1 split would be a no-op" }
            require(overlapPt >= 0) { "overlapPt cannot be negative" }
        }

        val perPage: Int get() = columns * rows
    }

    /**
     * Blow one page up across a grid of sheets, to be trimmed and taped into a
     * poster.
     */
    data class Poster(
        val columns: Int = 2,
        val rows: Int = 2,
        /** Overlap between sheets, giving a glue flap. */
        val overlapPt: Double = 18.0,
        /** Trim guides and "row 2, col 3" labels on each sheet. */
        val assemblyMarks: Boolean = true,
    ) : ImpositionMode {
        init {
            require(columns >= 1 && rows >= 1) { "Poster grid must be at least 1x1" }
            require(columns * rows > 1) { "A 1x1 poster would be a no-op" }
            require(overlapPt >= 0) { "overlapPt cannot be negative" }
        }
    }
}

/** Reading order for grid layouts. */
enum class PageOrder {
    ACROSS_THEN_DOWN,
    DOWN_THEN_ACROSS,
    ACROSS_THEN_DOWN_RTL,
    DOWN_THEN_ACROSS_RTL,
    ;

    val isRightToLeft: Boolean
        get() = this == ACROSS_THEN_DOWN_RTL || this == DOWN_THEN_ACROSS_RTL

    val isColumnMajor: Boolean
        get() = this == DOWN_THEN_ACROSS || this == DOWN_THEN_ACROSS_RTL
}

enum class BindingEdge { LEFT, RIGHT, TOP, BOTTOM }

/** How a source page is sized into the space available to it. */
sealed interface Scaling {
    /** Scale up or down to fill the cell, preserving aspect ratio. */
    data object FitToPage : Scaling

    /** Shrink oversized pages, but never enlarge. Keeps small pages at true size. */
    data object ShrinkOversized : Scaling

    /** No scaling at all. Content larger than the cell is clipped. */
    data object ActualSize : Scaling

    /** Explicit factor, where 1.0 is 100%. */
    data class Fixed(val factor: Double) : Scaling {
        init {
            require(factor > 0) { "Scale factor must be positive" }
        }
    }
}
