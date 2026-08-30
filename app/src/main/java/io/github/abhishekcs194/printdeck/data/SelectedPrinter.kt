package io.github.abhishekcs194.printdeck.data

import io.github.abhishekcs194.printdeck.print.ipp.PrinterCapabilities
import io.github.abhishekcs194.printdeck.print.ipp.discovery.PrinterEndpoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** A printer the user has chosen, together with what it told us it can do. */
data class ChosenPrinter(
    val endpoint: PrinterEndpoint,
    val capabilities: PrinterCapabilities,
) {
    val title: String get() = capabilities.makeAndModel ?: endpoint.displayName
}

/**
 * The printer currently selected, shared between the printers list and the
 * print flow.
 *
 * Held in memory rather than persisted with its capabilities, because
 * capabilities are a snapshot: ink drains, paper runs out, a tray is removed.
 * The address is remembered by [KnownPrintersStore] and the capabilities are
 * re-read when they are next needed, so the app never shows a stale ink level
 * as though it were current.
 */
@Singleton
class SelectedPrinter @Inject constructor() {

    private val _current = MutableStateFlow<ChosenPrinter?>(null)
    val current: StateFlow<ChosenPrinter?> = _current.asStateFlow()

    fun select(printer: ChosenPrinter) {
        _current.value = printer
    }

    fun clear() {
        _current.value = null
    }
}
