package io.github.abhishekcs194.printdeck.print.system

import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * Hands an already-imposed PDF to the platform print framework.
 *
 * Every layout decision has been made by the time this runs: the file on disk is
 * exactly what should come out of the printer, page for page. So this adapter
 * deliberately does nothing clever — it reports the page count and copies bytes.
 * Re-laying anything out here would mean a second implementation of the
 * imposition rules competing with the real one.
 *
 * The one thing worth getting right is [onLayout]. Returning `false` for
 * "nothing changed" when the user picks a different paper size would leave the
 * framework showing a stale preview, so a genuine change is reported whenever
 * the attributes differ.
 */
class ImposedPdfPrintAdapter(
    private val file: File,
    private val jobName: String,
    private val pageCount: Int,
) : PrintDocumentAdapter() {

    private var lastAttributes: PrintAttributes? = null

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback,
        extras: Bundle?,
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onLayoutCancelled()
            return
        }

        val info = PrintDocumentInfo.Builder(jobName)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(pageCount)
            .build()

        val changed = newAttributes != lastAttributes
        lastAttributes = newAttributes
        callback.onLayoutFinished(info, changed)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor?,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback,
    ) {
        if (destination == null) {
            callback.onWriteFailed("No destination to write to")
            return
        }

        try {
            val completed = copyTo(destination, cancellationSignal)
            if (!completed) {
                callback.onWriteCancelled()
                return
            }
            // The whole document is always written. Page selection was applied
            // during imposition, so what is on disk is already the final job -
            // narrowing it again here would drop pages the user asked for.
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (error: IOException) {
            callback.onWriteFailed(error.message)
        }
    }

    /** @return false if the copy was cancelled part-way. */
    private fun copyTo(
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
    ): Boolean = FileInputStream(file).use { input ->
        FileOutputStream(destination.fileDescriptor).use { output ->
            pump(input, output, cancellationSignal)
        }
    }

    /**
     * Copies in chunks, checking for cancellation between each.
     *
     * A print job can be several megabytes and the framework may cancel at any
     * point — if the user backs out of the dialog, or picks a different printer.
     * Checking only at the start would keep writing to a descriptor nobody is
     * reading any more.
     */
    private fun pump(
        input: FileInputStream,
        output: FileOutputStream,
        cancellationSignal: CancellationSignal?,
    ): Boolean {
        val buffer = ByteArray(BUFFER_BYTES)
        while (true) {
            if (cancellationSignal?.isCanceled == true) return false
            val read = input.read(buffer)
            if (read <= 0) return true
            output.write(buffer, 0, read)
        }
    }

    private companion object {
        const val BUFFER_BYTES = 16 * 1024
    }
}
