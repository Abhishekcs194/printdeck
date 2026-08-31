package io.github.abhishekcs194.printdeck.print.ipp.discovery

import kotlinx.coroutines.flow.Flow

/**
 * The network operations discovery needs.
 *
 * An interface so the ring logic can be tested without a network. That logic has
 * now been the source of two bugs that no amount of testing the pure address
 * arithmetic could have caught — a sweep that never terminated, and a remembered
 * printer reported as found without ever being contacted. Both lived in the
 * orchestration rather than the maths.
 */
interface NetworkProbe {

    /** Is anything routing for this network? One connection, not a sweep. */
    suspend fun subnetExists(subnet: Ipv4Subnet): Boolean

    /**
     * Can this exact address and port be reached right now?
     *
     * Distinct from [subnetExists] and not interchangeable with it. A gateway
     * answering on a subnet says nothing about whether a particular printer is
     * still there, still on, or still holding that address.
     */
    suspend fun canReach(address: String, port: Int): Boolean

    /** Sweeps for anything listening on a printer port. */
    fun sweep(
        subnets: List<Ipv4Subnet>,
        ports: List<PrinterPort> = PrinterPort.sweepOrder,
    ): Flow<PrinterEndpoint>
}
