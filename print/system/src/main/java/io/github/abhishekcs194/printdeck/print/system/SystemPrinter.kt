package io.github.abhishekcs194.printdeck.print.system

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import androidx.core.content.getSystemService
import io.github.abhishekcs194.printdeck.core.model.PaperSize
import java.io.File

/**
 * Opens the platform print dialog for an imposed document.
 *
 * This is the transport that works everywhere: it reaches any printer the phone
 * can already see, through Mopria or a manufacturer plugin, and offers
 * "Save as PDF" when there is no printer at all.
 */
class SystemPrinter(private val context: Context) {

    /**
     * @return true if the dialog was opened.
     *
     * The chosen paper size and orientation are passed through as the starting
     * attributes. Without that the dialog opens on whatever it used last, and a
     * document imposed for A4 landscape gets silently rescaled onto portrait
     * Letter — which would quietly undo the layout the user just built.
     */
    fun print(
        document: File,
        jobName: String,
        pageCount: Int,
        paper: PaperSize,
        landscape: Boolean,
    ): Boolean {
        val printManager = context.getSystemService<PrintManager>() ?: return false

        // Colour mode is deliberately not set. Which mode is right depends on
        // what ink the printer actually has, and that changes without warning -
        // the printer this was developed against had an empty black cartridge one
        // week and an empty colour one the next. Forcing either would be wrong
        // half the time, so the dialog's own default is left alone.
        //
        // Margins are set to none because imposition has already applied the
        // user's margins; letting the framework add its own would inset the
        // sheet twice.
        val attributes = PrintAttributes.Builder()
            .setMediaSize(paper.toMediaSize(landscape))
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()

        printManager.print(
            jobName,
            ImposedPdfPrintAdapter(file = document, jobName = jobName, pageCount = pageCount),
            attributes,
        )
        return true
    }
}

/**
 * Maps a paper size onto the framework's own list.
 *
 * Falls back to A4 rather than failing: an unmapped size still prints, just with
 * the dialog defaulting somewhere the user can correct, which beats no dialog.
 */
private fun PaperSize.toMediaSize(landscape: Boolean): PrintAttributes.MediaSize {
    val portrait = when (this) {
        PaperSize.A3 -> PrintAttributes.MediaSize.ISO_A3
        PaperSize.A4 -> PrintAttributes.MediaSize.ISO_A4
        PaperSize.A5 -> PrintAttributes.MediaSize.ISO_A5
        PaperSize.A6 -> PrintAttributes.MediaSize.ISO_A6
        PaperSize.B5 -> PrintAttributes.MediaSize.ISO_B5
        PaperSize.LETTER -> PrintAttributes.MediaSize.NA_LETTER
        PaperSize.LEGAL -> PrintAttributes.MediaSize.NA_LEGAL
        PaperSize.TABLOID -> PrintAttributes.MediaSize.NA_TABLOID
        PaperSize.EXECUTIVE -> PrintAttributes.MediaSize.NA_LETTER
    }
    return if (landscape) portrait.asLandscape() else portrait.asPortrait()
}
