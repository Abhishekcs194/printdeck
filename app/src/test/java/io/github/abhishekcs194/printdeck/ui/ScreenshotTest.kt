package io.github.abhishekcs194.printdeck.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import io.github.abhishekcs194.printdeck.core.design.theme.PrintDeckTheme
import io.github.abhishekcs194.printdeck.data.LoadedDocument
import io.github.abhishekcs194.printdeck.core.model.SizePt
import io.github.abhishekcs194.printdeck.ui.documents.DocumentsContent
import io.github.abhishekcs194.printdeck.ui.documents.DocumentsViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders screens headlessly and writes PNGs to `build/screenshots/`.
 *
 * Not an assertion suite — it exists so the interface can be looked at without a
 * device attached. Layout regressions are visible in seconds this way, and it
 * closes the gap where UI is the one part of the app that compiles, passes every
 * test, and is still wrong.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class ScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val outputDirectory = File("build/screenshots").apply { mkdirs() }

    private fun capture(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(outputDirectory, "$name.png").outputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        println("screenshot: ${File(outputDirectory, "$name.png").absolutePath}")
    }

    private fun document(name: String, pages: Int) = LoadedDocument(
        displayName = name,
        file = File("/tmp/$name.pdf"),
        pageCount = pages,
        pageSizes = List(pages) { SizePt(595.276, 841.89) },
    )

    @Test
    fun `documents screen, empty`() {
        compose.setContent {
            PrintDeckTheme(darkTheme = false) {
                DocumentsContent(
                    state = DocumentsViewModel.UiState(),
                    onOpenFile = {},
                    onOpenImages = {},
                    onSelect = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        capture("documents-empty-light")
    }

    @Test
    fun `documents screen, with recents`() {
        compose.setContent {
            PrintDeckTheme(darkTheme = false) {
                DocumentsContent(
                    state = DocumentsViewModel.UiState(
                        recents = listOf(
                            document("Lecture notes week 4", 24),
                            document("Tenancy agreement", 8),
                            document("Boarding pass", 1),
                        ),
                    ),
                    onOpenFile = {},
                    onOpenImages = {},
                    onSelect = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        capture("documents-recents-light")
    }

    @Test
    fun `documents screen, dark`() {
        compose.setContent {
            PrintDeckTheme(darkTheme = true) {
                DocumentsContent(
                    state = DocumentsViewModel.UiState(
                        recents = listOf(
                            document("Lecture notes week 4", 24),
                            document("Tenancy agreement", 8),
                        ),
                    ),
                    onOpenFile = {},
                    onOpenImages = {},
                    onSelect = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        capture("documents-recents-dark")
    }
}
