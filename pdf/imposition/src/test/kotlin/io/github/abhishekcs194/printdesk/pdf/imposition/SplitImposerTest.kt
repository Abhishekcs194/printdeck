package io.github.abhishekcs194.printdesk.pdf.imposition

import com.google.common.truth.Truth.assertThat
import io.github.abhishekcs194.printdesk.core.model.ImpositionMode
import io.github.abhishekcs194.printdesk.core.model.ImpositionSettings
import io.github.abhishekcs194.printdesk.core.model.PaperSize
import io.github.abhishekcs194.printdesk.core.model.SizePt
import org.junit.Test

/**
 * Splitting is verified by mapping known points of the source page through each
 * sheet's transform and checking where they land. That tests the thing that
 * actually matters — which part of the page ends up on which sheet — rather
 * than restating the arithmetic.
 */
class SplitImposerTest {

    /** A3 landscape: the classic "two A4 pages scanned as one spread" case. */
    private val spread = SizePt(PaperSize.A3.size.height, PaperSize.A3.size.width)

    private fun plan(
        source: SizePt = spread,
        mode: ImpositionMode.Split = ImpositionMode.Split(columns = 2, rows = 1),
        pages: Int = 1,
    ) = Imposer.plan(
        List(pages) { source },
        ImpositionSettings(mode = mode, sheet = PaperSize.A4),
    )

    @Test
    fun `splitting a landscape spread in two chooses a portrait sheet`() {
        // Each half of a landscape A3 is portrait; the sheet must follow the
        // half, not the original page.
        val result = plan()
        assertThat(result.sheetSize.isLandscape).isFalse()
    }

    @Test
    fun `each source page becomes one sheet per tile`() {
        assertThat(plan(pages = 3).sheets).hasSize(6)
        assertThat(plan(mode = ImpositionMode.Split(columns = 2, rows = 2)).sheets).hasSize(4)
    }

    @Test
    fun `the first sheet shows the left half and the second the right half`() {
        val result = plan()
        val sheetCentre = result.sheetSize.width / 2

        // Centre of the source's left half.
        val leftCentre = spread.width * 0.25 to spread.height / 2
        val rightCentre = spread.width * 0.75 to spread.height / 2

        val (lx, _) = result.sheets[0].placements[0].transform.apply(leftCentre.first, leftCentre.second)
        val (rx, _) = result.sheets[1].placements[0].transform.apply(rightCentre.first, rightCentre.second)

        // Each half lands centred on its own sheet.
        assertThat(lx).isWithin(TOLERANCE).of(sheetCentre)
        assertThat(rx).isWithin(TOLERANCE).of(sheetCentre)
    }

    @Test
    fun `the halves together cover the whole page with no gap`() {
        val result = plan()
        // The cut line - the middle of the source - must map to the outer edge
        // of the printed area on both sheets: right edge of sheet 1, left edge
        // of sheet 2. Anything else means lost or duplicated content.
        val cutX = spread.width / 2
        val (endOfLeft, _) = result.sheets[0].placements[0].transform.apply(cutX, 0.0)
        val (startOfRight, _) = result.sheets[1].placements[0].transform.apply(cutX, 0.0)

        val leftClip = result.sheets[0].placements[0].clip!!
        val rightClip = result.sheets[1].placements[0].clip!!

        assertThat(endOfLeft).isWithin(TOLERANCE).of(leftClip.right)
        assertThat(startOfRight).isWithin(TOLERANCE).of(rightClip.x)
    }

    @Test
    fun `overlap makes each tile reach past the cut line`() {
        val overlap = 20.0
        val plain = plan()
        val bled = plan(mode = ImpositionMode.Split(columns = 2, rows = 1, overlapPt = overlap))

        val cutX = spread.width / 2
        // Without overlap the cut line sits exactly on the clip edge; with
        // overlap the tile continues past it, so the cut line maps inside.
        val plainClip = plain.sheets[0].placements[0].clip!!
        val bledClip = bled.sheets[0].placements[0].clip!!

        val (plainCut, _) = plain.sheets[0].placements[0].transform.apply(cutX, 0.0)
        val (bledCut, _) = bled.sheets[0].placements[0].transform.apply(cutX, 0.0)

        assertThat(plainClip.right - plainCut).isWithin(TOLERANCE).of(0.0)
        assertThat(bledClip.right - bledCut).isGreaterThan(0.0)
    }

    @Test
    fun `a four way split reads across then down`() {
        val result = plan(mode = ImpositionMode.Split(columns = 2, rows = 2))
        // Top-left of the source should appear on the first sheet: map the
        // source's top-left quadrant centre and check it lands on sheet 0.
        val quadrantCentre = spread.width * 0.25 to spread.height * 0.75
        val onFirstSheet = result.sheets[0].placements[0].transform
            .apply(quadrantCentre.first, quadrantCentre.second)

        val clip = result.sheets[0].placements[0].clip!!
        assertThat(onFirstSheet.first).isAtLeast(clip.x)
        assertThat(onFirstSheet.first).isAtMost(clip.right)
        assertThat(onFirstSheet.second).isAtLeast(clip.y)
        assertThat(onFirstSheet.second).isAtMost(clip.top)
    }

    @Test
    fun `a one by one split is rejected as a no-op`() {
        val error = runCatching { ImpositionMode.Split(columns = 1, rows = 1) }.exceptionOrNull()
        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
    }

    private companion object {
        const val TOLERANCE = 0.001
    }
}
