package io.github.abhishekcs194.printdeck.print.ipp

import com.hp.jipp.encoding.AttributeGroup
import com.hp.jipp.encoding.IppInputStream
import com.hp.jipp.encoding.IppOutputStream
import com.hp.jipp.encoding.IppPacket
import com.hp.jipp.encoding.Tag
import com.hp.jipp.model.Operation
import com.hp.jipp.model.Types
import com.hp.jipp.pdl.ColorSpace
import com.hp.jipp.pdl.OutputSettings
import com.hp.jipp.pdl.pwg.PwgSettings
import com.hp.jipp.pdl.pwg.PwgWriter
import io.github.abhishekcs194.printdeck.print.ipp.discovery.PrinterEndpoint
import io.github.abhishekcs194.printdeck.print.ipp.discovery.PrivateAddressGuard
import io.github.abhishekcs194.printdeck.print.ipp.raster.PdfRasterDocument
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * Sends a job to a printer over IPP, and asks how it is getting on.
 *
 * This is the path that exists because the platform one cannot go here.
 * Android's print framework offers paper size, resolution, colour mode, duplex
 * and margins; print quality, media type and borderless simply have no
 * representation in it. Speaking IPP directly means the options screen can be
 * built from what the printer says it supports, and the job carries those
 * choices through unchanged.
 */
