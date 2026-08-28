package io.github.abhishekcs194.printdeck.core.model

/**
 * Which source pages take part in a job.
 *
 * Selections compose: [Custom] narrows to a set of pages, and [filter] can then
 * keep only the odd or even ones within that set — which is what makes the
 * manual-duplex workflow ("print 1-20 odd, flip the stack, print 1-20 even")
 * expressible as a single value.
 */
data class PageSelection(
    val ranges: List<PageRange>,
    val filter: Parity = Parity.ALL,
) {
    enum class Parity { ALL, ODD, EVEN }

    companion object {
        /** Every page, in document order. */
        val All = PageSelection(ranges = listOf(PageRange.OPEN), filter = Parity.ALL)

        fun odd() = PageSelection(listOf(PageRange.OPEN), Parity.ODD)

        fun even() = PageSelection(listOf(PageRange.OPEN), Parity.EVEN)
    }
}

/**
 * A closed or half-open run of pages, in **1-based** page numbers, matching what
 * the user types and what every other print dialog shows.
 *
 * `null` on either bound means "unbounded that way", so `11-` is
 * `PageRange(11, null)` and resolves against the real page count later.
 */
data class PageRange(val from: Int?, val to: Int?) {
    init {
        require(from == null || from >= 1) { "Page numbers are 1-based, got $from" }
        require(to == null || to >= 1) { "Page numbers are 1-based, got $to" }
    }

    companion object {
        /** The whole document. */
        val OPEN = PageRange(null, null)

        fun single(page: Int) = PageRange(page, page)
    }
}
