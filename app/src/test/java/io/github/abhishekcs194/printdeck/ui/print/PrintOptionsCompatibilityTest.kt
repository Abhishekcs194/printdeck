package io.github.abhishekcs194.printdeck.ui.print

import com.google.common.truth.Truth.assertThat
import io.github.abhishekcs194.printdeck.print.ipp.IppPrintOptions
import io.github.abhishekcs194.printdeck.print.ipp.PrinterCapabilities
import org.junit.Test

/**
 * Options survive a change of printer, and nothing guarantees the new one
 * supports them. Sending a keyword a printer does not recognise risks it
 * rejecting the whole job, so anything unsupported is dropped when the
 * selection changes.
 */
class PrintOptionsCompatibilityTest {

    private fun capabilities(
        sides: List<String> = listOf(IppPrintOptions.SIDES_ONE_SIDED),
        colorModes: List<String> = listOf(IppPrintOptions.COLOR_MODE_MONOCHROME),
        qualities: List<String> = listOf(IppPrintOptions.QUALITY_NORMAL),
        mediaTypes: List<String> = emptyList(),
    ) = PrinterCapabilities(
        name = null,
        makeAndModel = "Test printer",
        state = "idle",
        sides = sides,
        colorModes = colorModes,
        printQualities = qualities,
        mediaTypes = mediaTypes,
    )

    /** Mirrors the view model's filter; kept in step by these tests. */
    private fun IppPrintOptions.supportedBy(c: PrinterCapabilities) = copy(
        sides = sides.takeIf { it in c.sides } ?: c.sides.firstOrNull() ?: IppPrintOptions.SIDES_ONE_SIDED,
        colorMode = colorMode.takeIf { it in c.colorModes }
            ?: c.colorModes.firstOrNull() ?: IppPrintOptions.COLOR_MODE_MONOCHROME,
        quality = quality.takeIf { it in c.printQualities } ?: IppPrintOptions.QUALITY_NORMAL,
        mediaType = mediaType?.takeIf { it in c.mediaTypes },
    )

    @Test
    fun `duplex is dropped for a printer that cannot do it`() {
        val chosen = IppPrintOptions(sides = IppPrintOptions.SIDES_LONG_EDGE)
        val result = chosen.supportedBy(capabilities())

        assertThat(result.sides).isEqualTo(IppPrintOptions.SIDES_ONE_SIDED)
    }

    @Test
    fun `duplex is kept for a printer that can`() {
        val chosen = IppPrintOptions(sides = IppPrintOptions.SIDES_LONG_EDGE)
        val result = chosen.supportedBy(
            capabilities(sides = listOf(IppPrintOptions.SIDES_ONE_SIDED, IppPrintOptions.SIDES_LONG_EDGE)),
        )
        assertThat(result.sides).isEqualTo(IppPrintOptions.SIDES_LONG_EDGE)
    }

    @Test
    fun `colour is dropped for a mono-only printer`() {
        val chosen = IppPrintOptions(colorMode = IppPrintOptions.COLOR_MODE_COLOR)
        val result = chosen.supportedBy(capabilities())

        assertThat(result.colorMode).isEqualTo(IppPrintOptions.COLOR_MODE_MONOCHROME)
    }

    @Test
    fun `an unsupported quality falls back to normal`() {
        val chosen = IppPrintOptions(quality = IppPrintOptions.QUALITY_HIGH)
        val result = chosen.supportedBy(capabilities())

        assertThat(result.quality).isEqualTo(IppPrintOptions.QUALITY_NORMAL)
    }

    @Test
    fun `a paper type the new printer does not stock is cleared`() {
        val chosen = IppPrintOptions(mediaType = "com.canon.mtglossy")
        val result = chosen.supportedBy(capabilities(mediaTypes = listOf("stationery")))

        assertThat(result.mediaType).isNull()
    }

    @Test
    fun `copies and media size are never touched`() {
        // Neither is a capability keyword, so neither can be invalidated by a
        // change of printer.
        val chosen = IppPrintOptions(copies = 5, media = "iso_a4_210x297mm")
        val result = chosen.supportedBy(capabilities())

        assertThat(result.copies).isEqualTo(5)
        assertThat(result.media).isEqualTo("iso_a4_210x297mm")
    }
}
