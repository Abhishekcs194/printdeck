package io.github.abhishekcs194.printdeck.print.ipp

import io.github.abhishekcs194.printdeck.print.ipp.discovery.DiscoverySource
import io.github.abhishekcs194.printdeck.print.ipp.discovery.PrinterEndpoint
import kotlinx.coroutines.runBlocking
import kotlin.io.path.createTempDirectory
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Talks to a real printer, to check the IPP client against hardware rather than
 * against assumptions.
 *
 * Opt-in only: it does nothing unless `PRINTDECK_TEST_PRINTER` names an address.
 * A test that reaches the network by default is one that fails for reasons
 * unrelated to the code, on someone else's machine, at the worst moment.
 *
 *     PRINTDECK_TEST_PRINTER=192.168.1.50 ./gradlew :print:ipp:testDebugUnitTest --tests '*LiveProbe*' -i
 */
class LiveProbe {

    @Test
    fun `reports what the printer can do`() {
        val address = System.getenv("PRINTDECK_TEST_PRINTER")
        assumeTrue("set PRINTDECK_TEST_PRINTER to run this", address != null)

        val result = runBlocking {
            IppClient().query(PrinterEndpoint(address!!, IPP_PORT, DiscoverySource.MANUAL))
        }
        val capabilities = result.getOrThrow()

        println("model      = ${capabilities.makeAndModel}")
        println("state      = ${capabilities.state}")
        println("formats    = ${capabilities.documentFormats}")
        println("sides      = ${capabilities.sides}")
        println("qualities  = ${capabilities.printQualities}")
        println("mediaTypes = ${capabilities.mediaTypes}")
        println("supplies   = ${capabilities.supplies}")
    }

    /**
     * Checks that a job built from these options would be accepted, without
     * printing anything. Validate-Job is the only way to test the request
     * construction against real hardware without spending paper on it.
     */
    @Test
    fun `printer accepts a job built from our options`() {
        val address = System.getenv("PRINTDECK_TEST_PRINTER")
        assumeTrue("set PRINTDECK_TEST_PRINTER to run this", address != null)

        val endpoint = PrinterEndpoint(address!!, IPP_PORT, DiscoverySource.MANUAL)
        val printer = IppPrinter(workingDirectory = createTempDirectory().toFile())

        val combinations = listOf(
            "mono, one-sided" to IppPrintOptions(),
            "mono, duplex long edge" to IppPrintOptions(
                sides = IppPrintOptions.SIDES_LONG_EDGE,
            ),
            "colour, 2 copies, A4" to IppPrintOptions(
                colorMode = IppPrintOptions.COLOR_MODE_COLOR,
                copies = 2,
                media = "iso_a4_210x297mm",
            ),
        )

        combinations.forEach { (label, options) ->
            val accepted = runBlocking {
                printer.validate(endpoint, "PrintDeck validation", options, "image/pwg-raster")
            }
            println("validate [$label] -> $accepted")
        }
    }

    private companion object {
        const val IPP_PORT = 631
    }
}
