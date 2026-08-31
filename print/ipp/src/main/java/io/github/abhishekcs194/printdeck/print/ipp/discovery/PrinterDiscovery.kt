package io.github.abhishekcs194.printdeck.print.ipp.discovery

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Finds printers, in the order most likely to succeed soonest.
 *
 * The premise is that "the printer is on the network but my phone cannot see it"
 * is the normal case, not an edge case. Households run extenders in NAT mode,
 * mesh nodes that hand out their own range, and laptops sharing a connection;
 * any of those puts the printer one router away, where announcement-based
 * discovery is structurally blind. Printers also move: a DHCP lease with no
 * reservation changes address every time the power blinks.
 *
 * So discovery runs in widening rings:
 *
 *  1. **Remembered** — re-probe known addresses. Usually instant, and it is what
 *     makes the second use of a printer feel immediate.
 *  2. **Local** — mDNS and a sweep of attached networks, together. Announcement
 *     is faster and richer when it works; the sweep covers it when it does not.
 *  3. **Wide** — networks reachable through a router but not attached here. This
 *     is the ring that finds the stranded printer, and it escalates on its own
 *     rather than waiting to be asked, because a user who cannot find their
 *     printer has no way to know that a wider search is the thing they need.
 *
 * Every ring narrows the next: a subnet that yielded a printer before is
 * remembered and searched first, so the expensive path is paid once.
 */
