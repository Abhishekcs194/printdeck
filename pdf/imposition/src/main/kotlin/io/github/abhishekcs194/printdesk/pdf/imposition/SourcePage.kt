package io.github.abhishekcs194.printdesk.pdf.imposition

import io.github.abhishekcs194.printdesk.core.model.SizePt

/**
 * A page of the input document, as far as imposition is concerned: where it is
 * and how big it is. Deliberately not the page itself — this module never sees
 * PDF content, which is what keeps it testable without a document.
 */
data class SourcePage(
    /** 0-based index into the source document. */
    val index: Int,
    val size: SizePt,
) {
    companion object {
        /** Convenience for the common case of a document with uniform page size. */
        fun uniform(count: Int, size: SizePt): List<SourcePage> =
            List(count) { SourcePage(it, size) }
    }
}
