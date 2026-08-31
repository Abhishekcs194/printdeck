package io.github.abhishekcs194.printdeck.print.ipp.discovery

import kotlinx.coroutines.flow.Flow

/** Printers that announce themselves over mDNS. */
interface Announcements {
    fun discover(): Flow<PrinterEndpoint>
}

/** Where this device sits on the network. */
interface Topology {
    fun localAddresses(): List<CandidateSubnetPlanner.LocalAddress>

    fun observations(
        rememberedSubnets: List<Ipv4Subnet> = emptyList(),
    ): CandidateSubnetPlanner.Observations
}
