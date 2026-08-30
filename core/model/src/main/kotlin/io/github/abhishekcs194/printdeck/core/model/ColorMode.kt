package io.github.abhishekcs194.printdeck.core.model

/**
 * Which ink to print with.
 *
 * Kept out of [ImpositionSettings] on purpose: it changes nothing about where
 * pages land on a sheet, and the imposition engine would only have to ignore it.
 * It affects how the finished sheets are rendered and what the printer is asked
 * to do with them.
 */
enum class ColorMode(val displayName: String) {
    COLOR("Colour"),


    /**
     * Black and white, and the default.
     *
     * Most of what people print is text, colour ink is the expensive one, and a
     * colour cartridge runs dry long before the black one on a printer used for
     * documents. Defaulting to colour spends the scarcer ink on a page that
     * rarely needed it — and someone printing a photo will go looking for the
     * setting anyway, while someone printing a document will not.
     */
    MONOCHROME("Black & white"),
}
