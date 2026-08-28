package io.github.abhishekcs194.printdeck.pdf.engine

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One-time PdfBox setup.
 *
 * PdfBox-Android loads its font metrics and glyph lists from bundled assets, and
 * needs a [Context] to reach them. Skipping this does not fail loudly — it
 * surfaces much later as a missing-glyph or NPE deep inside text rendering — so
 * it is done once, defensively, from every entry point that touches a document.
 */
object PdfBoxRuntime {

    private val initialised = AtomicBoolean(false)

    fun ensureInitialised(context: Context) {
        if (initialised.compareAndSet(false, true)) {
            PDFBoxResourceLoader.init(context.applicationContext)
        }
    }
}
