package io.github.abhishekcs194.printdeck.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.abhishekcs194.printdeck.di.IoDispatcher
import io.github.abhishekcs194.printdeck.pdf.engine.ImageToPdfConverter
import io.github.abhishekcs194.printdeck.pdf.engine.PdfBoxRuntime
import io.github.abhishekcs194.printdeck.pdf.engine.PdfDocumentReader
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Brings documents into the app.
 *
 * Everything arrives through the Storage Access Framework or a share intent, so
 * the app never needs a storage permission and only ever sees files the user
 * explicitly handed over.
 */
@Singleton
class DocumentRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reader: PdfDocumentReader,
    private val imageConverter: ImageToPdfConverter,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) {

    private val workingDirectory: File
        get() = File(context.cacheDir, "documents").apply { mkdirs() }

    suspend fun open(uri: Uri): Result<LoadedDocument> = withContext(dispatcher) {
        runCatching {
            PdfBoxRuntime.ensureInitialised(context)
            val name = displayNameOf(uri)
            val file = if (isPdf(uri)) copyIn(uri, name) else imagesToPdf(listOf(uri), name)
            val info = reader.read(file)
            LoadedDocument(
                displayName = name,
                file = file,
                pageCount = info.pageCount,
                pageSizes = info.pageSizes,
                convertedFromImages = !isPdf(uri),
            )
        }
    }

    /** Several images become one document, so a set of photos prints as one job. */
    suspend fun openImages(uris: List<Uri>): Result<LoadedDocument> = withContext(dispatcher) {
        runCatching {
            require(uris.isNotEmpty()) { "No images selected" }
            PdfBoxRuntime.ensureInitialised(context)
            val name = if (uris.size == 1) displayNameOf(uris.first()) else "${uris.size} images"
            val file = imagesToPdf(uris, name)
            val info = reader.read(file)
            LoadedDocument(
                displayName = name,
                file = file,
                pageCount = info.pageCount,
                pageSizes = info.pageSizes,
                convertedFromImages = true,
            )
        }
    }

    private fun isPdf(uri: Uri): Boolean =
        context.contentResolver.getType(uri)?.contains("pdf", ignoreCase = true) == true

    private fun copyIn(uri: Uri, name: String): File {
        val destination = File(workingDirectory, "${name.sanitised()}-${System.nanoTime()}.pdf")
        context.contentResolver.openInputStream(uri)?.use { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Could not read $name")
        return destination
    }

    private suspend fun imagesToPdf(uris: List<Uri>, name: String): File {
        val destination = File(workingDirectory, "${name.sanitised()}-${System.nanoTime()}.pdf")
        val sources = uris.map { uri ->
            ImageToPdfConverter.ImageSource { context.contentResolver.openInputStream(uri) }
        }
        return imageConverter.convert(sources, destination)
    }

    private fun displayNameOf(uri: Uri): String {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                cursor.getString(index)?.let { return it.substringBeforeLast('.') }
            }
        }
        return uri.lastPathSegment?.substringBeforeLast('.') ?: "Document"
    }

    /** Removes intermediates. Print jobs are the user's papers and do not linger. */
    suspend fun clearCache() = withContext(dispatcher) {
        workingDirectory.listFiles()?.forEach { it.delete() }
        Unit
    }

    private fun String.sanitised(): String =
        filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(MAX_NAME_LENGTH).ifEmpty { "document" }

    private companion object {
        const val MAX_NAME_LENGTH = 40
    }
}
