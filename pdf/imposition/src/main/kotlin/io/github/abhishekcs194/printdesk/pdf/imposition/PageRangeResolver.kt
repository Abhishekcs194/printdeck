package io.github.abhishekcs194.printdesk.pdf.imposition

import io.github.abhishekcs194.printdesk.core.model.PageRange
import io.github.abhishekcs194.printdesk.core.model.PageSelection

/**
 * Turns what the user typed into concrete page indices.
 *
 * Input is 1-based (what print dialogs show and users type); output is 0-based
 * (what the document API wants). Getting that boundary wrong is a classic
 * off-by-one, so the conversion happens in exactly one place: [resolve].
 */
object PageRangeResolver {

    /**
     * Parses a page-range expression such as `1-5,8,11-` or `-4, 9`.
     *
     * Accepted forms per comma-separated term:
     *  - `N`     a single page
     *  - `N-M`   an inclusive run
     *  - `N-`    from N to the end
     *  - `-M`    from the start to M
     *
     * Whitespace is ignored and empty terms are skipped, so a trailing comma is
     * tolerated rather than treated as an error - people type those.
     *
     * @throws IllegalArgumentException on a malformed term, naming the term.
     */
    fun parse(spec: String): List<PageRange> {
        if (spec.isBlank()) return listOf(PageRange.OPEN)

        return spec.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { term -> parseTerm(term) }
            .ifEmpty { listOf(PageRange.OPEN) }
    }

    private fun parseTerm(term: String): PageRange {
        val hyphen = term.indexOf('-')

        // "N" - a single page.
        if (hyphen < 0) {
            val page = term.pageNumber(term, "is not a page number")
            require(page >= 1) { "Page numbers start at 1, got '$term'" }
            return PageRange.single(page)
        }

        val before = term.substring(0, hyphen).trim()
        val after = term.substring(hyphen + 1).trim()

        // A second hyphen means something like "1-2-3".
        require(!after.contains('-')) { "'$term' is not a valid page range" }

        val from = before.ifEmpty { null }?.pageNumber(term, "has an invalid start page")
        val to = after.ifEmpty { null }?.pageNumber(term, "has an invalid end page")

        require(from != null || to != null) { "'$term' has no page numbers" }
        require(from == null || from >= 1) { "Page numbers start at 1, got '$term'" }
        require(to == null || to >= 1) { "Page numbers start at 1, got '$term'" }
        require(from == null || to == null || from <= to) {
            "'$term' runs backwards; write it as ${to}-${from}"
        }

        return PageRange(from, to)
    }

    /**
     * Expands a selection against a real document.
     *
     * Returns **0-based** page indices in the order the user asked for, with
     * duplicates removed. Out-of-range pages are clipped rather than rejected:
     * asking for `1-999` of a 10-page document is a reasonable way to say
     * "everything", not a mistake worth an error dialog.
     */
    fun resolve(selection: PageSelection, pageCount: Int): List<Int> {
        if (pageCount <= 0) return emptyList()

        val pages = LinkedHashSet<Int>()
        for (range in selection.ranges) {
            val from = (range.from ?: 1).coerceAtLeast(1)
            val to = (range.to ?: pageCount).coerceAtMost(pageCount)
            if (from > to) continue
            for (page in from..to) pages.add(page)
        }

        // Parity is on the printed page number, which is what "print odd pages"
        // means on every desktop dialog.
        val filtered = when (selection.filter) {
            PageSelection.Parity.ALL -> pages
            PageSelection.Parity.ODD -> pages.filter { it % 2 == 1 }
            PageSelection.Parity.EVEN -> pages.filter { it % 2 == 0 }
        }

        return filtered.map { it - 1 }
    }

    /** Parses one page number, reporting the whole term rather than the fragment. */
    private fun String.pageNumber(term: String, complaint: String): Int =
        toIntOrNull() ?: throw IllegalArgumentException("'$term' $complaint")
}
