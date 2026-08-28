package io.github.abhishekcs194.printdeck.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
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
import io.github.abhishekcs194.printdeck.core.model.PaperSize
import io.github.abhishekcs194.printdeck.data.LoadedDocument

/** The four things this app does, as the user thinks of them. */
private enum class LayoutTab(val label: String) {
    NUp("Pages"),
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
    var settingsExpanded by rememberSaveable { mutableStateOf(true) }

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

        SheetPreview(
            state = state,
            onShowSheet = viewModel::showSheet,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.sm),
        )

        state.error?.let { message ->
            Notice(
                title = "That layout will not fit",
                body = message,
                icon = PrintDeckIcons.Warning,
                tone = colors.danger,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            )
        }

        SettingsHandle(expanded = settingsExpanded, onToggle = { settingsExpanded = !settingsExpanded })

        AnimatedVisibility(
            visible = settingsExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(
                modifier = Modifier
                    // Capped so the preview always keeps a usable share of the
                    // screen, however many controls a mode has.
                    .heightIn(max = SETTINGS_MAX_HEIGHT)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.lg)
                    .padding(bottom = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
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
}

/**
 * The grab handle between preview and settings.
 *
 * On a phone the controls crowd out the very thing they are adjusting, so this
 * folds them away. Shaped like a sheet handle and spanning the full width, since
 * a small chevron is a poor target for a thumb.
 */
@Composable
private fun SettingsHandle(expanded: Boolean, onToggle: () -> Unit) {
    val colors = PrintDeckTheme.colors
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 0f else HALF_TURN,
        label = "handleRotation",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = Spacing.sm),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(PrintDeckIcons.CaretDown),
            contentDescription = if (expanded) "Hide settings" else "Show settings",
            tint = colors.mutedForeground,
            modifier = Modifier.size(HANDLE_ICON).rotate(rotation),
        )
        Text(
            text = if (expanded) "Hide settings" else "Show settings",
            style = MaterialTheme.typography.labelLarge,
            color = colors.mutedForeground,
            modifier = Modifier.padding(start = Spacing.sm),
        )
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

        // Across and down describe the same traversal when the grid is a single
        // row or column, so the control is hidden there rather than offered and
        // ignored - a setting that visibly does nothing reads as a broken app.
        if (mode.columns > 1 && mode.rows > 1) {
            OptionRow(
                label = "Reading order",
                description = "Which way pages flow across the sheet.",
            ) {
                SegmentedTabs(
                    options = listOf(false, true),
                    selected = mode.order.isColumnMajor,
                    onSelect = { columnMajor ->
                        viewModel.setMode(mode.copy(order = mode.order.withColumnMajor(columnMajor)))
                    },
                    label = { columnMajor -> if (columnMajor) "Down" else "Across" },
                    modifier = Modifier.fillMaxWidth(CONTROL_WIDTH),
                )
            }
        }

        if (mode.columns > 1) {
            OptionRow(
                label = "Right to left",
                description = "Starts pages on the right, for Arabic and Hebrew documents.",
            ) {
                PrintDeckSwitch(mode.order.isRightToLeft) { rightToLeft ->
                    viewModel.setMode(mode.copy(order = mode.order.withRightToLeft(rightToLeft)))
                }
            }
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
private const val HALF_TURN = 180f
private val HANDLE_ICON = 16.dp
private val SETTINGS_MAX_HEIGHT = 360.dp

private fun Double.pointsToMillimetres(): Int = (this / POINTS_PER_MM).toInt()
