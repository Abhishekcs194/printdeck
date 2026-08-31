package io.github.abhishekcs194.printdeck.print.ipp.discovery

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Tests for the ring logic itself, with the network faked out.
 *
 * This is where the bugs have actually been. The address arithmetic was tested
 * exhaustively from the start and has never been wrong; the orchestration around
 * it shipped a sweep that never terminated, and a remembered printer reported as
 * found without ever being contacted. Neither was reachable by testing pure
 * functions, which is why these exist.
 */
class PrinterDiscoveryTest {

    /** Records what was asked of the network, and answers from a fixed script. */
    private class FakeProbe(
        val reachable: Set<String> = emptySet(),
        val existingSubnets: Set<String> = emptySet(),
        val sweepResults: Map<String, List<PrinterEndpoint>> = emptyMap(),
    ) : NetworkProbe {
        val reachChecks = mutableListOf<String>()
        val subnetsSwept = mutableListOf<String>()

        override suspend fun subnetExists(subnet: Ipv4Subnet): Boolean =
            subnet.asCidr() in existingSubnets

        override suspend fun canReach(address: String, port: Int): Boolean {
            reachChecks += "$address:$port"
            return "$address:$port" in reachable
        }

        override fun sweep(subnets: List<Ipv4Subnet>, ports: List<PrinterPort>): Flow<PrinterEndpoint> {
            subnets.forEach { subnetsSwept += it.asCidr() }
            return flowOf(*subnets.flatMap { sweepResults[it.asCidr()].orEmpty() }.toTypedArray())
        }
    }

    private object NoAnnouncements : Announcements {
        override fun discover(): Flow<PrinterEndpoint> = emptyFlow()
    }

    /** A router that declines to say what is above it, which many do. */
    private class FakeUpstream(private val address: String? = null) : UpstreamGateway {
        override suspend fun externalAddress(): String? = address
    }

    private class FakeTopology(private val address: String) : Topology {
        override fun localAddresses() =
            listOf(CandidateSubnetPlanner.LocalAddress(address, PREFIX))

        override fun observations(rememberedSubnets: List<Ipv4Subnet>) =
            CandidateSubnetPlanner.Observations(
                localAddresses = localAddresses(),
                // Every device on a network has a default route; a fake that
                // pretends otherwise tests a situation that does not occur.
                gateways = listOf(formatIpv4(
                    Ipv4Subnet.containing(parseIpv4(address), PREFIX).networkAddress + 1,
                )),
                rememberedSubnets = rememberedSubnets,
            )
    }

    private fun endpoint(address: String) = PrinterEndpoint(address, IPP_PORT, DiscoverySource.SCAN)

    private fun discover(
        probe: FakeProbe,
        localAddress: String,
        remembered: List<PrinterEndpoint> = emptyList(),
        upstream: String? = null,
    ) = runBlocking {
        PrinterDiscovery(NoAnnouncements, probe, FakeTopology(localAddress), FakeUpstream(upstream))
            .discover(remembered = remembered)
            .toList()
    }

    @Test
    fun `a remembered printer is contacted, not assumed from its subnet`() {
        // The bug this replaces: the subnet's gateway was probed instead of the
        // printer. A router answers on its own subnet from neighbouring networks
        // too, so a phone that had moved recorded a printer it could not reach.
        val probe = FakeProbe(
            reachable = emptySet(), // the printer is NOT reachable from here
            existingSubnets = setOf("192.168.101.0/24"), // but its gateway answers
        )
        discover(probe, localAddress = "192.168.100.50", remembered = listOf(endpoint("192.168.101.16")))

        assertThat(probe.reachChecks).contains("192.168.101.16:631")
    }

    @Test
    fun `an unreachable remembered printer is not reported as found`() {
        val probe = FakeProbe(existingSubnets = setOf("192.168.101.0/24"))
        val progress = discover(
            probe,
            localAddress = "192.168.100.50",
            remembered = listOf(endpoint("192.168.101.16")),
        )
        assertThat(progress.last().printers).isEmpty()
    }

