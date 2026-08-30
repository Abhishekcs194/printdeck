package io.github.abhishekcs194.printdeck.ui.printers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.abhishekcs194.printdeck.core.design.PrintDeckIcons
import io.github.abhishekcs194.printdeck.core.design.component.ButtonSize
import io.github.abhishekcs194.printdeck.core.design.component.ButtonVariant
import io.github.abhishekcs194.printdeck.core.design.component.Notice
import io.github.abhishekcs194.printdeck.core.design.component.Pill
import io.github.abhishekcs194.printdeck.core.design.component.PrintDeckButton
import io.github.abhishekcs194.printdeck.core.design.component.PrintDeckIconButton
import io.github.abhishekcs194.printdeck.core.design.component.ScreenHeader
import io.github.abhishekcs194.printdeck.core.design.component.Section
import io.github.abhishekcs194.printdeck.core.design.component.StatusDot
import io.github.abhishekcs194.printdeck.core.design.theme.PrintDeckTheme
import io.github.abhishekcs194.printdeck.core.design.theme.Radius
import io.github.abhishekcs194.printdeck.core.design.theme.Spacing
import io.github.abhishekcs194.printdeck.print.ipp.PrinterCapabilities

/**
 * Finds printers, and says why when it cannot.
 *
 * This is where the app's own discovery lives, as opposed to the platform print
 * dialog's. It searches beyond the network the phone is attached to, confirms
 * each result by asking it over IPP, and shows what the printer actually
 * supports — including the options the system dialog has no way to express.
 */
@Composable
fun PrintersScreen(
    onBack: () -> Unit,
    viewModel: PrintersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = PrintDeckTheme.colors
    var manualAddress by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PrintDeckIconButton(
                icon = PrintDeckIcons.ArrowLeft,
                contentDescription = "Back",
                onClick = onBack,
            )
            Box(modifier = Modifier.weight(1f))
            PrintDeckButton(
                text = if (state.searching) "Searching…" else "Search again",
                icon = PrintDeckIcons.Search,
                variant = ButtonVariant.Outline,
                size = ButtonSize.Small,
                onClick = viewModel::search,
                enabled = !state.searching,
            )
        }

        ScreenHeader(
            title = "Printers",
            subtitle = "PrintDeck looks beyond the network this phone is on.",
        )

        if (state.searching) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(SPINNER),
                    strokeWidth = 2.dp,
                    color = colors.primary,
                )
                Text(
                    text = state.progressLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.mutedForeground,
                )
            }
        }

        if (state.printers.isNotEmpty()) {
            Section(title = "Found", contentPadding = false) {
                state.printers.forEachIndexed { index, printer ->
                    if (index > 0) HorizontalDivider(thickness = 1.dp, color = colors.border)
                    PrinterRow(printer)
                }
            }
        }

        // Only explain a failure once the search has actually finished; saying
        // "nothing found" while still looking would be wrong and alarming.
        if (!state.searching && state.printers.isEmpty()) {
            state.diagnosis?.let { diagnosis ->
                Notice(
                    title = diagnosis.headline,
                    body = diagnosis.explanation,
                    bullets = diagnosis.suggestions,
                    icon = PrintDeckIcons.Warning,
                )
            }
        }

        Section(
            title = "Add by address",
            description = "If your printer is on a network this phone cannot search, enter its address.",
        ) {
            OutlinedTextField(
                value = manualAddress,
                onValueChange = { manualAddress = it },
                placeholder = { Text("192.168.1.50") },
                singleLine = true,
                isError = state.manualEntryError != null,
                supportingText = state.manualEntryError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )
            PrintDeckButton(
                text = "Add printer",
                icon = PrintDeckIcons.Plus,
                variant = ButtonVariant.Outline,
                size = ButtonSize.Small,
                onClick = { viewModel.addManually(manualAddress) },
                enabled = manualAddress.isNotBlank(),
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }
    }
}

@Composable
private fun PrinterRow(printer: PrintersViewModel.FoundPrinter) {
    val colors = PrintDeckTheme.colors
    val capabilities = printer.capabilities

    Row(
        modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(
            modifier = Modifier
                .size(TILE)
                .clip(RoundedCornerShape(Radius.md))
                .background(colors.muted),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(PrintDeckIcons.Printer),
                contentDescription = null,
                tint = colors.mutedForeground,
                modifier = Modifier.size(ICON),
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = printer.title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.foreground,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                if (capabilities?.state != null) {
                    StatusDot(
                        color = if (capabilities.state == "idle") colors.success else colors.warning,
                    )
                }
                Text(
                    text = listOfNotNull(capabilities?.state, printer.endpoint.address)
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.mutedForeground,
                )
            }

            if (capabilities == null) {
                Text(
                    text = "Checking…",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.mutedForeground,
                )
            } else {
                Supplies(capabilities)
                Capabilities(capabilities)
            }
        }
    }
}

/** Ink levels, when the printer reports them. */
@Composable
private fun Supplies(capabilities: PrinterCapabilities) {
    val colors = PrintDeckTheme.colors
    if (capabilities.supplies.isEmpty()) return

    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        capabilities.supplies.forEach { supply ->
            Pill(
                text = "${supply.name} ${supply.percent}%",
                // An empty cartridge is the single most useful thing this screen
                // can tell someone before they send a job.
                tone = when {
                    supply.isEmpty -> colors.danger
                    supply.isLow -> colors.warning
                    else -> colors.success
                },
            )
        }
    }
}

/** What the printer can do — including options the system dialog cannot reach. */
@Composable
private fun Capabilities(capabilities: PrinterCapabilities) {
    val colors = PrintDeckTheme.colors
    val badges = buildList {
        if (capabilities.supportsDuplex) add("Two-sided")
        if (capabilities.printQualities.size > 1) add("${capabilities.printQualities.size} qualities")
        if (capabilities.mediaTypes.isNotEmpty()) add("${capabilities.mediaTypes.size} media types")
        if (!capabilities.supportsPdf) add("No PDF")
    }
    if (badges.isEmpty()) return

    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        badges.forEach { Pill(text = it, tone = colors.mutedForeground) }
    }
}

private val TILE = 40.dp
private val ICON = 20.dp
private val SPINNER = 18.dp
