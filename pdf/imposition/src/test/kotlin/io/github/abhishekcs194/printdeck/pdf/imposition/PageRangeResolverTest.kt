package io.github.abhishekcs194.printdeck.pdf.imposition

import com.google.common.truth.Truth.assertThat
import io.github.abhishekcs194.printdeck.core.model.PageRange
import io.github.abhishekcs194.printdeck.core.model.PageSelection
import org.junit.Test

/**
 * The range parser sits on the 1-based/0-based boundary, which is where
 * off-by-one bugs live. Users type page numbers starting at 1; the document API
 * indexes from 0.
 */
class PageRangeResolverTest {

    private fun resolve(spec: String, pageCount: Int, parity: PageSelection.Parity = PageSelection.Parity.ALL) =
        PageRangeResolver.resolve(
            PageSelection(PageRangeResolver.parse(spec), parity),
            pageCount,
        )

    @Test
    fun `parses the mixed expression from the print dialog`() {
        // 1-5, 8, 11 to the end, against a 12-page document.
        assertThat(resolve("1-5,8,11-", 12))
            .isEqualTo(listOf(0, 1, 2, 3, 4, 7, 10, 11))
    }

    @Test
    fun `single pages, open starts and open ends`() {
        assertThat(resolve("3", 10)).isEqualTo(listOf(2))
        assertThat(resolve("-3", 10)).isEqualTo(listOf(0, 1, 2))
        assertThat(resolve("8-", 10)).isEqualTo(listOf(7, 8, 9))
    }

    @Test
    fun `blank spec means the whole document`() {
        assertThat(resolve("", 4)).isEqualTo(listOf(0, 1, 2, 3))
        assertThat(resolve("   ", 4)).isEqualTo(listOf(0, 1, 2, 3))
    }

    @Test
    fun `whitespace and trailing commas are tolerated`() {
        // People type these; they are not worth an error dialog.
        assertThat(resolve(" 1 - 3 , 5 , ", 6)).isEqualTo(listOf(0, 1, 2, 4))
    }

    @Test
    fun `overlapping ranges are de-duplicated but keep the order asked for`() {
        assertThat(resolve("5,1-3,2", 10)).isEqualTo(listOf(4, 0, 1, 2))
    }

    @Test
    fun `pages beyond the document are clipped rather than rejected`() {
        // "1-999" is a reasonable way to say "everything", not a mistake.
        assertThat(resolve("1-999", 3)).isEqualTo(listOf(0, 1, 2))
        assertThat(resolve("50-60", 3)).isEmpty()
    }

    @Test
    fun `parity filters on the printed page number`() {
        assertThat(resolve("", 6, PageSelection.Parity.ODD)).isEqualTo(listOf(0, 2, 4))
        assertThat(resolve("", 6, PageSelection.Parity.EVEN)).isEqualTo(listOf(1, 3, 5))
    }

    @Test
    fun `parity composes with an explicit range for manual duplex`() {
        // "print pages 1-6, odd side first" then the same range, even.
        assertThat(resolve("1-6", 20, PageSelection.Parity.ODD)).isEqualTo(listOf(0, 2, 4))
    }

    @Test
    fun `an empty document yields nothing`() {
        assertThat(resolve("1-5", 0)).isEmpty()
    }

    @Test
    fun `malformed input is rejected with the offending term`() {
        listOf("abc", "1-2-3", "0", "1-0", "5-2", "-").forEach { spec ->
            val error = runCatching { PageRangeResolver.parse(spec) }.exceptionOrNull()
            assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Test
    fun `backwards ranges suggest the corrected form`() {
        val error = runCatching { PageRangeResolver.parse("9-4") }.exceptionOrNull()
        assertThat(error).hasMessageThat().contains("4-9")
    }

    @Test
    fun `parse produces the expected range objects`() {
        assertThat(PageRangeResolver.parse("2-4,7"))
            .isEqualTo(listOf(PageRange(2, 4), PageRange(7, 7)))
    }
}
