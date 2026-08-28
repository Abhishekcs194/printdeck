package io.github.abhishekcs194.printdeck.print.ipp.discovery

import com.google.common.truth.Truth.assertThat
import io.github.abhishekcs194.printdeck.print.ipp.discovery.CandidateSubnetPlanner.Depth
import io.github.abhishekcs194.printdeck.print.ipp.discovery.CandidateSubnetPlanner.Observations
import org.junit.Test

/**
 * These cases are modelled on a real household that reproduces the problem this
 * app exists to solve: a main router on 192.168.100.x with a second router on
 * 192.168.101.x behind it, plus laptops that share their connection and create a
 * third range. A printer routinely ends up on a segment the phone cannot see by
 * announcement, and moves between them whenever the power blinks.
 */
class CandidateSubnetPlannerTest {

    private fun cidrs(subnets: List<Ipv4Subnet>) = subnets.map { it.asCidr() }

    @Test
    fun `local depth stays on networks the device is actually attached to`() {
        val plan = CandidateSubnetPlanner.plan(
            Observations(
                localAddresses = listOf(CandidateSubnetPlanner.LocalAddress("192.168.100.248", 24)),
                gateways = listOf("192.168.100.1"),
            ),
            Depth.LOCAL,
        )
        assertThat(cidrs(plan)).containsExactly("192.168.100.0/24")
    }

    @Test
    fun `remembered subnets come first, because they nearly always still hold`() {
        val plan = CandidateSubnetPlanner.plan(
            Observations(
                localAddresses = listOf(CandidateSubnetPlanner.LocalAddress("10.42.0.37", 24)),
                rememberedSubnets = listOf(Ipv4Subnet.parse("192.168.101.0/24")),
            ),
            Depth.LOCAL,
        )
        assertThat(cidrs(plan).first()).isEqualTo("192.168.101.0/24")
    }

    @Test
    fun `wide depth reaches the network behind a second router`() {
        // The phone is on a laptop's shared connection; the printer is two hops
        // away on the extender's range. No amount of mDNS will ever find it.
        val plan = CandidateSubnetPlanner.plan(
            Observations(
                localAddresses = listOf(CandidateSubnetPlanner.LocalAddress("10.42.0.37", 24)),
                gateways = listOf("10.42.0.1", "192.168.101.1"),
            ),
            Depth.WIDE,
        )
        assertThat(cidrs(plan)).contains("192.168.101.0/24")
    }

    @Test
    fun `wide depth guesses the ranges either side of a visible router`() {
        // A gateway at 192.168.101.1 is itself very often a client of 100.x.
        val plan = CandidateSubnetPlanner.plan(
            Observations(gateways = listOf("192.168.101.1")),
            Depth.WIDE,
        )
        assertThat(cidrs(plan)).containsAtLeast("192.168.100.0/24", "192.168.102.0/24")
    }

    @Test
    fun `wide depth includes the ranges consumer gear ships with`() {
        val plan = CandidateSubnetPlanner.plan(Observations(), Depth.WIDE)
        assertThat(cidrs(plan)).containsAtLeast(
            "192.168.0.0/24",
            "192.168.1.0/24",
            "192.168.8.0/24", // Huawei CPE
        )
    }

    @Test
    fun `nothing outside private space is ever planned`() {
        val plan = CandidateSubnetPlanner.plan(
            Observations(
                localAddresses = listOf(CandidateSubnetPlanner.LocalAddress("8.8.8.8", 24)),
                gateways = listOf("8.8.8.8", "197.226.230.69"),
            ),
            Depth.WIDE,
        )
        assertThat(plan.all(PrivateAddressGuard::isScannable)).isTrue()
        assertThat(cidrs(plan)).doesNotContain("8.8.8.0/24")
    }

    @Test
    fun `large leases are clamped to a 24 rather than swept whole`() {
        // A /16 is 65 534 probes; sweeping it would never finish acceptably.
        val plan = CandidateSubnetPlanner.plan(
            Observations(localAddresses = listOf(CandidateSubnetPlanner.LocalAddress("192.168.77.20", 16))),
            Depth.LOCAL,
        )
        assertThat(cidrs(plan)).containsExactly("192.168.77.0/24")
        assertThat(plan.single().hostCount).isEqualTo(254)
    }

    @Test
    fun `the plan is capped so a search stays responsive`() {
        val plan = CandidateSubnetPlanner.plan(
            Observations(gateways = listOf("192.168.101.1", "10.42.0.1", "172.16.5.1")),
            Depth.WIDE,
            maxSubnets = 6,
        )
        assertThat(plan).hasSize(6)
    }

    @Test
    fun `duplicates collapse so no network is swept twice`() {
        val plan = CandidateSubnetPlanner.plan(
            Observations(
                localAddresses = listOf(CandidateSubnetPlanner.LocalAddress("192.168.1.50", 24)),
                gateways = listOf("192.168.1.1"),
                rememberedSubnets = listOf(Ipv4Subnet.parse("192.168.1.0/24")),
            ),
            Depth.WIDE,
        )
        assertThat(plan).containsNoDuplicates()
    }

    @Test
    fun `an empty observation still produces a usable wide plan`() {
        // Worst case: no interfaces readable, no gateway. Guessing beats giving up.
        assertThat(CandidateSubnetPlanner.plan(Observations(), Depth.WIDE)).isNotEmpty()
    }
}
