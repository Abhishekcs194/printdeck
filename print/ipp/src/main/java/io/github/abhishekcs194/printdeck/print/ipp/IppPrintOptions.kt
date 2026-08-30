package io.github.abhishekcs194.printdeck.print.ipp

/**
 * What to ask the printer for.
 *
 * Deliberately expressed as IPP keywords rather than an enum of our own: these
 * values come straight from what the printer said it supports, so inventing a
 * parallel vocabulary would only mean translating back and risking a value the
 * printer never offered.
 */
data class IppPrintOptions(
    val copies: Int = 1,
    /** `one-sided`, `two-sided-long-edge`, `two-sided-short-edge`. */
    val sides: String = SIDES_ONE_SIDED,
    /** `color`, `monochrome`, `auto`, `auto-monochrome`. */
    val colorMode: String = COLOR_MODE_MONOCHROME,
    /** `draft`, `normal`, `high`. */
    val quality: String = QUALITY_NORMAL,
    /** PWG media keyword, e.g. `iso_a4_210x297mm`. */
    val media: String? = null,
    /** e.g. `stationery`, `photographic`. */
    val mediaType: String? = null,
    /**
     * Rasterising resolution, used only when the printer cannot take PDF.
     *
     * 300 rather than the printer's full 600: at 600dpi an A4 page is four times
     * the pixels, which on a phone is the difference between a job that renders
     * and one that runs out of memory. 300 is indistinguishable for text.
     */
    val rasterDpi: Int = DEFAULT_RASTER_DPI,
) {
    val isMonochrome: Boolean
        get() = colorMode == COLOR_MODE_MONOCHROME || colorMode == COLOR_MODE_AUTO_MONOCHROME

    companion object {
        const val SIDES_ONE_SIDED = "one-sided"
        const val SIDES_LONG_EDGE = "two-sided-long-edge"
        const val SIDES_SHORT_EDGE = "two-sided-short-edge"

        const val COLOR_MODE_COLOR = "color"
        const val COLOR_MODE_MONOCHROME = "monochrome"
        const val COLOR_MODE_AUTO_MONOCHROME = "auto-monochrome"

        const val QUALITY_DRAFT = "draft"
        const val QUALITY_NORMAL = "normal"
        const val QUALITY_HIGH = "high"

        const val DEFAULT_RASTER_DPI = 300
    }
}

/** Where a submitted job has got to. */
data class IppJob(
    val id: Int,
    val state: String,
    val stateReasons: List<String> = emptyList(),
) {
    val isFinished: Boolean
        get() = state in setOf("completed", "canceled", "aborted")

    val failed: Boolean
        get() = state in setOf("canceled", "aborted")
}
