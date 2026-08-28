package io.github.abhishekcs194.printdeck.pdf.engine

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import io.github.abhishekcs194.printdeck.core.model.ImpositionMode
import io.github.abhishekcs194.printdeck.core.model.ImpositionSettings
import io.github.abhishekcs194.printdeck.core.model.Margins
import io.github.abhishekcs194.printdeck.core.model.PaperSize
import io.github.abhishekcs194.printdeck.pdf.imposition.Imposer
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Writes real imposed PDFs to `build/samples/` so the output can be looked at,
 * printed and folded by a human.
 *
 * Automated assertions prove the structure is right; they cannot tell you a
 * booklet folds into the correct reading order in your hands, or that a 4-up
 * handout is actually legible. This exists to make that check cheap.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SampleOutputGenerator {

    private val outputDirectory = File("build/samples").apply { mkdirs() }

    private fun sourceDocument(pageCount: Int, name: String): File {
        val file = File(outputDirectory, name)
        PDDocument().use { document ->
            repeat(pageCount) { index ->
                val page = PDPage(PDRectangle.A4)
                document.addPage(page)
                PDPageContentStream(document, page).use { stream ->
                    // A big page number, so folding order is obvious at a glance.
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA_BOLD, 96f)
                    stream.newLineAtOffset(220f, 480f)
                    stream.showText("${index + 1}")
                    stream.endText()

                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, 14f)
                    stream.newLineAtOffset(72f, 760f)
                    stream.showText("PrintDeck sample - source page ${index + 1} of $pageCount")
                    stream.endText()

                    // A frame, so scaling and margins are visible.
                    stream.setLineWidth(1f)
                    stream.addRect(36f, 36f, PDRectangle.A4.width - 72f, PDRectangle.A4.height - 72f)
                    stream.stroke()
                }
            }
            document.save(file)
        }
        return file
    }

    @Test
    fun `write one sample per layout mode`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        // The sample sources use PdfBox fonts directly, and PdfBox loads its font
        // metrics from bundled assets. Touching a font before this is initialised
        // fails in the class initialiser, which reports as an unhelpful
        // ExceptionInInitializerError rather than anything about fonts.
        PdfBoxRuntime.ensureInitialised(context)

        val engine = ImpositionEngine(context)
        val reader = PdfDocumentReader(context)

        val samples = listOf(
            Triple("2-up.pdf", 8, ImpositionMode.NUp(columns = 2, rows = 1)),
            Triple("4-up-bordered.pdf", 8, ImpositionMode.NUp(columns = 2, rows = 2, gutterPt = 8.0, drawCellBorders = true)),
            Triple("9-up.pdf", 9, ImpositionMode.NUp(columns = 3, rows = 3, gutterPt = 6.0, drawCellBorders = true)),
            Triple("booklet.pdf", 8, ImpositionMode.Booklet()),
            Triple("booklet-16-page.pdf", 16, ImpositionMode.Booklet(creepPt = 6.0)),
            Triple("split-in-two.pdf", 2, ImpositionMode.Split(columns = 2, rows = 1)),
            Triple("poster-2x2.pdf", 1, ImpositionMode.Poster(columns = 2, rows = 2)),
        )

        samples.forEach { (name, pages, mode) ->
            val source = sourceDocument(pages, "_source-$pages-page.pdf")
            val info = reader.read(source)
            val plan = Imposer.plan(
                info.pageSizes,
                ImpositionSettings(
                    mode = mode,
                    sheet = PaperSize.A4,
                    margins = Margins.uniformMm(8.0),
                ),
            )
            val output = engine.impose(source, plan, File(outputDirectory, name))
            assertThat(output.length()).isGreaterThan(0)
            println("sample: ${output.absolutePath} (${plan.sheetCount} sheets)")
        }
    }
}
