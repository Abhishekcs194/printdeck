package io.github.abhishekcs194.printdesk.pdf.imposition

import com.google.common.truth.Truth.assertThat
import io.github.abhishekcs194.printdesk.core.model.ImpositionMode
import io.github.abhishekcs194.printdesk.core.model.ImpositionSettings
import io.github.abhishekcs194.printdesk.core.model.Margins
import io.github.abhishekcs194.printdesk.core.model.PageOrder
import io.github.abhishekcs194.printdesk.core.model.PaperSize
import io.github.abhishekcs194.printdesk.core.model.RectPt
import io.github.abhishekcs194.printdesk.core.model.Scaling
import org.junit.Test

class NUpImposerTest {

    private val a4 = PaperSize.A4.size

    private fun plan(
        pageCount: Int,
        mode: ImpositionMode.NUp,
        settings: ImpositionSettings = ImpositionSettings(),
    ) = Imposer.plan(
        List(pageCount) { a4 },
        settings.copy(mode = mode, sheet = PaperSize.A4),
    )

    private fun clips(sheet: SheetPlan): List<RectPt> = sheet.placements.mapNotNull { it.clip }

    @Test
    fun `two-up of portrait pages chooses a landscape sheet`() {
        // Two portrait A4s side by side fit a landscape A4 far better than a
        // portrait one - this is the single most common real-world layout, and
        // the orientation must be picked without the user asking.
        val result = plan(4, ImpositionMode.NUp(columns = 2, rows = 1))

        assertThat(result.sheetSize.isLandscape).isTrue()
        assertThat(result.sheets).hasSize(2)
        assertThat(result.sheets[0].placements.map { it.sourcePageIndex }).containsExactly(0, 1)
    }

    @Test
    fun `four-up of portrait pages stays on a portrait sheet`() {
        // A 2x2 grid preserves the page aspect, so turning the sheet would only
        // waste paper.
        val result = plan(4, ImpositionMode.NUp(columns = 2, rows = 2))

        assertThat(result.sheetSize.isLandscape).isFalse()
        assertThat(result.sheets).hasSize(1)
        assertThat(result.sheets[0].placements).hasSize(4)
    }

    @Test
    fun `across-then-down fills left to right, top row first`() {
        val sheet = plan(4, ImpositionMode.NUp(columns = 2, rows = 2)).sheets[0]
        val (p0, p1, p2, p3) = clips(sheet).let { listOf(it[0], it[1], it[2], it[3]) }

        assertThat(p0.x).isLessThan(p1.x) // 0 left of 1
        assertThat(p0.y).isWithin(TOLERANCE).of(p1.y) // same row
        assertThat(p2.y).isLessThan(p0.y) // 2 below 0
        assertThat(p2.x).isWithin(TOLERANCE).of(p0.x) // same column
        assertThat(p3.x).isGreaterThan(p2.x)
    }

    @Test
    fun `down-then-across fills the first column before the second`() {
        val sheet = plan(
            4,
            ImpositionMode.NUp(columns = 2, rows = 2, order = PageOrder.DOWN_THEN_ACROSS),
        ).sheets[0]
        val c = clips(sheet)

        assertThat(c[1].y).isLessThan(c[0].y) // page 1 sits below page 0
        assertThat(c[1].x).isWithin(TOLERANCE).of(c[0].x) // same column
        assertThat(c[2].x).isGreaterThan(c[0].x) // page 2 starts the next column
    }

    @Test
    fun `right to left order mirrors the columns`() {
        val ltr = clips(plan(2, ImpositionMode.NUp(columns = 2, rows = 1)).sheets[0])
        val rtl = clips(
            plan(2, ImpositionMode.NUp(columns = 2, rows = 1, order = PageOrder.ACROSS_THEN_DOWN_RTL))
                .sheets[0],
        )

        // First page starts on the right instead of the left.
        assertThat(ltr[0].x).isLessThan(ltr[1].x)
        assertThat(rtl[0].x).isGreaterThan(rtl[1].x)
    }

