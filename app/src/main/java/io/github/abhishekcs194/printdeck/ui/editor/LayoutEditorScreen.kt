package io.github.abhishekcs194.printdeck.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.abhishekcs194.printdeck.core.design.PrintDeckIcons
import io.github.abhishekcs194.printdeck.core.design.component.ButtonSize
import io.github.abhishekcs194.printdeck.core.design.component.ButtonVariant
import io.github.abhishekcs194.printdeck.core.design.component.ChoiceChips
import io.github.abhishekcs194.printdeck.core.design.component.Notice
import io.github.abhishekcs194.printdeck.core.design.component.OptionRow
import io.github.abhishekcs194.printdeck.core.design.component.PrintDeckButton
import io.github.abhishekcs194.printdeck.core.design.component.PrintDeckIconButton
import io.github.abhishekcs194.printdeck.core.design.component.Section
import io.github.abhishekcs194.printdeck.core.design.component.SegmentedTabs
import io.github.abhishekcs194.printdeck.core.design.component.Stepper
import io.github.abhishekcs194.printdeck.core.design.theme.PrintDeckTheme
import io.github.abhishekcs194.printdeck.core.design.theme.Spacing
import io.github.abhishekcs194.printdeck.core.model.ImpositionMode
import io.github.abhishekcs194.printdeck.core.model.Margins
import io.github.abhishekcs194.printdeck.core.model.PageOrder
import io.github.abhishekcs194.printdeck.core.model.PaperSize
import io.github.abhishekcs194.printdeck.data.LoadedDocument

/** The four things this app does, as the user thinks of them. */
private enum class LayoutTab(val label: String) {
    NUp("Pages per sheet"),
    Booklet("Booklet"),
    Split("Split"),
    Poster("Poster"),
}

private val ImpositionMode.tab: LayoutTab
    get() = when (this) {
        is ImpositionMode.NUp -> LayoutTab.NUp
        is ImpositionMode.Booklet -> LayoutTab.Booklet
        is ImpositionMode.Split -> LayoutTab.Split
        is ImpositionMode.Poster -> LayoutTab.Poster
    }

@Composable
fun LayoutEditorScreen(
    document: LoadedDocument,
    onBack: () -> Unit,
    onPrint: () -> Unit,
    viewModel: LayoutEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = PrintDeckTheme.colors

    LaunchedEffect(document.file) { viewModel.setDocument(document) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            PrintDeckIconButton(
                icon = PrintDeckIcons.ArrowLeft,
                contentDescription = "Back",
                onClick = onBack,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = document.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.foreground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = state.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.mutedForeground,
                )
            }
            PrintDeckButton(
                text = "Print",
                icon = PrintDeckIcons.Printer,
                size = ButtonSize.Small,
                onClick = onPrint,
                enabled = state.sheetCount > 0,
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            SheetPreview(state = state, onShowSheet = viewModel::showSheet)

            state.error?.let { message ->
                Notice(
                    title = "That layout will not fit",
                    body = message,
                    icon = PrintDeckIcons.Warning,
                    tone = colors.danger,
                )
            }

            SegmentedTabs(
                options = LayoutTab.entries,
                selected = state.settings.mode.tab,
                onSelect = { tab -> viewModel.setMode(tab.defaultMode()) },
                label = { it.label },
            )

            when (val mode = state.settings.mode) {
                is ImpositionMode.NUp -> NUpControls(mode, viewModel)
                is ImpositionMode.Booklet -> BookletControls(mode, viewModel)
                is ImpositionMode.Split -> SplitControls(mode, viewModel)
                is ImpositionMode.Poster -> PosterControls(mode, viewModel)
            }

            PaperControls(state, viewModel)
        }
    }
}

private fun LayoutTab.defaultMode(): ImpositionMode = when (this) {
    LayoutTab.NUp -> ImpositionMode.NUp(columns = 2, rows = 1)
    LayoutTab.Booklet -> ImpositionMode.Booklet()
    LayoutTab.Split -> ImpositionMode.Split(columns = 2, rows = 1)
    LayoutTab.Poster -> ImpositionMode.Poster(columns = 2, rows = 2)
}

/** The familiar N-up grids, offered as counts rather than as columns and rows. */
private val NUP_GRIDS = listOf(1 to 1, 2 to 1, 2 to 2, 3 to 2, 3 to 3, 4 to 4)

@Composable
private fun NUpControls(mode: ImpositionMode.NUp, viewModel: LayoutEditorViewModel) {
    Section(title = "Pages per sheet") {
        ChoiceChips(
            options = NUP_GRIDS,
            selected = mode.columns to mode.rows,
            onSelect = { (columns, rows) ->
                viewModel.setMode(mode.copy(columns = columns, rows = rows))
            },
            label = { (columns, rows) -> "${columns * rows}" },
        )

        OptionRow(
            label = "Reading order",
            description = "Which way pages flow across the sheet.",
        ) {
            SegmentedTabs(
                options = listOf(PageOrder.ACROSS_THEN_DOWN, PageOrder.DOWN_THEN_ACROSS),
                selected = if (mode.order.isColumnMajor) PageOrder.DOWN_THEN_ACROSS else PageOrder.ACROSS_THEN_DOWN,
                onSelect = { viewModel.setMode(mode.copy(order = it)) },
                label = { if (it.isColumnMajor) "Down" else "Across" },
                modifier = Modifier.fillMaxWidth(CONTROL_WIDTH),
            )
        }

        OptionRow(label = "Gap between pages") {
            Stepper(
                value = mode.gutterPt.toInt(),
                onChange = { viewModel.setMode(mode.copy(gutterPt = it.toDouble())) },
                range = 0..48,
                suffix = "pt",
            )
        }

        OptionRow(
            label = "Draw borders",
            description = "A hairline around each page, useful for cutting.",
        ) {
            PrintDeckSwitch(mode.drawCellBorders) {
                viewModel.setMode(mode.copy(drawCellBorders = it))
            }
        }

        OptionRow(
            label = "Turn pages to fit",
            description = "Rotates pages when that fills the sheet better.",
        ) {
            PrintDeckSwitch(mode.autoRotate) { viewModel.setMode(mode.copy(autoRotate = it)) }
        }
    }
}

