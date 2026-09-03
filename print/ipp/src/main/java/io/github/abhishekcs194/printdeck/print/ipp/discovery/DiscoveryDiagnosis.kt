package io.github.abhishekcs194.printdeck.print.ipp.discovery

/**
 * Explains *why* a search came up empty, and what to do about it.
 *
 * "No printers found" is the dead end this app exists to get past. When a
 * printer is switched on and working, the reason a phone cannot see it is
 * almost always structural — it is on the far side of a router, or the two
 * devices roamed onto different access points that happen to share an SSID —
 * and none of that is something the user can guess at.
 *
 * The important discipline here is not over-claiming. Some causes leave hard
 * evidence and can be stated outright; others are merely plausible and are
 * offered as things to check. Asserting a confident wrong cause would be worse
 * than saying nothing, because the user would act on it.
 */
sealed interface DiscoveryDiagnosis {

    /** One-line summary for the top of the panel. */
    val headline: String

    /** What was observed, in plain language. Never jargon the user cannot act on. */
    val explanation: String

    /** Concrete things to try, best first. */
    val suggestions: List<String>

    /** Printers were found; nothing to explain. */
    data class Found(val count: Int) : DiscoveryDiagnosis {
        override val headline = if (count == 1) "1 printer found" else "$count printers found"
        override val explanation = ""
        override val suggestions = emptyList<String>()
    }

    /** No usable network, so nothing could have been found. */
    data object NotConnected : DiscoveryDiagnosis {
        override val headline = "Not connected to a network"
        override val explanation =
            "PrintDeck finds printers over Wi-Fi. This device does not currently have a " +
                "network connection."
        override val suggestions = listOf(
            "Connect to the same Wi-Fi network as your printer",
            "If you are on mobile data, switch to Wi-Fi",
        )
    }

    /**
     * The app cannot reach even the router it is connected to.
     *
     * A device that has an address on a network can always reach that network's
     * router; if it cannot, nothing is stopping at the printer — something is
     * standing between this app and the local network entirely. On recent
     * Android that is usually a per-app local-network restriction, a VPN
     * capturing traffic, or a manufacturer's own privacy toggle.
     *
     * Distinguishing this from "no printer here" matters: one is a permission to
     * grant, the other is a printer to go and switch on, and telling someone to
     * check the wrong one wastes their afternoon.
     */
    data class LocalNetworkBlocked(val gateway: String) : DiscoveryDiagnosis {
        override val headline = "PrintDeck cannot reach your network"

        override val explanation =
            "This device is connected, but PrintDeck could not reach your router at " +
                "$gateway. Something is blocking it from the local network, so no printer " +
                "on it can be found."

        override val suggestions = listOf(
            "Check PrintDeck's permissions in Android settings — look for local network, " +
                "nearby devices, or Wi-Fi access",
            "Some phones add their own privacy switch for local network access; check the " +
                "manufacturer's settings too",
            "If a VPN is running, it may be capturing this traffic — try turning it off",
        )
    }

    /**
     * The strong case. Another router answered on a network this device is not
     * part of, which means there is a whole segment next door.
     *
     * Worth stating plainly that scanning cannot fix this: a router doing NAT
     * forwards traffic outward but not inward, so a printer behind one is
     * genuinely unreachable from here — not merely hard to find. Telling the
     * user to wait or search again would waste their time.
     */
    data class PrinterOnAnotherNetwork(
        val yourNetwork: String,
        val otherRouters: List<String>,
    ) : DiscoveryDiagnosis {
        override val headline = "Your printer may be on a different network"

        override val explanation = buildString {
            append("This device is on $yourNetwork. ")
            append(
                if (otherRouters.size == 1) {
                    "Another router answered at ${otherRouters.first()}, "
                } else {
                    "Other routers answered at ${otherRouters.joinToString(", ")}, "
                },
            )
            append(
                "which means there is a second network here. Devices behind it cannot be " +
                    "reached from this one, even though both use the same Wi-Fi name — so " +
                    "searching for longer will not help.",
            )
        }

        override val suggestions = listOf(
            "Check which Wi-Fi access point your printer joined, and connect this device to the same one",
            "Or set the extender or second router to bridge (access point) mode, which puts everything on one network",
            "If you know the printer's address, add it directly",
        )
    }

    /**
     * Nothing conclusive. The honest answer: here are the usual causes, in the
     * order they are usually true.
     */
    data class NotFoundOnThisNetwork(val yourNetwork: String) : DiscoveryDiagnosis {
        override val headline = "No printers found on $yourNetwork"

        override val explanation =
            "Every address on this network was checked and nothing answered as a printer."

        /**
         * Ordered by how often each turns out to be the cause, with the check
         * that settles the question first.
         *
         * A printer can be joined to Wi-Fi and still have no IP address at all,
         * if DHCP failed - its panel shows a connection but the address field is
         * blank. Nothing can discover a device in that state, because there is no
         * address to find, so the network page is the only thing that reveals it.
         * Guessing at router settings while that is true wastes an afternoon.
         */
        override val suggestions = listOf(
            "Print a network settings page from the printer's own menu. If its IP address " +
                "is blank, the printer joined Wi-Fi but never got an address — restart it, " +
                "or give it a fixed address",
            "Two access points often share one Wi-Fi name — your printer may have joined the other one",
            "Check the printer is switched on and its Wi-Fi light is steady",
            "Some routers stop devices on the same Wi-Fi from seeing each other; look for " +
                "\"AP isolation\", \"client isolation\" or \"privacy separator\" in the router settings",
            "As a last resort, switch on the printer's own direct Wi-Fi and connect this device straight to it",
        )
    }
}

/**
 * Turns observations into a diagnosis.
 *
 * Pure, so the decision tree is directly testable — which matters, because these
 * messages are the last thing a stuck user reads and a wrong one sends them off
 * to change settings that were never the problem.
 */
object DiscoveryDiagnostics {

    /**
     * @param foreignRoutersReachable routers that answered on subnets this device
     *   is *not* part of. This is the one piece of hard evidence available: it
     *   cannot be produced by a sleeping printer or a weak signal.
     */
    data class Evidence(
        val hasNetwork: Boolean,
        val localSubnets: List<Ipv4Subnet>,
        val foreignRoutersReachable: List<String> = emptyList(),
        val printersFound: Int = 0,
        /**
         * Whether this device's own router answered. Null when it was not
         * checked. A device with an address on a network can always reach that
         * network's router, so a failure here is about access, not about
         * printers.
         */
        val ownGateway: String? = null,
        val ownGatewayReachable: Boolean? = null,
    )

    fun diagnose(evidence: Evidence): DiscoveryDiagnosis = when {
        evidence.printersFound > 0 -> DiscoveryDiagnosis.Found(evidence.printersFound)

        !evidence.hasNetwork || evidence.localSubnets.isEmpty() -> DiscoveryDiagnosis.NotConnected

        // Checked before anything about printers: if the local network itself is
        // out of reach, everything downstream of it is explained by that.
        evidence.ownGatewayReachable == false && evidence.ownGateway != null ->
            DiscoveryDiagnosis.LocalNetworkBlocked(evidence.ownGateway)

        evidence.foreignRoutersReachable.isNotEmpty() -> DiscoveryDiagnosis.PrinterOnAnotherNetwork(
            yourNetwork = evidence.localSubnets.first().asCidr(),
            otherRouters = evidence.foreignRoutersReachable,
        )

        else -> DiscoveryDiagnosis.NotFoundOnThisNetwork(evidence.localSubnets.first().asCidr())
    }
}
