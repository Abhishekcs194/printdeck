package io.github.abhishekcs194.printdeck.print.ipp

/**
 * What a printer says it can do, in its own words.
 *
 * Read from the device rather than assumed, so an options screen built from
 * this can only ever offer things the printer actually supports — no tray that
 * is not fitted, no duplex on a simplex machine.
 */
data class PrinterCapabilities(
    val name: String?,
    val makeAndModel: String?,
    /** Human-readable printer state: idle, processing, stopped. */
    val state: String?,
    /** Reasons behind the state, e.g. "media-empty", "marker-supply-low". */
    val stateReasons: List<String> = emptyList(),
    /**
     * Formats the printer will accept. Worth surfacing: many consumer inkjets
     * do not take PDF at all, which decides whether a job can be sent directly
     * or has to be rasterised first.
     */
    val documentFormats: List<String> = emptyList(),
    val sides: List<String> = emptyList(),
    val colorModes: List<String> = emptyList(),
    val printQualities: List<String> = emptyList(),
    val mediaSizes: List<String> = emptyList(),
    val mediaTypes: List<String> = emptyList(),
    val resolutions: List<String> = emptyList(),
    /** Ink or toner levels, when the printer reports them. */
    val supplies: List<Supply> = emptyList(),
) {
    val supportsPdf: Boolean
        get() = documentFormats.any { it.equals(PDF_FORMAT, ignoreCase = true) }

    val supportsDuplex: Boolean
        get() = sides.any { it.startsWith("two-sided") }

    data class Supply(val name: String, val percent: Int) {
        /** Below this a cartridge is close enough to empty to warn about. */
        val isLow: Boolean get() = percent in 0..LOW_THRESHOLD

        val isEmpty: Boolean get() = percent == 0
    }

    companion object {
        const val PDF_FORMAT = "application/pdf"
        private const val LOW_THRESHOLD = 15
    }
}