    @Test
    fun `an unreachable remembered printer does not suppress the wider search`() {
        // The compounding half of the bug: something had been "found", so the
        // search stopped before looking anywhere the printer might have moved to.
        val probe = FakeProbe(existingSubnets = setOf("192.168.101.0/24"))
        discover(probe, localAddress = "192.168.100.50", remembered = listOf(endpoint("192.168.101.16")))

        assertThat(probe.subnetsSwept).contains("192.168.101.0/24")
    }

    @Test
    fun `a reachable remembered printer is reported without a wider search`() {
        val probe = FakeProbe(reachable = setOf("192.168.101.16:631"))
        val progress = discover(
            probe,
            localAddress = "192.168.101.50",
            remembered = listOf(endpoint("192.168.101.16")),
        )

        assertThat(progress.last().printers.map { it.address }).contains("192.168.101.16")
        // Nothing beyond the attached network needed searching.
        assertThat(probe.subnetsSwept).doesNotContain("192.168.0.0/24")
    }

    @Test
    fun `a printer found by sweeping the attached network is reported`() {
        val probe = FakeProbe(
            sweepResults = mapOf("192.168.101.0/24" to listOf(endpoint("192.168.101.16"))),
        )
        val progress = discover(probe, localAddress = "192.168.101.50")

        assertThat(progress.last().printers.map { it.address }).containsExactly("192.168.101.16")
    }

    @Test
    fun `a remembered network is swept even when its router stays silent`() {
        // The reported case: the phone moves to another band, the printer's
        // network has no router answering a probe from here, and the sweep that
        // would have found it was skipped. A network we have reason to believe
        // in is now searched regardless.
        val probe = FakeProbe(
            existingSubnets = emptySet(), // nothing answers a router probe anywhere
            sweepResults = mapOf("192.168.101.0/24" to listOf(endpoint("192.168.101.16"))),
        )
        val progress = discover(
            probe,
            localAddress = "192.168.100.50",
            remembered = listOf(endpoint("192.168.101.16")),
        )

        assertThat(probe.subnetsSwept).contains("192.168.101.0/24")
        assertThat(progress.last().printers.map { it.address }).contains("192.168.101.16")
    }

    @Test
    fun `the network beside the router is searched without needing a reply`() {
        // A phone on 192.168.100.x with a second network on 101.x: that
        // neighbour is the single most likely place for the printer to be.
        val probe = FakeProbe(
            sweepResults = mapOf("192.168.101.0/24" to listOf(endpoint("192.168.101.16"))),
        )
        val progress = discover(probe, localAddress = "192.168.100.50")

        assertThat(progress.last().printers.map { it.address }).contains("192.168.101.16")
    }

    @Test
    fun `the network the router names upstream is searched`() {
        // Not a guess: the router was asked and answered. This is what replaces
        // trying likely ranges in the hope that one of them exists.
        val probe = FakeProbe(
            sweepResults = mapOf("10.20.30.0/24" to listOf(endpoint("10.20.30.40"))),
        )
        val progress = discover(
            probe,
            localAddress = "192.168.1.50",
            upstream = "10.20.30.1",
        )

        assertThat(probe.subnetsSwept).contains("10.20.30.0/24")
        assertThat(progress.last().printers.map { it.address }).contains("10.20.30.40")
    }

    @Test
    fun `a public upstream address is ignored`() {
        // A router facing the internet reports a public address. Sweeping that
        // would be scanning somebody else's network.
        val probe = FakeProbe()
        discover(probe, localAddress = "192.168.1.50", upstream = "81.2.69.142")

        assertThat(probe.subnetsSwept.none { it.startsWith("81.") }).isTrue()
    }

    @Test
    fun `discovery always finishes`() {
        val progress = discover(FakeProbe(), localAddress = "192.168.101.50")
        assertThat(progress.last().phase).isEqualTo(PrinterDiscovery.Phase.FINISHED)
    }

    @Test
    fun `finishing with nothing found carries a diagnosis`() {
        val progress = discover(FakeProbe(), localAddress = "192.168.101.50")
        assertThat(progress.last().diagnosis).isNotNull()
    }

    private companion object {
        const val IPP_PORT = 631
        const val PREFIX = 24
    }
}
