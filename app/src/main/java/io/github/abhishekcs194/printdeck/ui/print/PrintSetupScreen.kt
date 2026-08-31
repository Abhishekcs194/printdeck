package io.github.abhishekcs194.printdeck.ui.print

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.abhishekcs194.printdeck.core.design.PrintDeckIcons
import io.github.abhishekcs194.printdeck.core.design.component.ButtonSize
import io.github.abhishekcs194.printdeck.core.design.component.ButtonVariant
import io.github.abhishekcs194.printdeck.core.design.component.ChoiceChips
import io.github.abhishekcs194.printdeck.core.design.component.EmptyState
import io.github.abhishekcs194.printdeck.core.design.component.Notice
import io.github.abhishekcs194.printdeck.core.design.component.OptionRow
import io.github.abhishekcs194.printdeck.core.design.component.Pill
import io.github.abhishekcs194.printdeck.core.design.component.PrintDeckButton
import io.github.abhishekcs194.printdeck.core.design.component.PrintDeckIconButton
import io.github.abhishekcs194.printdeck.core.design.component.ScreenHeader
import io.github.abhishekcs194.printdeck.core.design.component.Section
import io.github.abhishekcs194.printdeck.core.design.component.SegmentedTabs
import io.github.abhishekcs194.printdeck.core.design.component.Stepper
import io.github.abhishekcs194.printdeck.core.design.theme.PrintDeckTheme
import io.github.abhishekcs194.printdeck.core.design.theme.Spacing
import io.github.abhishekcs194.printdeck.print.ipp.IppPrintOptions
import io.github.abhishekcs194.printdeck.print.system.SystemPrinter
import io.github.abhishekcs194.printdeck.print.ipp.PrinterCapabilities

/**
 * The options screen the platform dialog cannot be.
 *
 * Every control here is built from what the printer said it supports, so it can
 * only ever offer real choices — no duplex on a simplex machine, no glossy on a
 * printer with one paper type. Several of these settings, print quality and
 * media type among them, have no representation in Android's print framework at
 * all and are reachable only this way.
 */
@Composable
fun PrintSetupScreen(
    onBack: () -> Unit,
    onChoosePrinter: () -> Unit,
    onFinished: () -> Unit,
    viewModel: PrintSetupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = PrintDeckTheme.colors
    // The platform print framework wants the Activity showing the dialog, so it
    // is built from the local context rather than injected.
    val context = LocalContext.current
    val systemPrinter = remember(context) { SystemPrinter(context) }

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
        }

        ScreenHeader(
            title = "Print",
            subtitle = state.spec?.let { "${it.sheetCount} sheets · ${it.paper.displayName}" }
                ?: "Nothing to print.",
        )

        val printer = state.printer
        if (printer == null) {
            NoPrinterChosen(
                onChoosePrinter = onChoosePrinter,
                onUseSystemDialog = state.spec?.let { spec ->
                    { systemPrinter.print(spec); onFinished() }
                },
            )
            return@Column
        }

        Section(title = "Printer") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = printer.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.foreground,
                    )
                    Text(
                        text = printer.endpoint.address,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.mutedForeground,
                    )
                }
                PrintDeckButton(
                    text = "Change",
                    variant = ButtonVariant.Outline,
                    size = ButtonSize.Small,
                    onClick = onChoosePrinter,
                )
            }

            if (state.needsRaster) {
                Text(
                    text = "This printer does not accept PDF, so PrintDeck converts the " +
                        "document before sending. Large jobs take a moment.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.mutedForeground,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
        }

        PrintOptions(printer.capabilities, state.options, viewModel)

        state.error?.let { message ->
            Notice(
                title = "The job did not print",
                body = message,
                icon = PrintDeckIcons.Warning,
                tone = colors.danger,
            )
        }

        PrintAction(
            state = state,
            printerTitle = printer.title,
            onPrint = viewModel::print,
            onDone = { viewModel.done(); onFinished() },
        )
    }
}

