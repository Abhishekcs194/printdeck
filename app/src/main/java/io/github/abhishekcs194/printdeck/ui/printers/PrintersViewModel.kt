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
import io.github.abhishekcs194.printdeck.print.ipp.discovery.NetworkChanges
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
    private val networkChanges: NetworkChanges,
) : ViewModel() {

    /**
     * Something discovery turned up, at whatever stage of being identified.
     *
     * A device that answered on a printer port but not to IPP is kept rather
     * than discarded. It is still real, the user can often still print to it
     * through the system dialog, and above all removing it silently leaves the
     * screen claiming a printer was found while showing nothing to select.
     */
    data class FoundPrinter(
        val endpoint: PrinterEndpoint,
        val capabilities: PrinterCapabilities? = null,
        val identifying: Boolean = true,
        /** Why identification failed, when it did. */
        val problem: String? = null,
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
        /**
         * Shown only when there is genuinely nothing on screen to act on.
         *
         * The diagnosis is computed from what discovery turned up, while the list
         * shows what survived identification, so the two can disagree. Explaining
         * a failure above a populated list, or announcing a find above an empty
         * one, is worse than saying nothing.
         */
        val diagnosisToShow: DiscoveryDiagnosis?
            get() = diagnosis
                ?.takeIf { !searching && printers.isEmpty() }
                ?.takeIf { it !is DiscoveryDiagnosis.Found }
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
        observeNetworkChanges()
    }

    /**
     * Starts again when the device moves network.
     *
     * A printer found on one network is not reachable from another, and the app
     * has no way to tell which of its results still hold. Keeping them would
     * show a printer that cannot be printed to - which is exactly what happened
     * when switching between the bands of one router, since those are separate
     * access points and can hand out separate subnets.
     */
    private fun observeNetworkChanges() {
        viewModelScope.launch {
            networkChanges.changes().collect {
                selectedPrinter.clear()
                _state.update { it.copy(printers = emptyList(), diagnosis = null) }
                search()
            }
        }
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
            val result = ippClient.query(endpoint)
            val capabilities = result.getOrNull()

            if (capabilities == null) {
                // Kept, not dropped. It answered on a printer port, so something
                // is there; the user can still reach it through the system
                // dialog, and hiding it would leave the screen contradicting
                // itself about what was found.
                _state.update { current ->
                    current.copy(
                        printers = current.printers.map {
                            if (it.endpoint.key == endpoint.key) {
                                it.copy(
                                    identifying = false,
                                    problem = result.exceptionOrNull()?.message
                                        ?: "It did not answer as a printer.",
                                )
                            } else {
                                it
                            }
                        },
                    )
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
                        if (it.endpoint.key == endpoint.key) {
                            FoundPrinter(confirmed, capabilities, identifying = false)
                        } else {
                            it
                        }
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

    /** Tries again to identify a device that did not answer the first time. */
    fun retry(printer: FoundPrinter) {
        _state.update { current ->
            current.copy(printers = current.printers.filterNot { it.endpoint.key == printer.endpoint.key })
        }
        identify(printer.endpoint)
    }

    /** Chooses a printer for printing. Only confirmed printers can be selected. */
    fun select(printer: FoundPrinter) {
        val capabilities = printer.capabilities ?: return
        selectedPrinter.select(ChosenPrinter(printer.endpoint, capabilities))
    }

    private companion object {
        const val IPP_PORT = 631
    }
}