class PrinterDiscovery(
    private val mdns: Announcements,
    private val scanner: NetworkProbe,
    private val topology: Topology,
) {

    enum class Phase {
        /** Re-checking addresses that worked before. */
        CHECKING_KNOWN,

        /** Listening for announcements and sweeping attached networks. */
        SEARCHING_NEARBY,

        /** Looking on networks reachable through a router. */
        SEARCHING_WIDER,

        FINISHED,
    }

    data class Progress(
        val phase: Phase,
        val printers: List<PrinterEndpoint> = emptyList(),
        val networksSearched: Int = 0,
        val networksTotal: Int = 0,
        /** Populated once the search finishes. Explains an empty result. */
        val diagnosis: DiscoveryDiagnosis? = null,
    ) {
        val found: Boolean get() = printers.isNotEmpty()
    }

    /**
     * @param escalate when true, a fruitless nearby search widens automatically.
     *   Set false to drive the wider search from an explicit user action instead.
     */
    fun discover(
        remembered: List<PrinterEndpoint> = emptyList(),
        rememberedSubnets: List<Ipv4Subnet> = emptyList(),
        escalate: Boolean = true,
    ): Flow<Progress> = channelFlow {
        // A remembered printer implies its network. Deriving that here rather
        // than making the caller pass both removes a way for the two to disagree
        // - and a subnet that goes missing is a subnet that never gets searched.
        val impliedSubnets = remembered.mapNotNull { printer ->
            runCatching {
                Ipv4Subnet.containing(parseIpv4(printer.address), SWEEP_PREFIX)
            }.getOrNull()
        }
        val allRememberedSubnets = (rememberedSubnets + impliedSubnets).distinct()

        val found = LinkedHashMap<String, PrinterEndpoint>()

        // Routers answering on networks this device is not part of. The one
        // piece of hard evidence that a second network exists, which a sleeping
        // printer or a weak signal cannot fake.
        val foreignRouters = mutableListOf<String>()

        suspend fun publish(
            phase: Phase,
            searched: Int = 0,
            total: Int = 0,
            diagnosis: DiscoveryDiagnosis? = null,
        ) {
            send(Progress(phase, found.values.toList(), searched, total, diagnosis))
        }

        /**
         * Records a find, keyed by address rather than by address and port.
         *
         * One printer usually answers on several ports at once — IPP on 631, LPD
         * on 515, raw on 9100 are all the same machine. Keying by port would list
         * it three times and spend three identification requests on it, two of
         * which cannot succeed because those ports do not speak IPP.
         *
         * Where the same address turns up more than once, IPP wins: it is the
         * only one of them that can report what the printer can do. Beyond that,
         * mDNS knows a printer's name and resource path while a sweep only knows
         * a port is open, so an announcement may enrich an earlier scan hit but
         * never replace richer detail with poorer.
         */
        fun record(endpoint: PrinterEndpoint) {
            val existing = found[endpoint.address]
            found[endpoint.address] = when {
                existing == null -> endpoint
                endpoint.speaksIpp && !existing.speaksIpp -> endpoint
                existing.name == null && endpoint.name != null -> endpoint
                else -> existing
            }
        }

        // --- Ring 1: addresses that worked before -----------------------------
        publish(Phase.CHECKING_KNOWN)
        remembered.forEach { printer ->
            // The printer itself is contacted, not merely its network.
            //
            // Checking whether the subnet exists was wrong, and wrong in a way
            // that hid itself: a router answers on its own subnet from
            // neighbouring networks too, so a phone that had moved would record
            // a printer it could not reach, report it as found, and - because
            // something had been "found" - skip the wider search that would
            // have located it properly.
            if (scanner.canReach(printer.address, printer.port)) {
                record(printer.copy(source = DiscoverySource.REMEMBERED))
            }
        }
        publish(Phase.CHECKING_KNOWN)

        // --- Ring 2: announcements and attached networks, concurrently --------
        val nearby = CandidateSubnetPlanner.plan(
            topology.observations(allRememberedSubnets),
            CandidateSubnetPlanner.Depth.LOCAL,
        )

        // mDNS runs for the whole nearby phase rather than being awaited: it
        // either answers quickly or it is never going to.
        val announcements = launch {
            withTimeoutOrNull(MDNS_LISTEN_MS) {
                mdns.discover().collectLatest { record(it); publish(Phase.SEARCHING_NEARBY) }
            }
        }

        nearby.forEachIndexed { index, candidate ->
            scanner.sweep(listOf(candidate.subnet)).collectLatest { record(it) }
            publish(Phase.SEARCHING_NEARBY, index + 1, nearby.size)
        }
        announcements.join()
        publish(Phase.SEARCHING_NEARBY, nearby.size, nearby.size)

        // --- Ring 3: through the router ---------------------------------------
        // Runs whenever nearby found nothing. When escalation is off the probe
        // still happens but the sweep does not: probing is cheap, and without it
        // there is no evidence to explain the failure with, which would leave the
        // user staring at a bare "no printers found".
        if (found.isEmpty()) {
            val wider = CandidateSubnetPlanner.plan(
                topology.observations(allRememberedSubnets),
                CandidateSubnetPlanner.Depth.WIDE,
            ).filterNot { candidate -> nearby.any { it.subnet == candidate.subnet } }

            publish(Phase.SEARCHING_WIDER, 0, wider.size)
            wider.forEachIndexed { index, candidate ->
                if (shouldSweep(candidate)) {
                    foreignRouters += formatIpv4(candidate.subnet.networkAddress + 1)
                    if (escalate) {
                        scanner.sweep(listOf(candidate.subnet)).collectLatest { record(it) }
                    }
                }
                publish(Phase.SEARCHING_WIDER, index + 1, wider.size)
            }
        }

        publish(Phase.FINISHED, diagnosis = diagnose(found.size, foreignRouters))
    }

    /**
     * Explains an empty result.
     *
     * Only routers on networks this device is genuinely not part of count as
     * evidence of a second network — one on our own subnet explains nothing.
     */
    private fun diagnose(printersFound: Int, foreignRouters: List<String>): DiscoveryDiagnosis {
        val localSubnets = topology.localAddresses().map { it.sweepableSubnet() }
        return DiscoveryDiagnostics.diagnose(
            DiscoveryDiagnostics.Evidence(
                hasNetwork = localSubnets.isNotEmpty(),
                localSubnets = localSubnets,
                foreignRoutersReachable = foreignRouters.filterNot { router ->
                    localSubnets.any { parseIpv4(router) in it }
                },
                printersFound = printersFound,
            ),
        )
    }

    /**
     * A network we have reason to believe in is swept outright.
     *
     * Requiring it to answer a router probe first is a sound optimisation for a
     * guess and a bad gate on a good candidate: a router that replies to ICMP
     * but not HTTP, or does not reply across a subnet boundary at all, would
     * veto the sweep of the very network the printer was last seen on.
     */
    private suspend fun shouldSweep(candidate: CandidateSubnetPlanner.Candidate): Boolean =
        candidate.confidence == CandidateSubnetPlanner.Confidence.HIGH ||
            scanner.subnetExists(candidate.subnet)

    private companion object {
        const val SWEEP_PREFIX = 24

        /**
         * Printers answer mDNS almost immediately or not at all; listening longer
         * only delays the sweep that was always going to be the thing that found
         * them.
         */
        const val MDNS_LISTEN_MS = 2_500L
    }
}
