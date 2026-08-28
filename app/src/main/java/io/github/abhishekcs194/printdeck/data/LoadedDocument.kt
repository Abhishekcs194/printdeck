package io.github.abhishekcs194.printdeck.data

import io.github.abhishekcs194.printdeck.core.model.SizePt
import java.io.File

/**
 * A document that has been brought into the app and is ready to lay out.
 *
 * [file] is always a real file in the app's private cache, never the picked
 * content URI. Both PdfBox and the platform PdfRenderer need random access, and
 * a provider is free to hand back a stream that cannot seek — so everything is
 * copied in once, up front, rather than failing unpredictably later depending on
 * which app the document came from.
 */
data class LoadedDocument(
    val displayName: String,
    val file: File,
    val pageCount: Int,
    val pageSizes: List<SizePt>,
    /** True when the source was images rather than a PDF. */
    val convertedFromImages: Boolean = false,
)

/** A document the user opened before, remembered by its source URI. */
data class RecentDocument(
    val uri: String,
    val displayName: String,
    val openedAtMillis: Long,
)
