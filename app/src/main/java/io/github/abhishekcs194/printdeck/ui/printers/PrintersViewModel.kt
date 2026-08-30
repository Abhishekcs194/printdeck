package io.github.abhishekcs194.printdeck.ui.printers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.abhishekcs194.printdeck.data.ChosenPrinter
import io.github.abhishekcs194.printdeck.data.KnownPrintersStore
import io.github.abhishekcs194.printdeck.data.SelectedPrinter
import io.github.abhishekcs194.printdeck.print.ipp.IppClient
import io.github.abhishekcs194.printdeck.print.ipp.PrinterCapabilities
import io.github.abhishekcs194.printdeck.print.ipp.discovery.DiscoverySource
import io.github.abhishekcs194.printdeck.print.ipp.discovery.DiscoveryDiagnosis
import io.github.abhishekcs194.printdeck.print.ipp.discovery.PrinterDiscovery
import io.github.abhishekcs194.printdeck.print.ipp.discovery.PrinterEndpoint
import io.github.abhishekcs194.printdeck.print.ipp.discovery.PrivateAddressGuard
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PrintersViewModel @Inject constructor(
    private val discovery: PrinterDiscovery,
    private val ippClient: IppClient,
    private val knownPrinters: KnownPrintersStore,
    private val selectedPrinter: SelectedPrinter,
) : ViewModel() {

    /** A discovered printer, once it has confirmed what it is. */
    data class FoundPrinter(
        val endpoint: PrinterEndpoint,
        val capabilities: PrinterCapabilities?,
    ) {
        val title: String
            get() = capabilities?.makeAndModel ?: endpoint.displayName

        val confirmed: Boolean get() = capabilities != null
    }

    data class UiState(
        val searching: Boolean = false,
        val phase: PrinterDiscovery.Phase? = null,
        val networksSearched: Int = 0,
        val networksTotal: Int = 0,
        val printers: List<FoundPrinter> = emptyList(),
        val diagnosis: DiscoveryDiagnosis? = null,
        val manualEntryError: String? = null,
    ) {
        /** Progress text. Naming the ring explains why a search is still going. */
        val progressLabel: String
            get() = when (phase) {
                PrinterDiscovery.Phase.CHECKING_KNOWN -> "Checking printers you have used"
                PrinterDiscovery.Phase.SEARCHING_NEARBY -> "Searching this network"
                PrinterDiscovery.Phase.SEARCHING_WIDER ->
                    "Looking on other networks ($networksSearched of $networksTotal)"
                PrinterDiscovery.Phase.FINISHED, null -> ""
            }
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        search()
    }

    fun search() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.update {
                it.copy(searching = true, printers = emptyList(), diagnosis = null)
            }

            discovery.discover(
                remembered = knownPrinters.currentEndpoints(),
                rememberedSubnets = knownPrinters.currentSubnets(),
            ).collect { progress ->
                _state.update {
                    it.copy(
                        phase = progress.phase,
                        networksSearched = progress.networksSearched,
                        networksTotal = progress.networksTotal,
                        diagnosis = progress.diagnosis,
                        searching = progress.phase != PrinterDiscovery.Phase.FINISHED,
                    )
                }
                // Identify each lead as it arrives, so the list fills in rather
                // than appearing all at once at the end.
                progress.printers.forEach { identify(it) }
            }
        }
    }

    /**
     * Confirms a lead is really a printer, and learns what it can do.
     *
     * An open port is not a printer: routers and NAS boxes hold 631 and 515 open
     * more often than you would expect. Asking over IPP is what separates the
     * two, and it is the same request that yields the model name, state and ink
     * levels the list shows.
     */
    private fun identify(endpoint: PrinterEndpoint) {
        if (_state.value.printers.any { it.endpoint.key == endpoint.key }) return

        // Show it immediately as unconfirmed, so the list responds at once.
        _state.update { it.copy(printers = it.printers + FoundPrinter(endpoint, null)) }

        viewModelScope.launch {
            val capabilities = ippClient.query(endpoint).getOrNull()
            if (capabilities == null) {
                // Not a printer after all; quietly drop it rather than listing
                // something the user cannot print to.
                _state.update { current ->
                    current.copy(printers = current.printers.filterNot { it.endpoint.key == endpoint.key })
                }
                return@launch
            }

            val confirmed = endpoint.copy(
                name = capabilities.name ?: endpoint.name,
                makeAndModel = capabilities.makeAndModel,
                confirmed = true,
            )
            _state.update { current ->
                current.copy(
                    printers = current.printers.map {
                        if (it.endpoint.key == endpoint.key) FoundPrinter(confirmed, capabilities) else it
                    },
                )
            }
            knownPrinters.remember(confirmed)
        }
    }

    /** Adds a printer by address, for when the network refuses to cooperate. */
    fun addManually(address: String) {
        val trimmed = address.trim()
        if (!PrivateAddressGuard.isAllowed(trimmed)) {
            _state.update {
                it.copy(manualEntryError = "Enter an address on your own network, such as 192.168.1.50")
            }
            return
        }
        _state.update { it.copy(manualEntryError = null) }
        identify(
            PrinterEndpoint(address = trimmed, port = IPP_PORT, source = DiscoverySource.MANUAL),
        )
    }

    fun dismissManualEntryError() = _state.update { it.copy(manualEntryError = null) }

    /** Chooses a printer for printing. Only confirmed printers can be selected. */
    fun select(printer: FoundPrinter) {
        val capabilities = printer.capabilities ?: return
        selectedPrinter.select(ChosenPrinter(printer.endpoint, capabilities))
    }

    private companion object {
        const val IPP_PORT = 631
    }
}
