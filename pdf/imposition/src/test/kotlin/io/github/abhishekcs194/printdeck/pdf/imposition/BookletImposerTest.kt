package io.github.abhishekcs194.printdeck.pdf.imposition

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.abhishekcs194.printdeck.core.model.ImpositionMode
import io.github.abhishekcs194.printdeck.core.model.ImpositionSettings
import io.github.abhishekcs194.printdeck.core.model.PaperSize
import io.github.abhishekcs194.printdeck.core.model.SizePt
import org.junit.Test

/**
 * Booklet imposition is the part of this app most likely to be quietly wrong:
 * the output looks plausible on screen and only reveals itself once the paper is
 * folded. So rather than asserting the formula against itself, these tests
 * **simulate the fold** and check that the resulting booklet reads 1, 2, 3, ...
 *
 * The fold model, for one folded sheet:
 *
 *  - flipping a sheet about its vertical fold axis puts the front-RIGHT half
 *    back-to-back with the back-LEFT half, so those two form one leaf;
 *  - the other leaf is back-right (recto) and front-left (verso);
 *  - nested sheets read right-leaves outermost-inwards, then left-leaves
 *    innermost-outwards.
 */
class BookletImposerTest {

    private val a4 = PaperSize.A4.size
    private val sheetSize = SizePt(a4.height, a4.width) // landscape, two-up

    private fun settings(mode: ImpositionMode.Booklet = ImpositionMode.Booklet()) =
        ImpositionSettings(mode = mode, sheet = PaperSize.A4, sheetLandscape = true)

    private fun planFor(pageCount: Int, mode: ImpositionMode.Booklet = ImpositionMode.Booklet()) =
        Imposer.plan(List(pageCount) { a4 }, settings(mode))

    /** Splits a sheet's placements into (leftPage, rightPage) by where they landed. */
    private fun halves(sheet: SheetPlan): Pair<Int?, Int?> {
        val midpoint = sheetSize.width / 2
        val left = sheet.placements.firstOrNull { (it.clip?.centerX ?: 0.0) < midpoint }
        val right = sheet.placements.firstOrNull { (it.clip?.centerX ?: 0.0) >= midpoint }
        return left?.sourcePageIndex to right?.sourcePageIndex
    }

    /** Reads the booklet the way a person would after folding and nesting it. */
    private fun readingOrder(plan: ImpositionPlan): List<Int?> {
        val sheetCount = plan.sheets.size / 2
        val fronts = (0 until sheetCount).map { halves(plan.sheets[it * 2]) }
        val backs = (0 until sheetCount).map { halves(plan.sheets[it * 2 + 1]) }

        return buildList {
            // Right-hand leaves, outermost sheet inwards.
            for (i in 0 until sheetCount) {
                add(fronts[i].second) // recto
                add(backs[i].first) // verso
            }
            // Left-hand leaves, innermost sheet back outwards.
            for (i in sheetCount - 1 downTo 0) {
                add(backs[i].second) // recto
                add(fronts[i].first) // verso
            }
        }
    }

    @Test
    fun `folded booklet reads in document order for every page count`() {
        for (pageCount in 1..64) {
            val plan = planFor(pageCount)
            val order = readingOrder(plan)

            val expected = (0 until pageCount).toList() +
                List(order.size - pageCount) { null } // padding blanks land at the end

            assertWithMessage("reading order for $pageCount pages").that(order).isEqualTo(expected)
        }
    }

    @Test
    fun `every source page is placed exactly once`() {
        for (pageCount in 1..64) {
            val plan = planFor(pageCount)
            val placed = plan.sheets
                .flatMap { sheet -> sheet.placements.mapNotNull { it.sourcePageIndex } }
                .sorted()

            assertWithMessage("pages placed for $pageCount").that(placed)
                .isEqualTo((0 until pageCount).toList())
        }
    }

    @Test
    fun `page count is padded up to whole folded sheets`() {
        // Four pages per folded sheet, two printed sides per sheet.
        mapOf(1 to 2, 4 to 2, 5 to 4, 8 to 4, 9 to 6, 20 to 10).forEach { (pages, expectedSides) ->
            assertWithMessage("$pages pages").that(planFor(pages).sheets).hasSize(expectedSides)
        }
    }

    @Test
    fun `signatures split a long document into separate folded sections`() {
        // 32 pages in signatures of 2 sheets = 8 pages each -> 4 signatures.
        val plan = planFor(32, ImpositionMode.Booklet(sheetsPerSignature = 2))
        assertThat(plan.sheets).hasSize(16)

        // Each signature must still be internally correct: its first sheet pairs
        // that signature's own last page with its own first page.
        val firstSheetOfSecondSignature = halves(plan.sheets[4])
        assertThat(firstSheetOfSecondSignature.second).isEqualTo(8) // first page of signature 2
        assertThat(firstSheetOfSecondSignature.first).isEqualTo(15) // last page of signature 2
    }

    @Test
    fun `right to left binding mirrors the halves`() {
        val ltr = halves(planFor(8).sheets[0])
        val rtl = halves(planFor(8, ImpositionMode.Booklet(rightToLeft = true)).sheets[0])

        assertThat(rtl.first).isEqualTo(ltr.second)
        assertThat(rtl.second).isEqualTo(ltr.first)
    }

    @Test
    fun `creep shifts inner sheets towards the spine and leaves the outermost alone`() {
        val creep = 12.0
        val plan = planFor(32, ImpositionMode.Booklet(creepPt = creep))
        val sheetCount = plan.sheets.size / 2

        // Left-hand pages move right (towards the spine) as sheets get inner.
        val leftEdges = (0 until sheetCount).map { i ->
            plan.sheets[i * 2].placements.first { (it.clip?.centerX ?: 0.0) < sheetSize.width / 2 }
                .clip!!.x
        }

        assertThat(leftEdges.first()).isWithin(TOLERANCE).of(leftEdges[0])
        assertThat(leftEdges).isInOrder() // monotonically towards the spine
        assertThat(leftEdges.last() - leftEdges.first()).isWithin(TOLERANCE).of(creep)
    }

    @Test
    fun `no creep leaves every sheet aligned`() {
        val plan = planFor(32, ImpositionMode.Booklet(creepPt = 0.0))
        val xs = plan.sheets.map { sheet ->
            sheet.placements.first { (it.clip?.centerX ?: 0.0) < sheetSize.width / 2 }.clip!!.x
        }
        assertThat(xs.toSet()).hasSize(1)
    }

    @Test
    fun `an empty document produces no sheets`() {
        assertThat(Imposer.plan(emptyList(), settings()).sheets).isEmpty()
    }

    private companion object {
        const val TOLERANCE = 0.001
    }
}
