package io.github.abhishekcs194.printdeck.print.ipp

import com.hp.jipp.encoding.AttributeGroup
import com.hp.jipp.encoding.IppInputStream
import com.hp.jipp.encoding.IppOutputStream
import com.hp.jipp.encoding.IppPacket
import com.hp.jipp.encoding.Tag
import com.hp.jipp.model.Operation
import com.hp.jipp.model.Types
import io.github.abhishekcs194.printdeck.print.ipp.discovery.PrinterEndpoint
import io.github.abhishekcs194.printdeck.print.ipp.discovery.PrivateAddressGuard
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * Asks a printer what it can do, over IPP.
 *
 * This is the half of the app that the platform print dialog cannot reach.
 * Android's `PrintAttributes` exposes paper size, resolution, colour mode,
 * duplex and margins, and nothing else — no print quality, no media type, no
 * borderless, no supply levels. Those exist on almost every printer and are
 * only reachable by asking it directly.
 *
 * IPP is a simple request/response protocol over HTTP POST, so no additional
 * networking dependency is needed beyond the encoder.
 */
class IppClient(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val timeoutMs: Int = DEFAULT_TIMEOUT_MS,
) {

    /**
     * @return the printer's capabilities, or a failure if it is unreachable or
     *   is not a printer at all. Ports 631 and 9100 are held open by plenty of
     *   routers and NAS boxes, so an open port is a lead and this is the check
     *   that turns it into a printer.
     */
    suspend fun query(endpoint: PrinterEndpoint): Result<PrinterCapabilities> =
        withContext(dispatcher) {
            runCatching {
                PrivateAddressGuard.require(endpoint.address)
                val printerUri = URI.create(endpoint.uri)
                val response = exchange(printerUri, getPrinterAttributes(printerUri))
                val attributes = response[Tag.printerAttributes]
                    ?: error("${endpoint.address} answered, but not as a printer")
                attributes.toCapabilities()
            }
        }

    private fun getPrinterAttributes(printerUri: URI): IppPacket = IppPacket(
        Operation.getPrinterAttributes,
        REQUEST_ID,
        AttributeGroup.groupOf(
            Tag.operationAttributes,
            Types.attributesCharset.of("utf-8"),
            Types.attributesNaturalLanguage.of("en"),
            Types.printerUri.of(printerUri),
            // "all" rather than a named list: supply levels and several quality
            // attributes are vendor extensions that a named request would miss.
            Types.requestedAttributes.of("all"),
        ),
    )

    private fun exchange(printerUri: URI, request: IppPacket): IppPacket {
        val body = ByteArrayOutputStream().also { IppOutputStream(it).write(request) }.toByteArray()

        // IPP rides on plain HTTP even when the scheme says ipp://.
        val httpUrl = URL(
            if (printerUri.scheme == "ipps") "https" else "http",
            printerUri.host,
            if (printerUri.port > 0) printerUri.port else DEFAULT_IPP_PORT,
            printerUri.rawPath.ifEmpty { "/ipp/print" },
        )

        val connection = (httpUrl.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            setRequestProperty("Content-Type", "application/ipp")
        }

        return try {
            connection.outputStream.use { it.write(body) }
            connection.inputStream.use { IppInputStream(it).readPacket() }
        } finally {
            connection.disconnect()
        }
    }

    private fun AttributeGroup.toCapabilities() = PrinterCapabilities(
        name = getValue(Types.printerName)?.toString()?.readable(),
        makeAndModel = getValue(Types.printerMakeAndModel)?.toString()?.readable(),
        state = getValue(Types.printerState)?.toString()?.readable(),
        stateReasons = stringsOf("printer-state-reasons"),
        documentFormats = getStrings(Types.documentFormatSupported).map { it.readable() },
        sides = getStrings(Types.sidesSupported).map { it.readable() },
        colorModes = getStrings(Types.printColorModeSupported).map { it.readable() },
        printQualities = getStrings(Types.printQualitySupported).map { it.readable() },
        mediaSizes = getStrings(Types.mediaSupported).map { it.readable() },
        mediaTypes = stringsOf("media-type-supported"),
        resolutions = stringsOf("printer-resolution-supported"),
        supplies = supplies(),
    )

    /**
     * Supply levels are a CUPS/PWG extension rather than core IPP, so they are
     * read by name: `marker-names` and `marker-levels` are parallel lists.
     */
    private fun AttributeGroup.supplies(): List<PrinterCapabilities.Supply> {
        val names = stringsOf("marker-names")
        val levels = get("marker-levels")?.mapNotNull { it as? Int }.orEmpty()
        return names.zip(levels).map { (name, level) ->
            PrinterCapabilities.Supply(name = name, percent = level.coerceIn(0, PERCENT))
        }
    }

    /** Reads any attribute by name, for the ones jipp has no typed accessor for. */
    private fun AttributeGroup.stringsOf(name: String): List<String> =
        get(name)?.map { it.toString().readable() }.orEmpty()

    private companion object {
        const val REQUEST_ID = 1
        const val DEFAULT_IPP_PORT = 631
        const val PERCENT = 100

        /** Printers on a domestic network answer quickly or not at all. */
        const val DEFAULT_TIMEOUT_MS = 4_000
    }
}

/**
 * Strips the type decoration jipp puts in its debug representation.
 *
 * Values arrive rendered for a log rather than for a person: a printer name
 * comes back as `"Canon TR4600 series" (text)`, an enum as `draft(3)`, a
 * resolution as `600x600 dpi(3)`. Showing that in a list of printers would look
 * like the app leaking its own internals, so it is cleaned once at this boundary
 * rather than in every place a value is displayed.
 *
 * Internal rather than private so the parsing can be tested directly — it is
 * string surgery on another library's formatting, which is exactly the kind of
 * thing that breaks silently on a dependency bump.
 */
internal fun String.readable(): String {
    var value = substringBefore(" (")
    if (value.endsWith(")") && value.contains('(')) {
        value = value.substringBeforeLast('(')
    }
    return value.trim().removeSurrounding("\"").trim()
}
