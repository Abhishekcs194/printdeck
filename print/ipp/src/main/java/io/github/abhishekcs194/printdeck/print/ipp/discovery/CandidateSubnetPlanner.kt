package io.github.abhishekcs194.printdeck.print.ipp.discovery

/**
 * Decides which networks to sweep, and in what order.
 *
 * This is the part that answers "the printer is on the network, so why can't my
 * phone see it?". mDNS is link-local by design: it cannot cross a router, so the
 * moment a phone and a printer sit on different subnets - an extender in NAT
 * mode, a laptop sharing its connection, a mesh node handing out its own range -
 * announcement-based discovery is structurally incapable of finding it, no
 * matter how long it waits.
 *
 * Sweeping works there, because the intervening router still forwards unicast.
 * The problem becomes choosing a small set of networks worth sweeping, since the
 * private address space is far too large to search blindly.
 *
 * Pure and Android-free: given observations about the network, it returns a
 * plan. That makes the ordering and the caps directly testable.
 */
object CandidateSubnetPlanner {

    /**
     * An address this device actually holds, with the prefix length its lease
     * came with.
     *
     * The address itself is kept, not just the derived subnet: a /16 lease has
     * to be narrowed to a sweepable /24, and that /24 must be the one *around
     * the device*. Narrowing from the network address instead would sweep
     * 192.168.0.x while the device sat on 192.168.77.x.
     */
    data class LocalAddress(val address: Long, val prefixLength: Int) {
        constructor(address: String, prefixLength: Int) : this(parseIpv4(address), prefixLength)

        /** The largest sweepable network around this address. */
        fun sweepableSubnet(): Ipv4Subnet =
            Ipv4Subnet.containing(address, maxOf(prefixLength, MIN_SWEEPABLE_PREFIX))
    }

    /** What the device can observe about where it is. */
    data class Observations(
        /** Addresses this device holds, from its own interfaces. */
        val localAddresses: List<LocalAddress> = emptyList(),
        /** Default gateways and any routers seen upstream (TTL probing, route table). */
        val gateways: List<String> = emptyList(),
        /** Subnets where a printer was found before. Cheap and very likely to hit again. */
        val rememberedSubnets: List<Ipv4Subnet> = emptyList(),
    )

    /**
     * How much reason there is to believe a network exists.
     *
     * The distinction decides whether a network is swept outright or has to
     * prove itself first. Probing a router before spending 254 connections on
     * its subnet is a sound optimisation for guesses, and a bad gate on good
     * candidates: a router that answers ICMP but not HTTP, or answers nothing
     * from a neighbouring subnet, would veto the sweep of the very network the
     * printer was last found on.
     */
    enum class Confidence {
        /** Attached, remembered, or derived from a router this device can see. */
        HIGH,

        /** A guess from the list of ranges consumer gear ships with. */
        SPECULATIVE,
    }

    data class Candidate(val subnet: Ipv4Subnet, val confidence: Confidence)

    enum class Depth {
        /**
         * Only what is certainly nearby: remembered subnets and directly attached
         * ones. Fast enough to run on every launch.
         */
        LOCAL,

        /**
         * Adds routers we can see upstream, their neighbouring ranges, and the
         * handful of ranges consumer gear actually ships with. This is what finds
         * a printer stranded behind a second NAT.
         */
        WIDE,
    }

    /**
     * Ranges that consumer routers, extenders and phone/laptop hotspots actually
     * hand out. Not a scan of RFC 1918 - that is 17.9 million addresses - but the
     * short list that covers almost every household.
     */
    private val commonHomeSubnets = listOf(
        "192.168.0.0/24", "192.168.1.0/24", "192.168.2.0/24",
        "192.168.8.0/24", // Huawei / many LTE CPE
        "192.168.10.0/24", "192.168.100.0/24", "192.168.101.0/24",
        "192.168.3.0/24", "192.168.4.0/24",
        "192.168.43.0/24", // Android hotspot
        "172.20.10.0/24", // iPhone hotspot
        "10.42.0.0/24", // GNOME / NetworkManager connection sharing
        "10.0.0.0/24", "10.0.1.0/24",
    ).map(Ipv4Subnet::parse)

