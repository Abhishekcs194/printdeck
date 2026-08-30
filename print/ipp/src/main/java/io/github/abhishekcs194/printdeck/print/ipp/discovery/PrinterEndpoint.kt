package io.github.abhishekcs194.printdeck.print.ipp.discovery

/** How a candidate printer was found, which decides how much we trust it. */
enum class DiscoverySource {
    /** Previously used and remembered. Probed first, and instantly. */
    REMEMBERED,

    /** Announced itself over mDNS. Only ever works on the same network segment. */
    MDNS,

    /** Answered a port probe during a sweep. */
    SCAN,

    /** Typed in by the user. */
    MANUAL,
}

/** A printer service port worth probing, and what finding it open implies. */
enum class PrinterPort(val port: Int, val label: String) {
    IPP(631, "IPP"),
    IPPS(443, "IPPS"),
    RAW(9100, "JetDirect"),
    LPD(515, "LPD"),

    /**
     * Canon's proprietary BJNP. Worth probing because Canon consumer inkjets -
     * the TR4600 among them - answer here even when their IPP stack is being
     * unhelpful.
     */
    BJNP(8611, "Canon BJNP"),
    ;

    companion object {
        /** Ordered by how strongly an open port suggests an actual printer. */
        val sweepOrder = listOf(IPP, RAW, BJNP, LPD)
    }
}

/**
 * Something on the network that answered like a printer.
 *
 * [confirmed] separates "port 631 is open" from "this identified itself as a
 * printer via Get-Printer-Attributes". Routers and NAS boxes hold 631 and 515
 * open surprisingly often, so an unconfirmed hit is a lead, not a printer, and
 * the UI must not present it as one.
 */
data class PrinterEndpoint(
    val address: String,
    val port: Int,
    val source: DiscoverySource,
    /** Advertised or queried name; null until identified. */
    val name: String? = null,
    val makeAndModel: String? = null,
    /** IPP resource path, e.g. "ipp/print". mDNS supplies it via the `rp` TXT record. */
    val resourcePath: String = "ipp/print",
    val supportsTls: Boolean = false,
    /** True once the device has answered Get-Printer-Attributes as a printer. */
    val confirmed: Boolean = false,
) {
    val uri: String
        get() = "${if (supportsTls) "ipps" else "ipp"}://$address:$port/${resourcePath.removePrefix("/")}"

    /** Identity for de-duplication: the same box found by mDNS and by sweep is one printer. */
    val key: String get() = "$address:$port"

    /**
     * True when this port can answer Get-Printer-Attributes.
     *
     * LPD and raw ports accept jobs but describe nothing, so a printer found on
     * one of those cannot report its capabilities and is worth less than the
     * same printer found on IPP.
     */
    val speaksIpp: Boolean get() = port == PrinterPort.IPP.port || port == PrinterPort.IPPS.port

    val displayName: String get() = name ?: makeAndModel ?: address
}
