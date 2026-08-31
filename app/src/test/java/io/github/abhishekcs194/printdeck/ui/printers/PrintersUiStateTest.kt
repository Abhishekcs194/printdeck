package io.github.abhishekcs194.printdeck.ui.printers

import com.google.common.truth.Truth.assertThat
import io.github.abhishekcs194.printdeck.print.ipp.PrinterCapabilities
import io.github.abhishekcs194.printdeck.print.ipp.discovery.DiscoverySource
import io.github.abhishekcs194.printdeck.print.ipp.discovery.DiscoveryDiagnosis
import io.github.abhishekcs194.printdeck.print.ipp.discovery.PrinterDiscovery
import io.github.abhishekcs194.printdeck.print.ipp.discovery.PrinterEndpoint
import org.junit.Test

/**
 * The diagnosis and the list are computed from different things — one from what
 * discovery turned up, the other from what survived identification — so they can
 * disagree. These tests pin the rule that decides when the diagnosis is allowed
 * to speak, after a build shipped a screen reading "1 printer found" above an
 * empty list.
 */
class PrintersUiStateTest {

    private fun endpoint(address: String = "192.168.1.50") =
        PrinterEndpoint(address, 631, DiscoverySource.SCAN)

    private fun found(confirmed: Boolean) = PrintersViewModel.FoundPrinter(
        endpoint = endpoint(),
        capabilities = if (confirmed) PrinterCapabilities(null, "A printer", "idle") else null,
        identifying = false,
    )

    @Test
    fun `a find is never announced above an empty list`() {
        // The exact contradiction that reached a device.
        val state = PrintersViewModel.UiState(
            searching = false,
            printers = emptyList(),
            diagnosis = DiscoveryDiagnosis.Found(count = 1),
        )
        assertThat(state.diagnosisToShow).isNull()
    }

    @Test
    fun `a failure is not explained above a populated list`() {
        val state = PrintersViewModel.UiState(
            searching = false,
            printers = listOf(found(confirmed = true)),
            diagnosis = DiscoveryDiagnosis.NotFoundOnThisNetwork("192.168.1.0/24"),
        )
        assertThat(state.diagnosisToShow).isNull()
    }

    @Test
    fun `a failure is explained when there is nothing to act on`() {
        val diagnosis = DiscoveryDiagnosis.PrinterOnAnotherNetwork(
            yourNetwork = "192.168.1.0/24",
            otherRouters = listOf("192.168.101.1"),
        )
        val state = PrintersViewModel.UiState(
            searching = false,
            printers = emptyList(),
            diagnosis = diagnosis,
        )
        assertThat(state.diagnosisToShow).isEqualTo(diagnosis)
    }

    @Test
    fun `nothing is explained while the search is still running`() {
        // Saying "not found" mid-search would be both wrong and alarming.
        val state = PrintersViewModel.UiState(
            searching = true,
            printers = emptyList(),
            diagnosis = DiscoveryDiagnosis.NotFoundOnThisNetwork("192.168.1.0/24"),
        )
        assertThat(state.diagnosisToShow).isNull()
    }

    @Test
    fun `a device that would not identify is still listed`() {
        // It answered on a printer port, so something is there. Dropping it is
        // what produced a screen claiming a find with nothing on it.
        val state = PrintersViewModel.UiState(
            searching = false,
            printers = listOf(found(confirmed = false)),
            diagnosis = DiscoveryDiagnosis.Found(count = 1),
        )
        assertThat(state.printers).hasSize(1)
        assertThat(state.printers.single().confirmed).isFalse()
        assertThat(state.diagnosisToShow).isNull()
    }

    @Test
    fun `progress names the ring being searched`() {
        val wider = PrintersViewModel.UiState(
            phase = PrinterDiscovery.Phase.SEARCHING_WIDER,
            networksSearched = 3,
            networksTotal = 8,
        )
        assertThat(wider.progressLabel).contains("other networks")
        assertThat(wider.progressLabel).contains("3 of 8")

        val nearby = PrintersViewModel.UiState(phase = PrinterDiscovery.Phase.SEARCHING_NEARBY)
        assertThat(nearby.progressLabel).contains("this network")
    }
}
