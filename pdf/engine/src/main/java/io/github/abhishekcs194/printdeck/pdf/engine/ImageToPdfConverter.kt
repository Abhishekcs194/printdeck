package io.github.abhishekcs194.printdeck.pdf.engine

import android.content.Context
import android.graphics.BitmapFactory
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

/**
 * Wraps images in a PDF so photos join the same path as documents.
 *
 * Everything downstream — imposition, preview, printing — speaks PDF, so images
 * are converted once at the door rather than every stage learning a second
 * format. That is what lets a set of photos be laid out 4-up or as a booklet
 * with no extra code.
 *
 * Lives here, not in the app, because PdfBox is an implementation detail of this
 * module and nothing above it should have to know the format exists.
 */
class ImageToPdfConverter(
    private val context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    /** Opens one image. A factory rather than a stream, so nothing is held open. */
    fun interface ImageSource {
        fun open(): InputStream?
    }

    /**
     * Each image becomes a page of its own dimensions rather than being forced
     * onto A4. Choosing a paper size here would bake in a crop and take the
     * decision away from the layout engine, which is the thing that actually
     * knows what paper is going to be used.
     */
    suspend fun convert(sources: List<ImageSource>, destination: File): File =
        withContext(dispatcher) {
            require(sources.isNotEmpty()) { "No images to convert" }
            PdfBoxRuntime.ensureInitialised(context)

            PDDocument().use { document ->
                sources.forEach { source ->
                    val bitmap = source.open()?.use { BitmapFactory.decodeStream(it) }
                        ?: error("An image could not be read")

                    val page = PDPage(
                        PDRectangle(bitmap.width.toFloat(), bitmap.height.toFloat()),
                    )
                    document.addPage(page)
                    val image = LosslessFactory.createFromImage(document, bitmap)
                    PDPageContentStream(document, page).use { stream ->
                        stream.drawImage(
                            image,
                            0f,
                            0f,
                            bitmap.width.toFloat(),
                            bitmap.height.toFloat(),
                        )
                    }
                    bitmap.recycle()
                }
                document.save(destination)
            }
            destination
        }
}