/**
 * Shown when nothing has been selected yet.
 *
 * The platform dialog is offered alongside, deliberately. Direct IPP exposes far
 * more, but only reaches printers that speak it; the platform path reaches
 * anything the phone already knows about, including Save as PDF. Dropping that
 * to gain options would be a poor trade.
 */
@Composable
private fun NoPrinterChosen(
    onChoosePrinter: () -> Unit,
    onUseSystemDialog: (() -> Unit)?,
) {
    EmptyState(
        icon = PrintDeckIcons.Printer,
        title = "No printer chosen",
        body = "Pick a printer and PrintDeck will offer everything it can actually do.",
        action = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                PrintDeckButton(
                    text = "Choose printer",
                    icon = PrintDeckIcons.Search,
                    onClick = onChoosePrinter,
                )
                onUseSystemDialog?.let { useDialog ->
                    PrintDeckButton(
                        text = "Use Android's print dialog",
                        variant = ButtonVariant.Ghost,
                        size = ButtonSize.Small,
                        onClick = useDialog,
                    )
                }
            }
        },
    )
}

/** The commit button, and what replaces it once a job is on its way. */
@Composable
private fun PrintAction(
    state: PrintSetupViewModel.UiState,
    printerTitle: String,
    onPrint: () -> Unit,
    onDone: () -> Unit,
) {
    val colors = PrintDeckTheme.colors

    when (state.stage) {
        PrintSetupViewModel.Stage.READY, PrintSetupViewModel.Stage.FAILED ->
            PrintDeckButton(
                text = "Print ${state.spec?.sheetCount ?: 0} sheets",
                icon = PrintDeckIcons.Printer,
                onClick = onPrint,
                enabled = state.canPrint || state.stage == PrintSetupViewModel.Stage.FAILED,
                modifier = Modifier.fillMaxWidth(),
            )

        PrintSetupViewModel.Stage.SENDING, PrintSetupViewModel.Stage.PRINTING ->
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
                    text = when (state.stage) {
                        PrintSetupViewModel.Stage.SENDING ->
                            if (state.needsRaster) "Converting and sending…" else "Sending…"
                        else -> "Printing · ${state.job?.state.orEmpty()}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.mutedForeground,
                )
            }

        PrintSetupViewModel.Stage.DONE -> {
            Notice(
                title = "Sent to $printerTitle",
                body = "The job is with the printer.",
                icon = PrintDeckIcons.Check,
                tone = colors.success,
            )
            PrintDeckButton(
                text = "Done",
                onClick = onDone,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Controls generated from the printer's own list of what it supports. */
@Composable
private fun PrintOptions(
    capabilities: PrinterCapabilities,
    options: IppPrintOptions,
    viewModel: PrintSetupViewModel,
) {
    val colors = PrintDeckTheme.colors

    Section(title = "Options") {
        OptionRow(label = "Copies") {
            Stepper(
                value = options.copies,
                onChange = { copies -> viewModel.updateOptions { it.copy(copies = copies) } },
                range = 1..COPY_LIMIT,
            )
        }

        // Each control appears only when the printer offers a real choice. A
        // duplex toggle on a simplex machine is a promise the hardware cannot keep.
        if (capabilities.supportsDuplex) {
            OptionRow(
                label = "Two-sided",
                description = "Long edge flips like a book, short edge like a notepad.",
            ) {
                SegmentedTabs(
                    options = capabilities.sides,
                    selected = options.sides,
                    onSelect = { sides -> viewModel.updateOptions { it.copy(sides = sides) } },
                    label = { it.sidesLabel() },
                    modifier = Modifier.fillMaxWidth(SIDES_WIDTH),
                )
            }
        }

        if (capabilities.printQualities.size > 1) {
            OptionRow(
                label = "Quality",
                description = "Draft uses noticeably less ink.",
            ) {
                SegmentedTabs(
                    options = capabilities.printQualities,
                    selected = options.quality,
                    onSelect = { quality -> viewModel.updateOptions { it.copy(quality = quality) } },
                    label = { it.replaceFirstChar(Char::uppercase) },
                    modifier = Modifier.fillMaxWidth(QUALITY_WIDTH),
                )
            }
        }

        if (capabilities.mediaTypes.size > 1) {
            Text(
                text = "Paper type",
                style = MaterialTheme.typography.titleSmall,
                color = colors.foreground,
                modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.sm),
            )
            ChoiceChips(
                // Everyday paper first. A printer lists its exotic photo stock
                // in whatever order it likes, and the one people actually use
                // was ending up off the end of a scrolling row.
                options = capabilities.mediaTypes.sortedBy { it.mediaRank() },
                // Shows exactly what will be sent. Falling back to the first
                // entry for display meant the chip claimed "Platinum" while the
                // job carried no paper type at all.
                selected = options.mediaType,
                onSelect = { type -> viewModel.updateOptions { it.copy(mediaType = type) } },
                label = { it.mediaLabel() },
            )
        }

        if (capabilities.supplies.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(top = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                capabilities.supplies.forEach { supply ->
                    Pill(
                        text = "${supply.name} ${supply.percent}%",
                        tone = when {
                            supply.isEmpty -> colors.danger
                            supply.isLow -> colors.warning
                            else -> colors.success
                        },
                    )
                }
            }
        }
    }
}

private fun String.sidesLabel(): String = when (this) {
    IppPrintOptions.SIDES_ONE_SIDED -> "One side"
    IppPrintOptions.SIDES_LONG_EDGE -> "Long edge"
    IppPrintOptions.SIDES_SHORT_EDGE -> "Short edge"
    else -> this
}

/**
 * What people call the paper, keyed by what IPP calls it.
 *
 * The standard vocabulary is not the everyday one — plain copier paper is
 * "stationery" in IPP, which nobody would recognise as the thing in their tray.
 * Vendor keywords are worse: mechanically trimming `com.canon.mtsemisuper`
 * yields "Semisuper", which is not a kind of paper at all.
 */
private val MEDIA_LABELS = mapOf(
    // PWG standard keywords.
    "stationery" to "Plain paper",
    "auto" to "Automatic",
    "photographic" to "Photo paper",
    "envelope" to "Envelope",
    "labels" to "Labels",
    "cardstock" to "Card",
    "transparency" to "Transparency",
    "stationery-letterhead" to "Letterhead",

    // Canon's own, as they are named on the packet.
    "com.canon.mtglossy" to "Glossy photo",
    "com.canon.mtmat" to "Matte photo",
    "com.canon.mtluster" to "Luster photo",
    "com.canon.mtplatinum" to "Pro Platinum photo",
    "com.canon.mtsemisuper" to "Semi-gloss photo",
    "com.canon.mthires" to "High resolution",
    "com.canon.mtgreeting" to "Greeting card",
)

/**
 * Unrecognised keywords keep their trimmed form rather than being given an
 * invented name, so an unfamiliar printer shows something traceable instead of
 * something confidently wrong.
 */
private fun String.mediaLabel(): String = MEDIA_LABELS[this]
    ?: substringAfterLast('.').removePrefix("mt").replaceFirstChar(Char::uppercase)

/**
 * Everyday paper first.
 *
 * A printer lists its speciality photo stock in whatever order it likes, and the
 * one people actually use was ending up off the end of a scrolling row.
 */
private val MEDIA_ORDER = listOf("stationery", "auto", "stationery-letterhead", "photographic")

private fun String.mediaRank(): Int =
    MEDIA_ORDER.indexOf(this).takeIf { it >= 0 } ?: MEDIA_ORDER.size

private val SPINNER = 20.dp
private const val COPY_LIMIT = 99
private const val SIDES_WIDTH = 0.62f
private const val QUALITY_WIDTH = 0.62f
