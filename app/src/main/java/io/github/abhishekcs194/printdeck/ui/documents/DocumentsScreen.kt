package io.github.abhishekcs194.printdeck.ui.documents

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.abhishekcs194.printdeck.core.design.PrintDeckIcons
import io.github.abhishekcs194.printdeck.core.design.component.ButtonSize
import io.github.abhishekcs194.printdeck.core.design.component.ButtonVariant
import io.github.abhishekcs194.printdeck.core.design.component.EmptyState
import io.github.abhishekcs194.printdeck.core.design.component.Notice
import io.github.abhishekcs194.printdeck.core.design.component.PrintDeckButton
import io.github.abhishekcs194.printdeck.core.design.component.ScreenHeader
import io.github.abhishekcs194.printdeck.core.design.component.Section
import io.github.abhishekcs194.printdeck.core.design.theme.PrintDeckTheme
import io.github.abhishekcs194.printdeck.core.design.theme.Radius
import io.github.abhishekcs194.printdeck.core.design.theme.Spacing
import io.github.abhishekcs194.printdeck.data.LoadedDocument

/**
 * The way in.
 *
 * Documents arrive only through the system picker, the photo picker, or a share
 * from another app — all permissionless. There is no file browser of our own,
 * and deliberately no "all files" permission: Google restricts that to file
 * managers and backup tools, and a printing app asking for it would be refused.
 */
@Composable
fun DocumentsScreen(
    onDocumentOpened: (LoadedDocument) -> Unit,
    viewModel: DocumentsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val pickDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::openDocument) }

    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_IMAGES),
    ) { uris -> if (uris.isNotEmpty()) viewModel.openImages(uris) }

    // Opening a document is a one-shot event; clear it so returning to this
    // screen does not immediately bounce forward into the editor again.
    LaunchedEffect(state.document) {
        state.document?.let {
            onDocumentOpened(it)
            viewModel.consumeOpenedDocument()
        }
    }

    DocumentsContent(
        state = state,
        onOpenFile = { pickDocument.launch(arrayOf(PDF_MIME)) },
        onOpenImages = {
            pickImages.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        },
        onSelect = onDocumentOpened,
    )
}

/**
 * The screen without its plumbing.
 *
 * Separated so it can be rendered from a test with made-up state, and so the
 * layout has no way to reach a view model or launch an intent by accident.
 */
@Composable
fun DocumentsContent(
    state: DocumentsViewModel.UiState,
    onOpenFile: () -> Unit,
    onOpenImages: () -> Unit,
    onSelect: (LoadedDocument) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PrintDeckTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        ScreenHeader(
            title = "Documents",
            subtitle = "Pick a file to lay out and print.",
        )

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            PrintDeckButton(
                text = "Open file",
                icon = PrintDeckIcons.FilePdf,
                onClick = onOpenFile,
                enabled = !state.loading,
            )
            PrintDeckButton(
                text = "Open images",
                icon = PrintDeckIcons.Images,
                variant = ButtonVariant.Outline,
                onClick = onOpenImages,
                enabled = !state.loading,
            )
        }

        state.error?.let { message ->
            Notice(
                title = "Could not open that file",
                body = message,
                icon = PrintDeckIcons.Warning,
                tone = colors.danger,
            )
        }

        when {
            state.loading -> LoadingCard()
            state.recents.isEmpty() -> EmptyState(
                icon = PrintDeckIcons.FilePdf,
                title = "Nothing open yet",
                body = "Open a PDF or some images, or share them to PrintDeck from another app.",
                action = {
                    PrintDeckButton(
                        text = "Open file",
                        icon = PrintDeckIcons.Plus,
                        onClick = onOpenFile,
                    )
                },
            )

            else -> Section(title = "Recent", contentPadding = false) {
                state.recents.forEachIndexed { index, document ->
                    if (index > 0) HorizontalDivider(thickness = 1.dp, color = colors.border)
                    RecentRow(document = document, onClick = { onSelect(document) })
                }
            }
        }
    }
}

@Composable
private fun RecentRow(document: LoadedDocument, onClick: () -> Unit) {
    val colors = PrintDeckTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(
            modifier = Modifier
                .size(THUMBNAIL)
                .clip(RoundedCornerShape(Radius.md))
                .background(colors.muted),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(
                    if (document.convertedFromImages) PrintDeckIcons.Images else PrintDeckIcons.FilePdf,
                ),
                contentDescription = null,
                tint = colors.mutedForeground,
                modifier = Modifier.size(ROW_ICON),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = document.displayName,
                style = MaterialTheme.typography.titleSmall,
                color = colors.foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (document.pageCount == 1) "1 page" else "${document.pageCount} pages",
                style = MaterialTheme.typography.bodySmall,
                color = colors.mutedForeground,
            )
        }
        Icon(
            painter = painterResource(PrintDeckIcons.CaretRight),
            contentDescription = null,
            tint = colors.mutedForeground,
            modifier = Modifier.size(ROW_ICON),
        )
    }
}

@Composable
private fun LoadingCard() {
    val colors = PrintDeckTheme.colors
    Section {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(SPINNER),
                strokeWidth = 2.dp,
                color = colors.primary,
            )
            Text(
                text = "Opening document…",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.mutedForeground,
            )
        }
    }
}

private const val PDF_MIME = "application/pdf"
private const val MAX_IMAGES = 50
private val THUMBNAIL = 40.dp
private val ROW_ICON = 20.dp
private val SPINNER = 20.dp
