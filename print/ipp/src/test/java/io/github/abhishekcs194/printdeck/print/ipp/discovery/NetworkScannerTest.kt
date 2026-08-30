package io.github.abhishekcs194.printdeck.print.ipp.discovery

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test

/**
 * These are lifecycle tests, not networking tests.
 *
 * A sweep that emits correctly but never *completes* looks fine in isolation and
 * hangs the whole search, because the orchestrator waits for one subnet to
 * finish before starting the next. That failure is invisible to any test of the
 * pure planning logic, which is exactly how it reached a device.
 */
class NetworkScannerTest {

    /** Short timeouts: these addresses are expected to answer nothing. */
    private val scanner = NetworkScanner(connectTimeoutMs = 60, concurrency = 64)

    @Test
    fun `a sweep completes rather than running forever`() = runBlocking {
        // A /30 is two usable hosts, so this finishes quickly whatever the
        // network does. The timeout is the assertion: without it a hang would
        // present as a test that never returns rather than one that fails.
        val found = withTimeout(TIMEOUT_MS) {
            scanner.sweep(listOf(Ipv4Subnet.parse("10.255.255.252/30"))).toList()
        }
        assertThat(found).isEmpty()
    }

    @Test
    fun `sweeping several subnets still completes`() = runBlocking {
        val subnets = listOf(
            Ipv4Subnet.parse("10.255.255.248/30"),
            Ipv4Subnet.parse("10.255.255.252/30"),
        )
        val found = withTimeout(TIMEOUT_MS) { scanner.sweep(subnets).toList() }
        assertThat(found).isEmpty()
    }

    @Test
    fun `a sweep of nothing completes immediately`() = runBlocking {
        val found = withTimeout(TIMEOUT_MS) { scanner.sweep(emptyList()).toList() }
        assertThat(found).isEmpty()
    }

    @Test
    fun `public subnets are refused rather than swept`() = runBlocking {
        val found = withTimeout(TIMEOUT_MS) {
            scanner.sweep(listOf(Ipv4Subnet.parse("8.8.8.0/30"))).toList()
        }
        assertThat(found).isEmpty()
    }

    private companion object {
        const val TIMEOUT_MS = 15_000L
    }
}
