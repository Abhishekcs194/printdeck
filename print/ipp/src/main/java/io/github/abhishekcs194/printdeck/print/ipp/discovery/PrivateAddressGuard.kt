package io.github.abhishekcs194.printdeck.print.ipp.discovery

/**
 * Refuses to let PrintDeck touch anything outside a private network.
 *
 * The app holds INTERNET solely to reach a printer, and discovery sweeps
 * address ranges — which is exactly the shape of behaviour that must never be
 * pointable at the public internet, whether by a malformed manual entry, a
 * hostile DHCP lease, or a bug in subnet planning. Every socket in this module
 * goes through here first.
 *
 * Pure and Android-free so the ranges can be tested directly.
 */
object PrivateAddressGuard {

    private val allowedRanges = listOf(
        Ipv4Subnet.parse("10.0.0.0/8"), // RFC 1918
        Ipv4Subnet.parse("172.16.0.0/12"), // RFC 1918
        Ipv4Subnet.parse("192.168.0.0/16"), // RFC 1918
        Ipv4Subnet.parse("169.254.0.0/16"), // RFC 3927 link-local (AirPrint ad-hoc)
        Ipv4Subnet.parse("100.64.0.0/10"), // RFC 6598 carrier NAT, used by some CPE
    )

    /**
     * Loopback is deliberately excluded: a printer is never on 127.0.0.0/8, and
     * allowing it would let a crafted entry aim the client at services running
     * on the phone itself.
     */
    private val loopback = Ipv4Subnet.parse("127.0.0.0/8")

    fun isAllowed(address: Long): Boolean =
        address !in loopback && allowedRanges.any { address in it }

    fun isAllowed(address: String): Boolean =
        runCatching { isAllowed(parseIpv4(address)) }.getOrDefault(false)

    /**
     * A whole subnet is scannable only if it sits entirely inside private space.
     * Partial overlap is rejected rather than clipped, so a bad plan fails loudly
     * instead of quietly sweeping part of the internet.
     */
    fun isScannable(subnet: Ipv4Subnet): Boolean =
        isAllowed(subnet.firstHost) && isAllowed(subnet.lastHost)

    /** @throws IllegalArgumentException naming the address, for use at API edges. */
    fun require(address: String): String {
        require(isAllowed(address)) {
            "$address is not a private address. PrintDeck only talks to printers on your own network."
        }
        return address
    }
}
