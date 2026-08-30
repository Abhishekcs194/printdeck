package io.github.abhishekcs194.printdeck.print.ipp

import io.github.abhishekcs194.printdeck.print.ipp.discovery.DiscoverySource
import io.github.abhishekcs194.printdeck.print.ipp.discovery.PrinterEndpoint
import kotlinx.coroutines.runBlocking
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

    private companion object {
        const val IPP_PORT = 631
    }
}
