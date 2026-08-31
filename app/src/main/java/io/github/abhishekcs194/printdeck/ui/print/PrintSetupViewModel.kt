package io.github.abhishekcs194.printdeck.ui.print

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.abhishekcs194.printdeck.core.model.ColorMode
import io.github.abhishekcs194.printdeck.data.ChosenPrinter
import io.github.abhishekcs194.printdeck.data.PendingJob
import io.github.abhishekcs194.printdeck.data.SelectedPrinter
import io.github.abhishekcs194.printdeck.print.ipp.IppJob
import io.github.abhishekcs194.printdeck.print.ipp.IppPrintOptions
import io.github.abhishekcs194.printdeck.print.ipp.IppPrinter
import io.github.abhishekcs194.printdeck.print.ipp.PrinterCapabilities
import io.github.abhishekcs194.printdeck.print.system.PrintJobSpec
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PrintSetupViewModel @Inject constructor(
    private val pendingJob: PendingJob,
    private val selectedPrinter: SelectedPrinter,
    private val ippPrinter: IppPrinter,
) : ViewModel() {

    enum class Stage { READY, SENDING, PRINTING, DONE, FAILED }

    data class UiState(
        val spec: PrintJobSpec? = null,
        val printer: ChosenPrinter? = null,
        val options: IppPrintOptions = IppPrintOptions(),
        val stage: Stage = Stage.READY,
        val job: IppJob? = null,
        val error: String? = null,
    ) {
        /** True when the printer needs the document rasterised before sending. */
        val needsRaster: Boolean get() = printer?.capabilities?.supportsPdf == false

        val canPrint: Boolean get() = printer != null && spec != null && stage == Stage.READY
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        val spec = pendingJob.spec
        _state.update {
            it.copy(
                spec = spec,
                // The ink already chosen in the editor carries through, rather
                // than resetting to a default the user has to set twice.
                options = it.options.copy(
                    colorMode = when (spec?.colorMode) {
                        ColorMode.COLOR -> IppPrintOptions.COLOR_MODE_COLOR
                        else -> IppPrintOptions.COLOR_MODE_MONOCHROME
                    },
                    media = spec?.paper?.ippKeyword,
                    // The imposition already decided the sheet orientation; the
                    // rasteriser needs it so a landscape sheet is turned to match
                    // the way paper feeds.
                    sheetIsLandscape = spec?.landscape == true,
                ),
            )
        }
        observeSelectedPrinter()
    }

    /**
     * Follows the selection rather than sampling it once.
     *
     * This screen stays on the back stack while the user picks a printer, so its
     * view model - and the state it holds - outlives the trip. Reading the
     * selection once at construction meant returning with a printer chosen and a
     * screen still insisting none was, which sent the user straight back to the
     * picker they had just used.
     */
    private fun observeSelectedPrinter() {
        viewModelScope.launch {
            selectedPrinter.current.collect { chosen ->
                _state.update { current ->
                    current.copy(
                        printer = chosen,
                        options = chosen?.let { current.options.supportedBy(it.capabilities) }
                            ?: current.options,
                    )
                }
            }
        }
    }

    /**
     * Drops any option this printer does not offer.
     *
     * Options are carried over from the editor and from a previously chosen
     * printer, and nothing guarantees the new one supports them. Sending a
     * keyword a printer does not recognise risks it rejecting the whole job.
     */
    private fun IppPrintOptions.supportedBy(capabilities: PrinterCapabilities): IppPrintOptions =
        copy(
            sides = sides.takeIf { it in capabilities.sides }
                ?: capabilities.sides.firstOrNull()
                ?: IppPrintOptions.SIDES_ONE_SIDED,
            colorMode = colorMode.takeIf { it in capabilities.colorModes }
                ?: capabilities.colorModes.firstOrNull()
                ?: IppPrintOptions.COLOR_MODE_MONOCHROME,
            quality = quality.takeIf { it in capabilities.printQualities }
                ?: IppPrintOptions.QUALITY_NORMAL,
            // Plain paper unless the user says otherwise. Leaving this unset
            // let the printer choose, which on a photo-capable machine can mean
            // laying down photo-density ink on copier paper.
            mediaType = mediaType?.takeIf { it in capabilities.mediaTypes }
                ?: capabilities.mediaTypes.firstOrNull { it == PLAIN_PAPER }
                ?: capabilities.mediaTypes.firstOrNull { it == MEDIA_AUTO },
        )

    fun updateOptions(transform: (IppPrintOptions) -> IppPrintOptions) =
        _state.update { it.copy(options = transform(it.options)) }

    fun print() {
        val current = _state.value
        val spec = current.spec ?: return
        val printer = current.printer ?: return
        if (!current.canPrint) return

        viewModelScope.launch {
            _state.update { it.copy(stage = Stage.SENDING, error = null) }

            ippPrinter.print(
                endpoint = printer.endpoint,
                capabilities = printer.capabilities,
                pdf = spec.document,
                jobName = spec.name,
                options = current.options,
            )
                .onSuccess { job ->
                    _state.update { it.copy(stage = Stage.PRINTING, job = job) }
                    monitor(printer, job.id)
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(
                            stage = Stage.FAILED,
                            error = failure.message ?: "The printer refused the job.",
                        )
                    }
                }
        }
    }

    /**
     * Follows the job until the printer says it is finished.
     *
     * Polled rather than pushed because IPP has no notification a phone can
     * subscribe to without running a listener. Giving up after a while is
     * deliberate: a job can sit waiting for paper indefinitely, and a spinner
     * that never stops is worse than saying it has been sent.
     */
    private suspend fun monitor(printer: ChosenPrinter, jobId: Int) {
        repeat(MAX_POLLS) {
            delay(POLL_INTERVAL_MS)
            val job = ippPrinter.jobStatus(printer.endpoint, jobId).getOrNull() ?: return@repeat
            _state.update { it.copy(job = job) }

            if (job.isFinished) {
                _state.update {
                    it.copy(
                        stage = if (job.failed) Stage.FAILED else Stage.DONE,
                        error = if (job.failed) {
                            job.stateReasons.joinToString().ifEmpty { "The printer stopped the job." }
                        } else {
                            null
                        },
                    )
                }
                return
            }
        }
        // Still going after the polling window; it is with the printer now.
        _state.update { it.copy(stage = Stage.DONE) }
    }

    fun done() {
        pendingJob.clear()
        _state.update { it.copy(stage = Stage.READY, job = null, error = null) }
    }

    private companion object {
        /** The IPP keyword for ordinary copier paper. */
        const val PLAIN_PAPER = "stationery"
        const val MEDIA_AUTO = "auto"

        const val POLL_INTERVAL_MS = 1_500L

        /** About a minute of following the job before leaving it to the printer. */
        const val MAX_POLLS = 40
    }
}
