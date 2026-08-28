package io.github.abhishekcs194.printdeck.pdf.engine

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.text.PDFTextStripper
import io.github.abhishekcs194.printdeck.core.model.ImpositionMode
import io.github.abhishekcs194.printdeck.core.model.ImpositionSettings
import io.github.abhishekcs194.printdeck.core.model.PaperSize
import io.github.abhishekcs194.printdeck.pdf.imposition.Imposer
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImpositionEngineTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var engine: ImpositionEngine
    private lateinit var reader: PdfDocumentReader

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        engine = ImpositionEngine(context)
        reader = PdfDocumentReader(context)
    }

    /** A real PDF with real text, so vector-ness can be asserted rather than assumed. */
    private fun sourceDocument(pageCount: Int): File {
        val file = temporaryFolder.newFile("source-$pageCount.pdf")
        PDDocument().use { document ->
            repeat(pageCount) { index ->
                val page = PDPage(PDRectangle.A4)
                document.addPage(page)
                PDPageContentStream(document, page).use { stream ->
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, 24f)
                    stream.newLineAtOffset(72f, 700f)
                    stream.showText("PageMarker${index + 1}")
                    stream.endText()
                }
            }
            document.save(file)
        }
        return file
    }

    private suspend fun impose(
        source: File,
        mode: ImpositionMode,
        name: String,
    ): File {
        val info = reader.read(source)
        val plan = Imposer.plan(
            info.pageSizes,
            ImpositionSettings(mode = mode, sheet = PaperSize.A4),
        )
        return engine.impose(source, plan, temporaryFolder.newFile(name))
    }

    @Test
    fun `two-up puts four pages onto two landscape sheets`() = runTest {
        val output = impose(sourceDocument(4), ImpositionMode.NUp(columns = 2, rows = 1), "2up.pdf")

        PDDocument.load(output).use { document ->
            assertThat(document.numberOfPages).isEqualTo(2)
            val page = document.getPage(0).mediaBox
            assertThat(page.width).isGreaterThan(page.height) // landscape
        }
    }

    /**
     * The regression guard for the single most important property of this engine.
     *
     * Pages must be placed as vector Form XObjects. If someone ever "simplifies"
     * this by rendering to a bitmap and pasting, output goes fuzzy and file sizes
     * explode — and it would look perfectly fine on screen. Asserting the object
     * types catches that at build time.
     */
    @Test
    fun `imposed pages are vector forms, not images`() = runTest {
        val output = impose(sourceDocument(4), ImpositionMode.NUp(columns = 2, rows = 2), "4up.pdf")

        PDDocument.load(output).use { document ->
            val resources = document.getPage(0).resources
            val objects = resources.xObjectNames.map { resources.getXObject(it) }

            assertThat(objects).isNotEmpty()
            assertThat(objects.all { it is PDFormXObject }).isTrue()
            assertThat(objects.none { it is PDImageXObject }).isTrue()
        }
    }

    /** The practical proof of the same thing: the text survived as text. */
    @Test
    fun `text remains selectable after imposition`() = runTest {
        val output = impose(sourceDocument(4), ImpositionMode.NUp(columns = 2, rows = 2), "text.pdf")

        PDDocument.load(output).use { document ->
            val text = PDFTextStripper().getText(document)
            assertThat(text).contains("PageMarker1")
            assertThat(text).contains("PageMarker4")
        }
    }

    @Test
    fun `a source page used on several sheets is embedded only once`() = runTest {
        // Poster tiling places the same page on every sheet. Re-importing it per
        // placement would multiply the file size by the tile count.
        val output = impose(
            sourceDocument(1),
            ImpositionMode.Poster(columns = 2, rows = 2),
            "poster.pdf",
        )

        PDDocument.load(output).use { document ->
            assertThat(document.numberOfPages).isEqualTo(4)
            // Every sheet references a form; they must be the same underlying object.
            val streams = (0 until document.numberOfPages).map { index ->
                val resources = document.getPage(index).resources
                resources.xObjectNames.map { (resources.getXObject(it) as PDFormXObject).cosObject }
            }.flatten()
            assertThat(streams.distinct()).hasSize(1)
        }
    }

    @Test
    fun `booklet output has one side per printed page`() = runTest {
        val output = impose(sourceDocument(8), ImpositionMode.Booklet(), "booklet.pdf")

        PDDocument.load(output).use { document ->
            // 8 pages = 2 folded sheets = 4 printed sides.
            assertThat(document.numberOfPages).isEqualTo(4)
        }
    }

    @Test
    fun `splitting a page yields one sheet per tile`() = runTest {
        val output = impose(
            sourceDocument(2),
            ImpositionMode.Split(columns = 2, rows = 1),
            "split.pdf",
        )

        PDDocument.load(output).use { document ->
            assertThat(document.numberOfPages).isEqualTo(4) // 2 pages x 2 halves
        }
    }

    @Test
    fun `page rotation is honoured when reading sizes`() = runTest {
        // A portrait box with /Rotate 90 displays as landscape. Reading the media
        // box alone would lay scanned documents out sideways.
        val file = temporaryFolder.newFile("rotated.pdf")
        PDDocument().use { document ->
            val page = PDPage(PDRectangle.A4)
            page.rotation = 90
            document.addPage(page)
            document.save(file)
        }

        val info = reader.read(file)
        val size = info.pageSizes.single()
        assertThat(size.isLandscape).isTrue()
        assertThat(size.width).isWithin(0.01).of(PDRectangle.A4.height.toDouble())
    }
}
