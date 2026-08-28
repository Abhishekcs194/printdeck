package io.github.abhishekcs194.printdeck.print.ipp.discovery

/** Structure of an IPv4 address, named once so the bit maths reads plainly. */
private const val ADDRESS_BITS = 32
private const val OCTET_BITS = 8
private const val OCTET_COUNT = 4
private const val OCTET_MASK = 0xFFL
private const val MAX_OCTET_VALUE = 255
internal const val ALL_ONES = 0xFFFFFFFFL

/**
 * An IPv4 subnet in CIDR form, and the host enumeration a sweep needs.
 *
 * Addresses are held as unsigned 32-bit values in a [Long], so the top bit that
 * `192.x` and above set cannot silently turn negative — the classic way this
 * arithmetic goes wrong. Pure and Android-free, so the boundary behaviour can be
 * tested directly.
 */
data class Ipv4Subnet(val networkAddress: Long, val prefixLength: Int) {

    init {
        require(prefixLength in 0..ADDRESS_BITS) { "Prefix length must be 0..$ADDRESS_BITS, was $prefixLength" }
        require(networkAddress in 0..ALL_ONES) { "Address out of range: $networkAddress" }
    }

    private val hostBits: Int get() = ADDRESS_BITS - prefixLength

    /** Total addresses in the block, including network and broadcast. */
    private val blockSize: Long get() = 1L shl hostBits

    /** Addresses actually assignable to a host. */
    val hostCount: Long
        get() = when (prefixLength) {
            ADDRESS_BITS -> 1L
            // RFC 3021 point-to-point: no network or broadcast reservation.
            ADDRESS_BITS - 1 -> 2L
            else -> blockSize - RESERVED_ADDRESSES
        }

    val firstHost: Long
        get() = if (prefixLength >= ADDRESS_BITS - 1) networkAddress else networkAddress + 1

    val lastHost: Long
        get() = if (prefixLength >= ADDRESS_BITS - 1) {
            networkAddress + blockSize - 1
        } else {
            networkAddress + blockSize - RESERVED_ADDRESSES
        }

    operator fun contains(address: Long): Boolean =
        address >= networkAddress && address <= networkAddress + blockSize - 1

    /**
     * Every usable host address. A [Sequence] rather than a list: a /16 holds
     * 65 534 addresses, and materialising that just to hand it to a scanner is
     * pure waste.
     */
    fun hosts(): Sequence<Long> = (firstHost..lastHost).asSequence()

    fun asCidr(): String = "${formatIpv4(networkAddress)}/$prefixLength"

    override fun toString(): String = asCidr()

    companion object {
        /** Network and broadcast, which no host may take. */
        private const val RESERVED_ADDRESSES = 2

        /** Builds the subnet containing [address], masking the host bits off. */
        fun containing(address: Long, prefixLength: Int): Ipv4Subnet {
            require(prefixLength in 0..ADDRESS_BITS) {
                "Prefix length must be 0..$ADDRESS_BITS, was $prefixLength"
            }
            val mask = if (prefixLength == 0) 0L else (ALL_ONES shl (ADDRESS_BITS - prefixLength)) and ALL_ONES
            return Ipv4Subnet(address and mask, prefixLength)
        }

        fun containing(address: String, prefixLength: Int): Ipv4Subnet =
            containing(parseIpv4(address), prefixLength)

        fun parse(cidr: String): Ipv4Subnet {
            val parts = cidr.split('/')
            require(parts.size == 2) { "'$cidr' is not in a.b.c.d/nn form" }
            val prefix = parts[1].toIntOrNull()
                ?: throw IllegalArgumentException("'$cidr' has a non-numeric prefix")
            return containing(parseIpv4(parts[0]), prefix)
        }
    }
}

/** Parses dotted-quad IPv4 into an unsigned 32-bit value. */
fun parseIpv4(address: String): Long {
    val octets = address.trim().split('.')
    require(octets.size == OCTET_COUNT) { "'$address' is not an IPv4 address" }
    return octets.fold(0L) { acc, octet ->
        val value = octet.toIntOrNull()
            ?: throw IllegalArgumentException("'$address' has a non-numeric octet")
        require(value in 0..MAX_OCTET_VALUE) { "'$address' has an octet out of range" }
        (acc shl OCTET_BITS) or value.toLong()
    }
}

/** Formats an unsigned 32-bit value as dotted-quad IPv4. */
fun formatIpv4(address: Long): String =
    (OCTET_COUNT - 1 downTo 0).joinToString(".") { index ->
        ((address shr (index * OCTET_BITS)) and OCTET_MASK).toString()
    }