class IppPrinter(
    /** Where rasterised jobs are written. A property of the printer, not of a job. */
    private val workingDirectory: File,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val connectTimeoutMs: Int = CONNECT_TIMEOUT_MS,
) {

    /**
     * Prints [pdf], converting it first if the printer cannot take PDF.
     *
     * A rasterised job is written to a file rather than held in memory: a dozen
     * A4 sheets at 300dpi is far more than a phone will hand out as one array.
     */
    suspend fun print(
        endpoint: PrinterEndpoint,
        capabilities: PrinterCapabilities,
        pdf: File,
        jobName: String,
        options: IppPrintOptions,
    ): Result<IppJob> = withContext(dispatcher) {
        runCatching {
            PrivateAddressGuard.require(endpoint.address)

            val (document, format) = if (capabilities.supportsPdf) {
                pdf to PrinterCapabilities.PDF_FORMAT
            } else {
                rasterise(pdf, options) to PWG_RASTER_FORMAT
            }

            try {
                val printerUri = URI.create(endpoint.uri)
                val response = submit(printerUri, document, format, jobName, options)
                response.toJob()
            } finally {
                // The rasterised copy is large and single-use.
                if (document != pdf) document.delete()
            }
        }
    }

    /**
     * Asks whether the printer would accept a job, without sending one.
     *
     * IPP's Validate-Job exists precisely for this. It is the honest way to check
     * that an options combination is acceptable — the alternative is discovering
     * a rejected attribute after a dozen sheets have already gone through.
     */
    suspend fun validate(
        endpoint: PrinterEndpoint,
        jobName: String,
        options: IppPrintOptions,
        format: String,
    ): Result<Boolean> = withContext(dispatcher) {
        runCatching {
            PrivateAddressGuard.require(endpoint.address)
            val printerUri = URI.create(endpoint.uri)
            val response = exchange(
                printerUri,
                jobRequest(Operation.validateJob, printerUri, format, jobName, options),
                document = null,
            )
            // Any successful status means the attributes were understood.
            response.status.code < STATUS_ERROR_THRESHOLD
        }
    }

    /** Asks the printer how a job is getting on. */
    suspend fun jobStatus(endpoint: PrinterEndpoint, jobId: Int): Result<IppJob> =
        withContext(dispatcher) {
            runCatching {
                PrivateAddressGuard.require(endpoint.address)
                val printerUri = URI.create(endpoint.uri)
                val request = IppPacket(
                    Operation.getJobAttributes,
                    REQUEST_ID,
                    AttributeGroup.groupOf(
                        Tag.operationAttributes,
                        Types.attributesCharset.of(CHARSET),
                        Types.attributesNaturalLanguage.of(LANGUAGE),
                        Types.printerUri.of(printerUri),
                        Types.jobId.of(jobId),
                    ),
                )
                exchange(printerUri, request, document = null).toJob()
            }
        }

    /**
     * Converts the PDF to PWG-Raster.
     *
     * This is the work a desktop print driver does. Doing it on the phone is the
     * price of talking to a printer that has no PDF interpreter, which is most
     * consumer inkjets.
     */
    private fun rasterise(pdf: File, options: IppPrintOptions): File {
        val target = File(workingDirectory, "job-${System.nanoTime()}.pwg")
        val colorSpace = if (options.isMonochrome) ColorSpace.Grayscale else ColorSpace.Rgb

        PdfRasterDocument.open(pdf, options.rasterDpi).use { document ->
            val output = OutputSettings(
                colorSpace = colorSpace,
                sides = options.sides,
                copies = options.copies,
            )

            // Paper feeds one way round, and the media keyword names a portrait
            // sheet. A landscape imposition has to be turned to match it - the
            // rotation a desktop print driver performs and the user never sees.
            // Without it the printer shrinks the whole sheet to fit its short
            // edge, leaving a white band top and bottom and wasting most of the
            // page the imposition was arranged to fill.
            val oriented = if (options.sheetIsLandscape) {
                document.mapPages { pages -> pages.map { it.rotated() } }
            } else {
                document
            }

            target.outputStream().buffered().use { stream ->
                PwgWriter(stream, PwgSettings(output)).write(oriented)
            }
        }
        return target
    }

    private fun submit(
        printerUri: URI,
        document: File,
        format: String,
        jobName: String,
        options: IppPrintOptions,
    ): IppPacket = exchange(
        printerUri,
        jobRequest(Operation.printJob, printerUri, format, jobName, options),
        document,
    )

    /**
     * The attributes for a job. Shared by Print-Job and Validate-Job so that what
     * is validated is exactly what would be sent — validating a different request
     * from the one that follows would be worse than not validating at all.
     */
    private fun jobRequest(
        operation: Operation,
        printerUri: URI,
        format: String,
        jobName: String,
        options: IppPrintOptions,
    ): IppPacket {
        val operationAttributes = AttributeGroup.groupOf(
            Tag.operationAttributes,
            Types.attributesCharset.of(CHARSET),
            Types.attributesNaturalLanguage.of(LANGUAGE),
            Types.printerUri.of(printerUri),
            Types.requestingUserName.of(REQUESTING_USER),
            Types.jobName.of(jobName),
            Types.documentFormat.of(format),
        )

        // Only attributes the user actually chose are sent. Naming every
        // attribute, defaults included, invites a printer to reject the whole
        // job over one it does not recognise.
        val jobAttributes = buildList {
            add(Types.copies.of(options.copies))
            add(Types.sides.of(options.sides))
            add(Types.printColorMode.of(options.colorMode))
            options.media?.let { add(Types.media.of(it)) }
        }

        return IppPacket(
            operation,
            REQUEST_ID,
            operationAttributes,
            AttributeGroup.groupOf(Tag.jobAttributes, jobAttributes),
        )
    }

    private fun exchange(printerUri: URI, request: IppPacket, document: File?): IppPacket {
        val header = ByteArrayOutputStream()
            .also { IppOutputStream(it).write(request) }
            .toByteArray()

        val httpUrl = URL(
            if (printerUri.scheme == "ipps") "https" else "http",
            printerUri.host,
            if (printerUri.port > 0) printerUri.port else DEFAULT_IPP_PORT,
            printerUri.rawPath.ifEmpty { DEFAULT_PATH },
        )

        val connection = (httpUrl.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = connectTimeoutMs
            // A large job takes real time to accept; the printer holds the
            // connection open while it ingests.
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Content-Type", "application/ipp")
            if (document != null) {
                // Streamed rather than buffered: the whole point of writing the
                // raster to a file was not to hold it in memory.
                setChunkedStreamingMode(0)
            }
        }

        return try {
            connection.outputStream.use { out ->
                out.write(header)
                document?.inputStream()?.use { it.copyTo(out) }
            }
            connection.inputStream.use { IppInputStream(it).readPacket() }
        } finally {
            connection.disconnect()
        }
    }

    private fun IppPacket.toJob(): IppJob {
        val group = this[Tag.jobAttributes]
            ?: error("The printer accepted the request but described no job")
        return IppJob(
            id = group.getValue(Types.jobId) ?: 0,
            state = group.getValue(Types.jobState)?.toString()?.readable() ?: "unknown",
            stateReasons = group.get("job-state-reasons")?.map { it.toString().readable() }.orEmpty(),
        )
    }

    private companion object {
        const val REQUEST_ID = 1
        const val DEFAULT_IPP_PORT = 631
        const val DEFAULT_PATH = "/ipp/print"
        const val CHARSET = "utf-8"
        const val LANGUAGE = "en"
        const val PWG_RASTER_FORMAT = "image/pwg-raster"

        /** IPP requires a user name; the printer only uses it for the job list. */
        const val REQUESTING_USER = "PrintDeck"

        /** IPP status codes at or above this are errors. */
        const val STATUS_ERROR_THRESHOLD = 0x0100

        const val CONNECT_TIMEOUT_MS = 5_000
        const val READ_TIMEOUT_MS = 120_000
    }
}
