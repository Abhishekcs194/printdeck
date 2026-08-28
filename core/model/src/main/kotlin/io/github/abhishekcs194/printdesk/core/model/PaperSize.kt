package io.github.abhishekcs194.printdesk.core.model

/**
 * Standard paper sizes in points, portrait.
 *
 * Values are the ISO/ANSI definitions rounded to the nearest 1/1000 point, which
 * is what every PDF producer uses. [ippKeyword] is the PWG 5101.1 self-describing
 * media name, so a size maps straight onto an IPP `media` attribute without a
 * lookup table.
 */
enum class PaperSize(
    val displayName: String,
    val size: SizePt,
    val ippKeyword: String,
) {
    A3("A3", SizePt(841.89, 1190.55), "iso_a3_297x420mm"),
    A4("A4", SizePt(595.276, 841.89), "iso_a4_210x297mm"),
    A5("A5", SizePt(419.528, 595.276), "iso_a5_148x210mm"),
    A6("A6", SizePt(297.638, 419.528), "iso_a6_105x148mm"),
    B5("B5", SizePt(498.898, 708.661), "iso_b5_176x250mm"),
    LETTER("Letter", SizePt(612.0, 792.0), "na_letter_8.5x11in"),
    LEGAL("Legal", SizePt(612.0, 1008.0), "na_legal_8.5x14in"),
    TABLOID("Tabloid", SizePt(792.0, 1224.0), "na_ledger_11x17in"),
    EXECUTIVE("Executive", SizePt(521.86, 756.0), "na_executive_7.25x10.5in"),
    ;

    companion object {
        val Default = A4

        /**
         * Best match for an arbitrary page size, within [tolerancePt].
         * Orientation-insensitive: a landscape A4 page still matches [A4].
         */
        fun closestTo(size: SizePt, tolerancePt: Double = 3.0): PaperSize? {
            val portrait = size.oriented(landscape = false)
            return entries.firstOrNull { candidate ->
                val c = candidate.size
                kotlin.math.abs(c.width - portrait.width) <= tolerancePt &&
                    kotlin.math.abs(c.height - portrait.height) <= tolerancePt
            }
        }

        fun fromIppKeyword(keyword: String): PaperSize? =
            entries.firstOrNull { it.ippKeyword.equals(keyword, ignoreCase = true) }
    }
}
