package io.github.abhishekcs194.printdeck.print.system

import io.github.abhishekcs194.printdeck.core.model.ColorMode
import io.github.abhishekcs194.printdeck.core.model.PaperSize
import java.io.File

/**
 * A finished job: an imposed document plus how it should be printed.
 *
 * Everything here is already decided — the file on disk is the final layout, and
 * these are the attributes the dialog should open with rather than suggestions.
 */
data class PrintJobSpec(
    val document: File,
    val name: String,
    val sheetCount: Int,
    val paper: PaperSize,
    val landscape: Boolean,
    val colorMode: ColorMode,
)