    @Test
    fun `cells never overlap`() {
        val sheet = plan(9, ImpositionMode.NUp(columns = 3, rows = 3, gutterPt = 6.0)).sheets[0]
        val boxes = clips(sheet)

        for (i in boxes.indices) {
            for (j in i + 1 until boxes.size) {
                assertThat(overlaps(boxes[i], boxes[j])).isFalse()
            }
        }
    }

    @Test
    fun `a short final sheet simply leaves cells empty`() {
        val result = plan(5, ImpositionMode.NUp(columns = 2, rows = 2))

        assertThat(result.sheets).hasSize(2)
        assertThat(result.sheets[1].placements).hasSize(1)
        assertThat(result.sheets[1].placements[0].sourcePageIndex).isEqualTo(4)
    }

    @Test
    fun `borders still describe the full grid on a short sheet`() {
        // Without this the grid visibly collapses on the last page.
        val result = plan(5, ImpositionMode.NUp(columns = 2, rows = 2, drawCellBorders = true))
        val last = result.sheets[1]

        assertThat(last.placements).hasSize(4)
        assertThat(last.placements.count { it.isBlank }).isEqualTo(3)
        assertThat(last.placements.all { it.border != null }).isTrue()
    }

    @Test
    fun `gutters take space from the cells`() {
        val tight = clips(plan(4, ImpositionMode.NUp(2, 2, gutterPt = 0.0)).sheets[0])[0]
        val loose = clips(plan(4, ImpositionMode.NUp(2, 2, gutterPt = 24.0)).sheets[0])[0]

        assertThat(loose.width).isLessThan(tight.width)
        assertThat(loose.height).isLessThan(tight.height)
    }

    @Test
    fun `margins inset the whole grid`() {
        val margin = 36.0
        val result = plan(
            4,
            ImpositionMode.NUp(2, 2),
            ImpositionSettings(margins = Margins.uniform(margin)),
        )
        val boxes = clips(result.sheets[0])

        assertThat(boxes.minOf { it.x }).isWithin(TOLERANCE).of(margin)
        assertThat(boxes.maxOf { it.right }).isWithin(TOLERANCE).of(result.sheetSize.width - margin)
    }

    @Test
    fun `actual size scaling does not resize the page`() {
        val result = plan(
            1,
            ImpositionMode.NUp(columns = 1, rows = 1, autoRotate = false),
            ImpositionSettings(scaling = Scaling.ActualSize, sheetLandscape = false),
        )
        // The page is placed at 100%: mapping its own corners through the
        // transform must give back its own dimensions.
        val transform = result.sheets[0].placements[0].transform
        val (x0, y0) = transform.apply(0.0, 0.0)
        val (x1, y1) = transform.apply(a4.width, a4.height)

        assertThat(x1 - x0).isWithin(TOLERANCE).of(a4.width)
        assertThat(y1 - y0).isWithin(TOLERANCE).of(a4.height)
    }

    @Test
    fun `page order is preserved across sheets`() {
        val result = plan(10, ImpositionMode.NUp(columns = 2, rows = 2))
        val order = result.sheets.flatMap { s -> s.placements.mapNotNull { it.sourcePageIndex } }

        assertThat(order).isEqualTo((0..9).toList())
    }

    @Test
    fun `reverse order prints the last page first`() {
        val result = plan(
            4,
            ImpositionMode.NUp(columns = 1, rows = 1),
            ImpositionSettings(reverseOrder = true),
        )
        val order = result.sheets.flatMap { s -> s.placements.mapNotNull { it.sourcePageIndex } }

        assertThat(order).isEqualTo(listOf(3, 2, 1, 0))
    }

    private fun overlaps(a: RectPt, b: RectPt): Boolean =
        a.x < b.right - TOLERANCE && b.x < a.right - TOLERANCE &&
            a.y < b.top - TOLERANCE && b.y < a.top - TOLERANCE

    private companion object {
        const val TOLERANCE = 0.001
    }
}