@Composable
private fun BookletControls(mode: ImpositionMode.Booklet, viewModel: LayoutEditorViewModel) {
    Section(
        title = "Booklet",
        description = "Print double-sided, fold in half, staple the spine.",
    ) {
        OptionRow(
            label = "Creep compensation",
            description = "Shifts inner pages towards the spine so margins stay even after trimming.",
        ) {
            Stepper(
                value = mode.creepPt.toInt(),
                onChange = { viewModel.setMode(mode.copy(creepPt = it.toDouble())) },
                range = 0..24,
                suffix = "pt",
            )
        }

        OptionRow(
            label = "Right-to-left",
            description = "For Arabic, Hebrew and Japanese documents.",
        ) {
            PrintDeckSwitch(mode.rightToLeft) { viewModel.setMode(mode.copy(rightToLeft = it)) }
        }
    }
}

@Composable
private fun SplitControls(mode: ImpositionMode.Split, viewModel: LayoutEditorViewModel) {
    Section(
        title = "Split pages",
        description = "Cuts each page into separate sheets — a two-page spread becomes two pages.",
    ) {
        OptionRow(label = "Columns") {
            Stepper(
                value = mode.columns,
                onChange = { viewModel.setMode(mode.copy(columns = it)) },
                range = 1..4,
            )
        }
        OptionRow(label = "Rows") {
            Stepper(
                value = mode.rows,
                onChange = { viewModel.setMode(mode.copy(rows = it)) },
                range = 1..4,
            )
        }
        OptionRow(
            label = "Overlap",
            description = "Extra margin either side of the cut, so nothing is lost.",
        ) {
            Stepper(
                value = mode.overlapPt.toInt(),
                onChange = { viewModel.setMode(mode.copy(overlapPt = it.toDouble())) },
                range = 0..36,
                suffix = "pt",
            )
        }
    }
}

@Composable
private fun PosterControls(mode: ImpositionMode.Poster, viewModel: LayoutEditorViewModel) {
    Section(
        title = "Poster",
        description = "Enlarges one page across several sheets to trim and tape together.",
    ) {
        OptionRow(label = "Sheets across") {
            Stepper(
                value = mode.columns,
                onChange = { viewModel.setMode(mode.copy(columns = it)) },
                range = 1..6,
            )
        }
        OptionRow(label = "Sheets down") {
            Stepper(
                value = mode.rows,
                onChange = { viewModel.setMode(mode.copy(rows = it)) },
                range = 1..6,
            )
        }
        OptionRow(
            label = "Overlap",
            description = "A flap on each seam so sheets can be lapped rather than butted.",
        ) {
            Stepper(
                value = mode.overlapPt.toInt(),
                onChange = { viewModel.setMode(mode.copy(overlapPt = it.toDouble())) },
                range = 0..72,
                suffix = "pt",
            )
        }
        OptionRow(label = "Assembly marks") {
            PrintDeckSwitch(mode.assemblyMarks) {
                viewModel.setMode(mode.copy(assemblyMarks = it))
            }
        }
    }
}

@Composable
private fun PaperControls(
    state: LayoutEditorViewModel.UiState,
    viewModel: LayoutEditorViewModel,
) {
    Section(title = "Paper") {
        ChoiceChips(
            options = listOf(PaperSize.A4, PaperSize.LETTER, PaperSize.A5, PaperSize.A3, PaperSize.LEGAL),
            selected = state.settings.sheet,
            onSelect = { paper -> viewModel.updateSettings { it.copy(sheet = paper) } },
            label = { it.displayName },
        )

        OptionRow(label = "Margin") {
            Stepper(
                value = state.settings.margins.left.pointsToMillimetres(),
                onChange = { millimetres ->
                    viewModel.updateSettings { it.copy(margins = Margins.uniformMm(millimetres.toDouble())) }
                },
                range = 0..30,
                suffix = "mm",
            )
        }

        OptionRow(
            label = "Binding margin",
            description = "Extra space on the bound edge, so text does not vanish into the fold.",
        ) {
            Stepper(
                value = state.settings.bindingGutterPt.pointsToMillimetres(),
                onChange = { millimetres ->
                    viewModel.updateSettings {
                        it.copy(bindingGutterPt = millimetres * POINTS_PER_MM)
                    }
                },
                range = 0..30,
                suffix = "mm",
            )
        }
    }
}

@Composable
private fun PrintDeckSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = PrintDeckTheme.colors
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = colors.onPrimary,
            checkedTrackColor = colors.primary,
            uncheckedThumbColor = colors.mutedForeground,
            uncheckedTrackColor = colors.muted,
            uncheckedBorderColor = colors.border,
        ),
    )
}

private const val POINTS_PER_MM = 72.0 / 25.4
private const val CONTROL_WIDTH = 0.55f

private fun Double.pointsToMillimetres(): Int = (this / POINTS_PER_MM).toInt()
