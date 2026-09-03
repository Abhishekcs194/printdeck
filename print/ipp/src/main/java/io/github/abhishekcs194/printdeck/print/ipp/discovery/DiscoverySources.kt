package io.github.abhishekcs194.printdeck.print.ipp.discovery

import kotlinx.coroutines.flow.Flow

/** Printers that announce themselves over mDNS. */
interface Announcements {
    fun discover(): Flow<PrinterEndpoint>
}

/** Where this device sits on the network. */
interface Topology {
    fun localAddresses(): List<CandidateSubnetPlanner.LocalAddress>

    /** Routers this device knows about, nearest first. */
    fun gateways(): List<String>

    fun observations(
        rememberedSubnets: List<Ipv4Subnet> = emptyList(),
    ): CandidateSubnetPlanner.Observations
}

/**
 * The network above this one, as the local router reports it.
 *
 * An observation rather than a guess: a router doing NAT knows its own address
 * on the network above, and asking it names that network exactly instead of
 * trying likely ranges and hoping.
 */
interface UpstreamGateway {
    suspend fun externalAddress(): String?
}
