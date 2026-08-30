package io.github.abhishekcs194.printdeck.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.abhishekcs194.printdeck.core.design.theme.PrintDeckTheme
import io.github.abhishekcs194.printdeck.data.LoadedDocument
import io.github.abhishekcs194.printdeck.ui.documents.DocumentsScreen
import io.github.abhishekcs194.printdeck.ui.documents.DocumentsViewModel
import io.github.abhishekcs194.printdeck.ui.editor.LayoutEditorScreen
import io.github.abhishekcs194.printdeck.ui.print.PrintSetupScreen
import io.github.abhishekcs194.printdeck.ui.printers.PrintersScreen

private object Routes {
    const val DOCUMENTS = "documents"
    const val EDITOR = "editor"
    const val PRINTERS = "printers"
    const val PRINT = "print"
}

/**
 * Root of the app.
 *
 * The opened document is held here rather than passed as a navigation argument.
 * It carries a file handle and a list of page sizes, which do not belong in a
 * route — and serialising them only to parse them straight back would be work
 * done for the sake of the framework rather than the app.
 *
 * @param sharedUri a document sent from another app, handled once on arrival.
 */
@Composable
fun PrintDeckApp(sharedUri: Uri? = null) {
    val navController = rememberNavController()
    var document by remember { mutableStateOf<LoadedDocument?>(null) }
    val colors = PrintDeckTheme.colors

    NavHost(
        navController = navController,
        startDestination = Routes.DOCUMENTS,
        modifier = Modifier.fillMaxSize().background(colors.background),
    ) {
        composable(Routes.DOCUMENTS) { entry ->
            val viewModel: DocumentsViewModel = hiltViewModel(entry)

            // A shared document goes straight into the editor: someone who chose
            // "print with PrintDeck" has already picked their file, and showing
            // them a picker would be asking twice.
            LaunchedEffect(sharedUri) { sharedUri?.let(viewModel::openDocument) }

            DocumentsScreen(
                onDocumentOpened = { opened ->
                    document = opened
                    navController.navigate(Routes.EDITOR)
                },
                onOpenPrinters = { navController.navigate(Routes.PRINTERS) },
                viewModel = viewModel,
            )
        }

        composable(Routes.PRINTERS) {
            PrintersScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.PRINT) {
            PrintSetupScreen(
                onBack = { navController.popBackStack() },
                onChoosePrinter = { navController.navigate(Routes.PRINTERS) },
                // Finishing returns to the documents list rather than the editor:
                // the job is done, and dropping the user back on the settings
                // they just committed invites them to send it twice.
                onFinished = {
                    navController.popBackStack(Routes.DOCUMENTS, inclusive = false)
                },
            )
        }

        composable(Routes.EDITOR) {
            document?.let { opened ->
                LayoutEditorScreen(
                    document = opened,
                    onBack = { navController.popBackStack() },
                    onPrint = { navController.navigate(Routes.PRINT) },
                )
            }
        }
    }
}
