package io.github.abhishekcs194.printdeck.print.ipp.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import androidx.core.content.getSystemService
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.net.Inet4Address

/**
 * Announcement-based discovery over mDNS / DNS-SD.
 *
 * Fast and precise when it works: a printer that answers here hands over its
 * name, model and IPP resource path, so nothing has to be guessed. Its hard
 * limit is that multicast is link-local — it will never find a printer on the
 * other side of a router, however long it listens. That is not a bug to be
 * tuned around; it is why [NetworkScanner] exists alongside it.
 */
class MdnsDiscovery(private val context: Context) : Announcements {

    override fun discover(): Flow<PrinterEndpoint> = callbackFlow {
        val nsd = context.getSystemService<NsdManager>()
        if (nsd == null) {
            close()
            return@callbackFlow
        }

        // Platforms before API 30 allow only one resolve in flight and fail the
        // rest with FAILURE_ALREADY_ACTIVE. Those losses are tolerated rather
        // than queued around: the sweep covers the same ground, and a printer
        // missed here is found there a moment later.
        val listeners = mutableListOf<NsdManager.DiscoveryListener>()

        SERVICE_TYPES.forEach { serviceType ->
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(type: String) = Unit
                override fun onDiscoveryStopped(type: String) = Unit
                override fun onStartDiscoveryFailed(type: String, errorCode: Int) = Unit
                override fun onStopDiscoveryFailed(type: String, errorCode: Int) = Unit
                override fun onServiceLost(service: NsdServiceInfo) = Unit

                override fun onServiceFound(service: NsdServiceInfo) {
                    @Suppress("DEPRECATION")
                    nsd.resolveService(
                        service,
                        object : NsdManager.ResolveListener {
                            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) = Unit

                            override fun onServiceResolved(info: NsdServiceInfo) {
                                toEndpoint(info, serviceType)?.let { trySend(it) }
                            }
                        },
                    )
                }
            }
            listeners += listener
            runCatching {
                nsd.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
            }
        }

        awaitClose {
            listeners.forEach { runCatching { nsd.stopServiceDiscovery(it) } }
        }
    }

    @Suppress("DEPRECATION")
    private fun toEndpoint(info: NsdServiceInfo, serviceType: String): PrinterEndpoint? {
        val host = info.host as? Inet4Address ?: return null
        val address = host.hostAddress ?: return null
        if (!PrivateAddressGuard.isAllowed(address)) return null

        // The `rp` TXT record carries the IPP resource path. Without it the queue
        // path has to be guessed, and guessing wrong yields a printer that is
        // found but cannot be printed to.
        val resourcePath = info.attributes["rp"]?.toString(Charsets.UTF_8) ?: "ipp/print"
        val model = info.attributes["ty"]?.toString(Charsets.UTF_8)

        return PrinterEndpoint(
            address = address,
            port = info.port,
            source = DiscoverySource.MDNS,
            name = info.serviceName,
            makeAndModel = model,
            resourcePath = resourcePath,
            supportsTls = serviceType.startsWith("_ipps"),
        )
    }

    private companion object {
        /**
         * IPP first, then raw and LPD. The last two are worth browsing because
         * plenty of older network printers advertise nothing else, and a printer
         * found by any means beats one that is not found at all.
         */
        val SERVICE_TYPES = listOf(
            "_ipps._tcp.",
            "_ipp._tcp.",
            "_pdl-datastream._tcp.",
            "_printer._tcp.",
        )
    }
}
