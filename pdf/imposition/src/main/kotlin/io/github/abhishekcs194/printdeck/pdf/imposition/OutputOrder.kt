package io.github.abhishekcs194.printdeck.pdf.imposition

/**
 * Reverses the order sheets are sent in, so they stack the right way up.
 *
 * Almost every consumer printer delivers face-up: each sheet lands printed-side
 * up on top of the last. Print pages one to eight and the finished stack reads
 * eight to one, and has to be sorted by hand. Sending the last sheet first
 * leaves it in reading order straight out of the tray.
 *
 * Duplex is the part that needs care. The two sides of one physical sheet are
 * consecutive entries, and reversing them as a flat list would separate a front
 * from its back and print each on the wrong side of the paper. Sheets are
 * therefore reversed as whole sheets, with their sides kept in order.
 */
fun ImpositionPlan.reversedForFaceUpStacking(): ImpositionPlan =
    copy(sheets = sheets.groupedByPhysicalSheet().reversed().flatten())

/**
 * Groups sides into the physical sheets they will be printed on.
 *
 * A [SheetSide.FRONT] entry claims the [SheetSide.BACK] that follows it;
 * everything else stands alone. Anything malformed — a front with no back —
 * is left as its own group rather than swallowing an unrelated sheet.
 */
private fun List<SheetPlan>.groupedByPhysicalSheet(): List<List<SheetPlan>> {
    val groups = mutableListOf<List<SheetPlan>>()
    var index = 0
    while (index < size) {
        val sheet = this[index]
        val back = getOrNull(index + 1)
        if (sheet.side == SheetSide.FRONT && back?.side == SheetSide.BACK) {
            groups += listOf(sheet, back)
            index += 2
        } else {
            groups += listOf(sheet)
            index += 1
        }
    }
    return groups
}
