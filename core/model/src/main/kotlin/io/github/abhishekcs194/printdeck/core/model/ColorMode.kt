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
     * Black and white. Worth having as an explicit choice rather than a printer
     * default: a cartridge runs out without warning, and being able to say "use
     * the ink I still have" is the difference between a job printing and a job
     * failing halfway.
     */
    MONOCHROME("Black & white"),
}
