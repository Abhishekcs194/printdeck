package io.github.abhishekcs194.printdeck.print.ipp.discovery

import android.content.Context
import android.net.ConnectivityManager
import androidx.core.content.getSystemService
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Works out where this device actually sits, which is the input the sweep plan
 * is built from.
 *
 * Two sources, because neither alone is enough. [NetworkInterface] sees every
 * interface — Wi-Fi, USB tethering, VPN — including ones the connectivity
 * service does not report as the active network. [ConnectivityManager] knows the
 * routing table, and therefore the gateways, which [NetworkInterface] cannot
 * see.
 */
class NetworkTopology(private val context: Context) {

    /** Every IPv4 address this device holds, with its lease's prefix length. */
    fun localAddresses(): List<CandidateSubnetPlanner.LocalAddress> =
        runCatching {
            NetworkInterface.getNetworkInterfaces()
                ?.asSequence()
                ?.filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
                ?.flatMap { it.interfaceAddresses.asSequence() }
                ?.mapNotNull { interfaceAddress ->
                    val address = interfaceAddress.address as? Inet4Address ?: return@mapNotNull null
                    val text = address.hostAddress ?: return@mapNotNull null
                    if (!PrivateAddressGuard.isAllowed(text)) return@mapNotNull null
                    CandidateSubnetPlanner.LocalAddress(
                        address = parseIpv4(text),
                        prefixLength = interfaceAddress.networkPrefixLength.toInt(),
                    )
                }
                ?.distinct()
                ?.toList()
                .orEmpty()
        }.getOrDefault(emptyList())

    /**
     * Routers this device knows about: real next hops from the routing table,
     * plus the conventional router address of each network it is on.
     *
     * The second part matters more than it looks. A device is often handed a
     * default route and nothing else, so the routing table alone reveals just one
     * gateway — while the address that betrays a *second* network is usually
     * sitting at the `.1` of a range nobody advertised.
     */
    fun gateways(): List<String> {
        val fromRoutes = runCatching {
            val connectivity = context.getSystemService<ConnectivityManager>()
            val network = connectivity?.activeNetwork
            connectivity?.getLinkProperties(network)
                ?.routes
                ?.mapNotNull { it.gateway }
                ?.filterIsInstance<Inet4Address>()
                ?.mapNotNull { it.hostAddress }
                ?.filter(PrivateAddressGuard::isAllowed)
                .orEmpty()
        }.getOrDefault(emptyList())

        val conventional = localAddresses().map { local ->
            formatIpv4(Ipv4Subnet.containing(local.address, MIN_ROUTER_PREFIX).networkAddress + 1)
        }

        return (fromRoutes + conventional).distinct()
    }

    fun observations(
        rememberedSubnets: List<Ipv4Subnet> = emptyList(),
    ): CandidateSubnetPlanner.Observations = CandidateSubnetPlanner.Observations(
        localAddresses = localAddresses(),
        gateways = gateways(),
        rememberedSubnets = rememberedSubnets,
    )

    private companion object {
        const val MIN_ROUTER_PREFIX = 24
    }
}
