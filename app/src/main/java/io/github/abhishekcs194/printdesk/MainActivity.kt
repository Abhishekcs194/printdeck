package io.github.abhishekcs194.printdesk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import io.github.abhishekcs194.printdesk.core.design.theme.PrintDeskTheme
import io.github.abhishekcs194.printdesk.ui.PrintDeskApp

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Android 16 (API 36) enforces edge-to-edge; opting in explicitly keeps
        // behaviour identical across the minSdk 26..36 range rather than
        // depending on the platform default.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            PrintDeskTheme {
                PrintDeskApp()
            }
        }
    }
}
