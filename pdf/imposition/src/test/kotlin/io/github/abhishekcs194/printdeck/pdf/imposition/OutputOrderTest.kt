package io.github.abhishekcs194.printdeck.pdf.imposition

import com.google.common.truth.Truth.assertThat
import io.github.abhishekcs194.printdeck.core.model.AffineTransform
import io.github.abhishekcs194.printdeck.core.model.SizePt
import org.junit.Test

class OutputOrderTest {

    private fun sheet(page: Int, side: SheetSide = SheetSide.SINGLE) = SheetPlan(
        placements = listOf(Placement(page, AffineTransform.Identity)),
        side = side,
    )

    private fun plan(vararg sheets: SheetPlan) =
        ImpositionPlan(SizePt(595.0, 842.0), sheets.toList())

    /** The source page on each side, in the order they would be printed. */
    private fun order(plan: ImpositionPlan) =
        plan.sheets.map { it.placements.single().sourcePageIndex }

    @Test
    fun `simplex sheets are sent last first`() {
        val reversed = plan(sheet(0), sheet(1), sheet(2), sheet(3))
            .reversedForFaceUpStacking()

        // Printed in this order, sheet 0 ends up on top of a face-up stack.
        assertThat(order(reversed)).containsExactly(3, 2, 1, 0).inOrder()
    }

    @Test
    fun `duplex keeps each front with its own back`() {
        // The failure this guards against: reversing sides as a flat list puts a
        // back before its front, so each lands on the wrong face of the paper.
        val reversed = plan(
            sheet(0, SheetSide.FRONT), sheet(1, SheetSide.BACK),
            sheet(2, SheetSide.FRONT), sheet(3, SheetSide.BACK),
            sheet(4, SheetSide.FRONT), sheet(5, SheetSide.BACK),
        ).reversedForFaceUpStacking()

        assertThat(order(reversed)).containsExactly(4, 5, 2, 3, 0, 1).inOrder()
    }

    @Test
    fun `sides stay in front-then-back order within every sheet`() {
        val reversed = plan(
            sheet(0, SheetSide.FRONT), sheet(1, SheetSide.BACK),
            sheet(2, SheetSide.FRONT), sheet(3, SheetSide.BACK),
        ).reversedForFaceUpStacking()

        reversed.sheets.chunked(2).forEach { (front, back) ->
            assertThat(front.side).isEqualTo(SheetSide.FRONT)
            assertThat(back.side).isEqualTo(SheetSide.BACK)
        }
    }

    @Test
    fun `a booklet's folded order survives reversal`() {
        // Eight pages fold into two sheets. Reversing which sheet goes through
        // the printer first must not disturb what is printed on either of them.
        val booklet = Imposer.plan(
            List(8) { SizePt(595.276, 841.89) },
            io.github.abhishekcs194.printdeck.core.model.ImpositionSettings(
                mode = io.github.abhishekcs194.printdeck.core.model.ImpositionMode.Booklet(),
            ),
        )
        val reversed = booklet.reversedForFaceUpStacking()

        assertThat(reversed.sheets).hasSize(booklet.sheets.size)
        assertThat(reversed.referencedPages).isEqualTo(booklet.referencedPages)
        // Last physical sheet first, its two sides still the right way round.
        assertThat(reversed.sheets.first().side).isEqualTo(SheetSide.FRONT)
        assertThat(reversed.sheets[1].side).isEqualTo(SheetSide.BACK)
    }

    @Test
    fun `an odd trailing side is not paired with an unrelated sheet`() {
        val reversed = plan(
            sheet(0, SheetSide.FRONT), sheet(1, SheetSide.BACK),
            sheet(2, SheetSide.FRONT), // no back: malformed, but must not eat the next
        ).reversedForFaceUpStacking()

        assertThat(order(reversed)).containsExactly(2, 0, 1).inOrder()
    }

    @Test
    fun `reversing twice returns the original order`() {
        val original = plan(sheet(0), sheet(1), sheet(2))
        val there = original.reversedForFaceUpStacking()
        val back = there.reversedForFaceUpStacking()

        assertThat(order(back)).isEqualTo(order(original))
    }

    @Test
    fun `a single sheet is unchanged`() {
        val single = plan(sheet(0))
        assertThat(order(single.reversedForFaceUpStacking())).containsExactly(0)
    }

    @Test
    fun `an empty plan is unchanged`() {
        assertThat(plan().reversedForFaceUpStacking().sheets).isEmpty()
    }
}
