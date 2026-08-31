package io.github.abhishekcs194.printdeck.print.ipp.discovery

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Active sweeping: the half of discovery that works when announcement does not.
 *
 * mDNS is link-local by design, so it is structurally incapable of crossing a
 * router. A sweep is not, because the router still forwards ordinary unicast —
 * which is why a printer stranded behind a second NAT is findable this way and
 * only this way.
 *
 * Two things keep it from being slow or rude:
 *
 *  - **Probe before sweep.** Testing whether a network exists at all costs one
 *    connection; sweeping it costs 254. Networks that do not answer at their
 *    router address are skipped, so a wide search spends its time only where
 *    something is actually listening.
 *  - **Bounded concurrency with short timeouts.** A /24 across four ports is
 *    about a thousand probes, which finishes in roughly a second at this
 *    concurrency without swamping a domestic access point.
 */
class NetworkScanner(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val connectTimeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
    private val concurrency: Int = DEFAULT_CONCURRENCY,
) : NetworkProbe {

    /**
     * Is anything routing for this network?
     *
     * Checks the addresses domestic routers actually sit on. A hit means the
     * network is reachable from here even when the device holds no route for it,
     * because the default gateway forwards on its behalf — which is exactly the
     * case that makes a printer on another subnet reachable but invisible.
     */
    override suspend fun subnetExists(subnet: Ipv4Subnet): Boolean = withContext(dispatcher) {
        if (!PrivateAddressGuard.isScannable(subnet)) return@withContext false

        val base = subnet.networkAddress
        val routerCandidates = ROUTER_HOST_OFFSETS.map { base + it }
            .filter { it in subnet }

        routerCandidates.any { address ->
            GATEWAY_PROBE_PORTS.any { port -> canConnect(formatIpv4(address), port) }
        }
    }

    /**
     * Sweeps [subnets] for anything listening on a printer port, emitting hits as
     * they arrive rather than at the end, so the UI fills in progressively.
     *
     * Results are unconfirmed leads: routers and NAS boxes hold 631 and 515 open
     * more often than you would expect. Confirmation is a separate step.
     */
    override fun sweep(
        subnets: List<Ipv4Subnet>,
        ports: List<PrinterPort>,
    ): Flow<PrinterEndpoint> = channelFlow {
        val gate = Semaphore(concurrency)

        for (subnet in subnets) {
            if (!PrivateAddressGuard.isScannable(subnet)) continue

            for (host in subnet.hosts()) {
                val address = formatIpv4(host)
                for (port in ports) {
                    launch {
                        gate.withPermit {
                            if (canConnect(address, port.port)) {
                                send(
                                    PrinterEndpoint(
                                        address = address,
                                        port = port.port,
                                        source = DiscoverySource.SCAN,
                                        supportsTls = port == PrinterPort.IPPS,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
        // No awaitClose: channelFlow closes once this block returns and every
        // probe it launched has finished. An awaitClose here would keep the flow
        // open forever, and a collector waiting on it would never see the sweep
        // end - which is a search that runs indefinitely rather than reporting
        // what it found.
    }.flowOn(dispatcher)

    /** Can this exact address and port be reached right now? */
    override suspend fun canReach(address: String, port: Int): Boolean =
        withContext(dispatcher) { canConnect(address, port) }

    /**
     * A plain TCP connect. Deliberately not [java.net.InetAddress.isReachable],
     * which needs raw sockets for ICMP and silently degrades to something far
     * less meaningful when it cannot get them.
     */
    private fun canConnect(address: String, port: Int): Boolean {
        if (!PrivateAddressGuard.isAllowed(address)) return false
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(address, port), connectTimeoutMs)
                true
            }
        } catch (_: IOException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    private companion object {
        /**
         * Long enough for a sleeping inkjet on congested 2.4GHz to answer, short
         * enough that 254 hosts finish quickly. Consumer printers idle their
         * radios aggressively and can take a few hundred ms on first contact.
         */
        const val DEFAULT_CONNECT_TIMEOUT_MS = 400

        /**
         * High enough to sweep a /24 in about a second, low enough not to
         * exhaust the file-descriptor limit or make a cheap access point drop
         * frames.
         */
        const val DEFAULT_CONCURRENCY = 192

        /**
         * Where domestic routers actually live within their own range: almost
         * always the first address, occasionally the last usable one.
         */
        val ROUTER_HOST_OFFSETS = listOf(1L, 254L, 253L)

        /** Ports a domestic router almost always answers on. */
        val GATEWAY_PROBE_PORTS = listOf(80, 443, 53)
    }
}
