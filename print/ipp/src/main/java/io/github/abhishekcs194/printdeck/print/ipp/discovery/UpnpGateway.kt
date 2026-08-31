package io.github.abhishekcs194.printdeck.print.ipp.discovery

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URL

/**
 * Asks the router what network it is itself on.
 *
 * This is the difference between observing a network and guessing at one. A
 * router that does NAT has an address on the network above it, and UPnP's
 * `GetExternalIPAddress` will simply hand it over — so instead of trying
 * neighbouring ranges in the hope that one exists, the upstream network can be
 * named exactly.
 *
 * SSDP is multicast and therefore link-local, but that is no obstacle here: the
 * device being asked is our own gateway, which is by definition on our link.
 *
 * Nothing about this can reveal a network *below* another router. A NAT hides
 * what is behind it by design, and no discovery technique changes that — which
 * is worth stating plainly rather than searching forever for something
 * unreachable.
 */
class UpnpGateway(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : UpstreamGateway {

    /**
     * @return the router's own address on the network above it, if it will say.
     */
    override suspend fun externalAddress(): String? = withContext(dispatcher) {
        withTimeoutOrNull(TOTAL_TIMEOUT_MS) {
            val descriptionUrl = discoverGateway() ?: return@withTimeoutOrNull null
            val controlUrl = findControlUrl(descriptionUrl) ?: return@withTimeoutOrNull null
            queryExternalAddress(controlUrl)
        }
    }

    /** SSDP M-SEARCH for an Internet Gateway Device on the local link. */
    private fun discoverGateway(): URL? = runCatching {
        DatagramSocket().use { socket ->
            socket.soTimeout = SEARCH_TIMEOUT_MS
            socket.broadcast = true

            val request = buildString {
                append("M-SEARCH * HTTP/1.1\r\n")
                append("HOST: $SSDP_ADDRESS:$SSDP_PORT\r\n")
                append("MAN: \"ssdp:discover\"\r\n")
                append("MX: 2\r\n")
                append("ST: $IGD_DEVICE\r\n\r\n")
            }.toByteArray()

            socket.send(
                DatagramPacket(
                    request,
                    request.size,
                    InetSocketAddress(InetAddress.getByName(SSDP_ADDRESS), SSDP_PORT),
                ),
            )

            val buffer = ByteArray(RESPONSE_BUFFER)
            val response = DatagramPacket(buffer, buffer.size)
            socket.receive(response)

            String(response.data, 0, response.length)
                .lineSequence()
                .firstOrNull { it.startsWith("LOCATION:", ignoreCase = true) }
                ?.substringAfter(':', "")
                ?.let { URL(it.trim()) }
        }
    }.getOrNull()

    /** Reads the device description to find where control requests go. */
    private fun findControlUrl(descriptionUrl: URL): URL? = runCatching {
        val body = descriptionUrl.readTextWithTimeout()

        // Deliberately not an XML parse. The description is a large document and
        // all that is needed is the control URL that follows the WAN connection
        // service, which a scan finds without building a tree.
        val serviceIndex = body.indexOf(WAN_IP_SERVICE).takeIf { it >= 0 }
            ?: body.indexOf(WAN_PPP_SERVICE).takeIf { it >= 0 }
            ?: return null

        val controlTag = body.indexOf("<controlURL>", serviceIndex).takeIf { it >= 0 } ?: return null
        val path = body.substring(
            controlTag + "<controlURL>".length,
            body.indexOf("</controlURL>", controlTag),
        ).trim()

        URL(descriptionUrl, path)
    }.getOrNull()

    private fun queryExternalAddress(controlUrl: URL): String? = runCatching {
        val service = if (controlUrl.toString().contains("PPP", ignoreCase = true)) {
            WAN_PPP_SERVICE
        } else {
            WAN_IP_SERVICE
        }
        val envelope = """
            <?xml version="1.0"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"
                s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
              <s:Body><u:GetExternalIPAddress xmlns:u="$service"/></s:Body>
            </s:Envelope>
        """.trimIndent().toByteArray()

        val connection = (controlUrl.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = HTTP_TIMEOUT_MS
            readTimeout = HTTP_TIMEOUT_MS
            setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
            setRequestProperty("SOAPAction", "\"$service#GetExternalIPAddress\"")
        }

        try {
            connection.outputStream.use { it.write(envelope) }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            body.substringAfter("<NewExternalIPAddress>", "")
                .substringBefore("</NewExternalIPAddress>", "")
                .trim()
                .takeIf { it.isNotEmpty() }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    private fun URL.readTextWithTimeout(): String {
        val connection = (openConnection() as HttpURLConnection).apply {
            connectTimeout = HTTP_TIMEOUT_MS
            readTimeout = HTTP_TIMEOUT_MS
        }
        return try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val SSDP_ADDRESS = "239.255.255.250"
        const val SSDP_PORT = 1900
        const val IGD_DEVICE = "urn:schemas-upnp-org:device:InternetGatewayDevice:1"
        const val WAN_IP_SERVICE = "urn:schemas-upnp-org:service:WANIPConnection:1"
        const val WAN_PPP_SERVICE = "urn:schemas-upnp-org:service:WANPPPConnection:1"

        const val SEARCH_TIMEOUT_MS = 2_500
        const val HTTP_TIMEOUT_MS = 2_500
        const val RESPONSE_BUFFER = 2048

        /** The whole exchange is three round trips; past this it is not coming. */
        const val TOTAL_TIMEOUT_MS = 6_000L
    }
}