    /**
     * Ranges to try around a router we can see. A gateway at 192.168.101.1 is
     * very often itself a client of 192.168.100.x or 192.168.1.x, so its
     * neighbours are much better guesses than the generic list.
     */
    private fun neighboursOf(gateway: String): List<Ipv4Subnet> {
        val address = runCatching { parseIpv4(gateway) }.getOrNull() ?: return emptyList()
        val thirdOctet = (address shr OCTET_BITS) and OCTET_MASK
        val classBBase = address and CLASS_B_MASK

        // The adjacent third octets, plus the two bases nearly every consumer
        // router defaults to.
        return listOf(thirdOctet - 1, thirdOctet + 1, thirdOctet - 2, thirdOctet + 2, 0L, 1L)
            .filter { it in 0..MAX_OCTET }
            .distinct()
            .map { octet -> Ipv4Subnet(classBBase or (octet shl OCTET_BITS), MIN_SWEEPABLE_PREFIX) }
    }

    /**
     * @param maxSubnets hard cap on the plan. Each /24 costs roughly a second to
     *   sweep, so this is the difference between a search that feels responsive
     *   and one the user abandons.
     */
    fun plan(
        observations: Observations,
        depth: Depth,
        maxSubnets: Int = if (depth == Depth.LOCAL) MAX_LOCAL_SUBNETS else MAX_WIDE_SUBNETS,
    ): List<Candidate> {
        val ordered = buildList {
            // 1. Where it was last time. Almost always still true, and nearly free.
            observations.rememberedSubnets.forEach { add(it to Confidence.HIGH) }

            // 2. Networks this device is actually on.
            observations.localAddresses.forEach { add(it.sweepableSubnet() to Confidence.HIGH) }

            if (depth == Depth.WIDE) {
                // 3. The subnet each visible router lives on. An upstream hop at
                //    192.168.101.1 means a whole network exists there that
                //    announcement-based discovery can never reach.
                observations.gateways.forEach { gateway ->
                    runCatching { Ipv4Subnet.containing(parseIpv4(gateway), MIN_SWEEPABLE_PREFIX) }
                        .getOrNull()?.let { add(it to Confidence.HIGH) }
                }
                // 4. Ranges adjacent to those routers. Still high confidence: a
                //    router at .101.1 is very often itself a client of .100.x,
                //    and that neighbour is where a second network usually lives.
                observations.gateways.forEach { gateway ->
                    neighboursOf(gateway).forEach { add(it to Confidence.HIGH) }
                }
                // 5. The standard consumer ranges. Guesses, and gated as such.
                commonHomeSubnets.forEach { add(it to Confidence.SPECULATIVE) }
            }
        }

        return ordered
            .asSequence()
            // Never sweep anything that is not unambiguously private.
            .filter { (subnet, _) -> PrivateAddressGuard.isScannable(subnet) }
            // A /16 would be 65 534 probes; only /24-or-smaller is sweepable.
            .filter { (subnet, _) -> subnet.prefixLength >= MIN_SWEEPABLE_PREFIX }
            .distinctBy { (subnet, _) -> subnet }
            .take(maxSubnets)
            .map { (subnet, confidence) -> Candidate(subnet, confidence) }
            .toList()
    }

    /**
     * The largest network worth sweeping. A /24 is 254 probes and finishes in
     * about a second; a /23 doubles that, and a /16 would never finish.
     */
    const val MIN_SWEEPABLE_PREFIX = 24

    /** Attached networks are few, and searching them must feel instant. */
    private const val MAX_LOCAL_SUBNETS = 4

    /**
     * Roughly fifteen seconds of searching in the worst case, and far less in
     * practice because networks that do not answer at their router address are
     * skipped without being swept.
     */
    private const val MAX_WIDE_SUBNETS = 20

    // IPv4 octet arithmetic.
    private const val OCTET_BITS = 8
    private const val OCTET_MASK = 0xFFL
    private const val MAX_OCTET = 255L
    private const val CLASS_B_MASK = 0xFFFF0000L
}
