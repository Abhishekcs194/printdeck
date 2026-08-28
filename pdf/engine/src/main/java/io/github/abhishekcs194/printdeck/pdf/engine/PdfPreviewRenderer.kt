package io.github.abhishekcs194.printdeck.pdf.engine

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Renders pages to bitmaps for display.
 *
 * This is the **only** place rasterisation happens, and it never touches output.
 * Previews are drawn from the already-imposed document, which is what makes the
 * preview trustworthy: it is a picture of the very file that will be sent to the
 * printer, so it cannot drift from the result the way a re-simulated preview
 * would.
 *
 * Uses the platform [PdfRenderer] rather than PdfBox. It is hardware-accelerated,
 * costs nothing in binary size, and its output is what the rest of Android would
 * show for the same file.
 */
class PdfPreviewRenderer(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    suspend fun pageCount(file: File): Int = withContext(dispatcher) {
        open(file) { renderer -> renderer.pageCount }
    }

    /**
     * Renders one page to fit [targetWidthPx], preserving aspect.
     *
     * The bitmap is filled white first. PDF pages have no background of their
     * own, so skipping that leaves transparent areas that composite against the
     * app's surface — which looks fine in light mode and turns pages black in
     * dark mode.
     */
    suspend fun renderPage(
        file: File,
        pageIndex: Int,
        targetWidthPx: Int,
    ): Bitmap = withContext(dispatcher) {
        require(targetWidthPx > 0) { "Target width must be positive" }

        open(file) { renderer ->
            require(pageIndex in 0 until renderer.pageCount) {
                "Page $pageIndex is outside a ${renderer.pageCount}-page document"
            }
            renderer.openPage(pageIndex).use { page ->
                val scale = targetWidthPx.toFloat() / page.width
                val height = (page.height * scale).toInt().coerceAtLeast(1)

                val bitmap = Bitmap.createBitmap(targetWidthPx, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        }
    }

    /** Aspect ratio of a page, for laying out a placeholder before it renders. */
    suspend fun pageAspect(file: File, pageIndex: Int): Float = withContext(dispatcher) {
        open(file) { renderer ->
            renderer.openPage(pageIndex).use { page ->
                page.width.toFloat() / page.height
            }
        }
    }

    private inline fun <T> open(file: File, block: (PdfRenderer) -> T): T =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use(block)
        }
}
