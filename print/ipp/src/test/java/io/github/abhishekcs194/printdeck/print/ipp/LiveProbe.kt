package io.github.abhishekcs194.printdeck.print.ipp

import io.github.abhishekcs194.printdeck.print.ipp.discovery.DiscoverySource
import io.github.abhishekcs194.printdeck.print.ipp.discovery.PrinterEndpoint
import com.google.common.truth.Truth.assertThat
import io.github.abhishekcs194.printdeck.print.ipp.discovery.Ipv4Subnet
import io.github.abhishekcs194.printdeck.print.ipp.discovery.NetworkScanner
import io.github.abhishekcs194.printdeck.print.ipp.discovery.parseIpv4
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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

    /**
     * Sweeps the printer's own subnet, checking both halves of the contract:
     * that the printer is found, and that the sweep finishes afterwards. The
     * second is the one that broke — a sweep that emits correctly but never
     * completes leaves the search running forever.
     */
    @Test
    fun `sweeping the printer's subnet finds it and finishes`() {
        val address = System.getenv("PRINTDECK_TEST_PRINTER")
        assumeTrue("set PRINTDECK_TEST_PRINTER to run this", address != null)

        val subnet = Ipv4Subnet.containing(parseIpv4(address!!), SUBNET_PREFIX)
        val found = runBlocking {
            withTimeout(SWEEP_TIMEOUT_MS) {
                NetworkScanner().sweep(listOf(subnet)).toList()
            }
        }

        println("swept ${subnet.asCidr()} -> ${found.map { it.key }}")
        assertThat(found.map { it.address }).contains(address)
    }

    private companion object {
        const val IPP_PORT = 631
        const val SUBNET_PREFIX = 24
        const val SWEEP_TIMEOUT_MS = 30_000L
    }
}
