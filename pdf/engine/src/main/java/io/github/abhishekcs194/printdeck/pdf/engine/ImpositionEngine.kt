package io.github.abhishekcs194.printdeck.pdf.engine

import android.content.Context
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.LayerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.util.Matrix
import io.github.abhishekcs194.printdeck.core.model.RectPt
import io.github.abhishekcs194.printdeck.pdf.imposition.ImpositionPlan
import io.github.abhishekcs194.printdeck.pdf.imposition.Placement
import io.github.abhishekcs194.printdeck.pdf.imposition.SheetMark
import io.github.abhishekcs194.printdeck.pdf.imposition.SheetPlan
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * Executes an [ImpositionPlan] against a source document, producing the PDF that
 * will actually be printed.
 *
 * **Pages are placed as vector Form XObjects, never rasterised.**
 * [LayerUtility.importPageAsForm] wraps a source page in a reusable form which
 * is then drawn under an arbitrary transform, so text stays text — selectable,
 * searchable and sharp at any scale. The shortcut of rendering each page to a
 * bitmap and pasting it produces the fuzzy, enormous handouts that mobile
 * printing is known for, and there is a test asserting this engine does not
 * regress into it.
 *
 * The engine is deliberately dumb: all the judgement lives in
 * `:pdf:imposition`, and this only carries out the instructions.
 */
class ImpositionEngine(
    private val context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    suspend fun impose(source: File, plan: ImpositionPlan, destination: File): File =
        withContext(dispatcher) {
            PdfBoxRuntime.ensureInitialised(context)

            // Phones have far less headroom than the desktops PdfBox assumes, and
            // a scanned document can be hundreds of megabytes. Spilling to a
            // temporary file past a modest threshold trades a little speed for
            // not being killed mid-job.
            val memory = MemoryUsageSetting.setupMixed(MAX_MAIN_MEMORY_BYTES)

            PDDocument.load(source, memory).use { sourceDocument ->
                PDDocument().use { output ->
                    val session = Session(output, sourceDocument)
                    plan.sheets.forEach { sheet ->
                        coroutineContext.ensureActive()
                        session.writeSheet(plan, sheet)
                    }
                    output.save(destination)
                }
            }
            destination
        }

    /**
     * State for one imposition run.
     *
     * Exists mainly to own [forms]: a source page used on twenty sheets must be
     * imported exactly once. Re-importing per placement embeds the same content
     * repeatedly and multiplies the file size by the number of pages per sheet -
     * which is how a 9-up handout turns into a document larger than its source.
     */
    private inner class Session(
        private val output: PDDocument,
        private val source: PDDocument,
    ) {
        private val layerUtility = LayerUtility(output)
        private val forms = HashMap<Int, PDFormXObject>()

        fun writeSheet(plan: ImpositionPlan, sheet: SheetPlan) {
            val page = PDPage(
                PDRectangle(plan.sheetSize.width.toFloat(), plan.sheetSize.height.toFloat()),
            )
            output.addPage(page)

            PDPageContentStream(
                output,
                page,
                PDPageContentStream.AppendMode.OVERWRITE,
                /* compress = */ true,
                /* resetContext = */ true,
            ).use { stream ->
                sheet.placements.forEach { placement ->
                    drawBorder(stream, placement.border)
                    val pageIndex = placement.sourcePageIndex ?: return@forEach
                    val form = forms.getOrPut(pageIndex) {
                        layerUtility.importPageAsForm(source, pageIndex)
                    }
                    drawPlacement(stream, placement, form)
                }
                sheet.marks.forEach { drawMark(stream, it) }
            }
        }
    }

    private fun drawPlacement(
        stream: PDPageContentStream,
        placement: Placement,
        form: PDFormXObject,
    ) {
        stream.saveGraphicsState()

        // Clipping is what makes split and poster work: the whole page is placed
        // each time, and only the window of interest is allowed to paint.
        placement.clip?.let { clip ->
            stream.addRect(
                clip.x.toFloat(),
                clip.y.toFloat(),
                clip.width.toFloat(),
                clip.height.toFloat(),
            )
            stream.clip()
        }

        val t = placement.transform
        stream.transform(
            Matrix(
                t.a.toFloat(), t.b.toFloat(),
                t.c.toFloat(), t.d.toFloat(),
                t.e.toFloat(), t.f.toFloat(),
            ),
        )
        stream.drawForm(form)
        stream.restoreGraphicsState()
    }

    private fun drawBorder(stream: PDPageContentStream, border: RectPt?) {
        if (border == null) return
        stream.saveGraphicsState()
        stream.setStrokingColor(BORDER_GREY, BORDER_GREY, BORDER_GREY)
        stream.setLineWidth(HAIRLINE)
        stream.addRect(
            border.x.toFloat(),
            border.y.toFloat(),
            border.width.toFloat(),
            border.height.toFloat(),
        )
        stream.stroke()
        stream.restoreGraphicsState()
    }

    private fun drawMark(stream: PDPageContentStream, mark: SheetMark) {
        stream.saveGraphicsState()
        when (mark) {
            is SheetMark.Line -> {
                stream.setStrokingColor(MARK_GREY, MARK_GREY, MARK_GREY)
                stream.setLineWidth(HAIRLINE)
                if (mark.dashed) {
                    stream.setLineDashPattern(floatArrayOf(DASH_ON, DASH_OFF), 0f)
                }
                stream.moveTo(mark.x1.toFloat(), mark.y1.toFloat())
                stream.lineTo(mark.x2.toFloat(), mark.y2.toFloat())
                stream.stroke()
            }

            is SheetMark.Label -> {
                stream.setNonStrokingColor(MARK_GREY, MARK_GREY, MARK_GREY)
                stream.beginText()
                stream.setFont(PDType1Font.HELVETICA, LABEL_POINTS)
                stream.newLineAtOffset(mark.x.toFloat(), mark.y.toFloat())
                stream.showText(mark.text)
                stream.endText()
            }
        }
        stream.restoreGraphicsState()
    }

    private companion object {
        /** Beyond this, PdfBox spills to a scratch file instead of the heap. */
        const val MAX_MAIN_MEMORY_BYTES = 32L * 1024 * 1024

        /** Thin enough to guide a cut without being mistaken for content. */
        const val HAIRLINE = 0.4f

        // PdfBox takes RGB components as 0..1 floats; the int overload is deprecated.
        const val BORDER_GREY = 0.78f
        const val MARK_GREY = 0.50f

        const val DASH_ON = 3f
        const val DASH_OFF = 3f
        const val LABEL_POINTS = 6f
    }
}
